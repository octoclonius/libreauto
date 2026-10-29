package io.github.octoclonius.libreauto.privileged

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.os.IBinder
import android.os.Parcel
import android.os.Process
import android.util.Log
import android.view.Surface
import java.io.Closeable

internal class CarDisplaySession
private constructor(
    private val context: Context,
    private val display: VirtualCarDisplay,
    private val tasks: DisplayTasks,
    private val keys: DisplayKeys,
    private val appUid: Int,
    private val app: IBinder,
) : ICarDisplaySession.Stub(), Closeable {

    companion object {
        private const val TAG = "CarDisplaySession"

        fun create(
            context: Context,
            association: CompanionAssociation,
            keys: DisplayKeys,
            appUid: Int,
            surface: Surface,
            width: Int,
            height: Int,
            densityDpi: Int,
            home: ComponentName,
            listener: IDisplayTaskListener,
        ): CarDisplaySession {
            val display =
                reportingCall(TAG, "Creating the car display") {
                    VirtualCarDisplay.create(
                        context,
                        association,
                        surface,
                        width,
                        height,
                        densityDpi,
                        home,
                    )
                }
            val tasks =
                reportingCall(
                    TAG,
                    "Watching the tasks on the car display",
                    onFailure = display::close,
                ) {
                    DisplayTasks(context, display.id, home.packageName, listener).apply { start() }
                }
            val session =
                CarDisplaySession(context, display, tasks, keys, appUid, listener.asBinder())
            reportingCall(TAG, "Watching the app", onFailure = session::close) {
                session.watchApp()
            }
            return session
        }
    }

    private val lock = Any()
    private var isClosed = false
    private var isWatchingApp = false
    private val appDeath =
        IBinder.DeathRecipient {
            Log.w(TAG, "The app died; releasing the car display $id")
            close()
        }

    val id: Int = display.id

    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        checkCaller(TAG, code) { isAppCallAllowed(code, it, appUid, Process.myUid()) }
        return super.onTransact(code, data, reply, flags)
    }

    override fun getDisplayId(): Int = id

    override fun resize(width: Int, height: Int, densityDpi: Int) {
        synchronized(lock) {
            if (isClosed) throw IllegalStateException("The car display $id is released")
            val error = resizeRequestError(width, height, densityDpi)
            if (error != null) throw IllegalArgumentException(error)
            reportingCall(TAG, "Resizing the car display $id") {
                display.resize(width, height, densityDpi)
            }
        }
    }

    override fun sendTouch(
        eventTimeNanos: Long,
        action: Int,
        actionIndex: Int,
        pointerIds: IntArray,
        xs: IntArray,
        ys: IntArray,
    ) {
        call(
            "Sending a touch to the car display $id",
            { touchRequestError(eventTimeNanos, action, actionIndex, pointerIds, xs, ys) },
        ) {
            val pointers = pointerIds.indices.map { TouchPointer(pointerIds[it], xs[it], ys[it]) }
            display.onTouch(eventTimeNanos, action, actionIndex, pointers)
        }
    }

    override fun sendKey(keyCode: Int) {
        call("Sending the key $keyCode to the car display $id", { keyRequestError(keyCode) }) {
            keys.press(id, keyCode)
        }
    }

    override fun showActivity(activity: PendingIntent) {
        call(
            "Starting an activity on the car display $id",
            { activityRequestError(activity.isActivity, activity.creatorUid, appUid) },
        ) {
            display.showActivity(context, activity)
        }
    }

    override fun showTask(taskId: Int) {
        call(
            "Showing the task $taskId on the car display $id",
            { taskRequestError(taskId, tasks.taskIds) },
        ) {
            tasks.show(taskId)
        }
    }

    override fun closeTask(taskId: Int) {
        call(
            "Closing the task $taskId on the car display $id",
            { taskRequestError(taskId, tasks.taskIds) },
        ) {
            tasks.close(taskId)
        }
    }

    override fun restoreTasks(taskIds: IntArray, frontTaskId: Int) {
        call(
            "Bringing back tasks to the car display $id",
            { restoreRequestError(taskIds.toList(), frontTaskId) },
        ) {
            tasks.restore(taskIds.toList(), frontTaskId)
        }
    }

    override fun release() {
        close()
    }

    override fun close() {
        synchronized(lock) {
            if (isClosed) return
            isClosed = true
            identityCall(TAG, "Releasing the car display $id") {
                closeAll(TAG) {
                    step("Stopping watching the app") {
                        if (isWatchingApp) app.unlinkToDeath(appDeath, 0)
                    }
                    step("Moving the car display's tasks to the phone") { tasks.moveToPhone() }
                    step("Stopping watching the tasks on the car display") { tasks.close() }
                    step("Releasing the car display $id") { display.close() }
                }
            }
        }
    }

    private fun watchApp() {
        synchronized(lock) {
            app.linkToDeath(appDeath, 0)
            isWatchingApp = true
        }
    }

    private fun call(action: String, refusal: () -> String?, block: () -> Unit) {
        synchronized(lock) {
            if (isClosed) {
                Log.w(TAG, "$action refused: the car display is released")
                return
            }
            checkedCall(TAG, action, refusal(), block)
        }
    }
}
