package io.github.octoclonius.libreauto.privileged

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
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
) : Closeable {

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
            return CarDisplaySession(context, display, tasks, keys, appUid)
        }
    }

    private val lock = Any()
    private var isClosed = false

    val id: Int = display.id

    fun resize(displayId: Int, width: Int, height: Int, densityDpi: Int) {
        synchronized(lock) {
            val error = resizeRequestError(displayId, width, height, densityDpi, currentId())
            if (error != null) throw IllegalArgumentException(error)
            reportingCall(TAG, "Resizing the car display $id") {
                display.resize(width, height, densityDpi)
            }
        }
    }

    fun sendTouch(
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

    fun sendKey(displayId: Int, keyCode: Int) {
        call(
            "Sending the key $keyCode to the car display $displayId",
            { keyRequestError(displayId, keyCode, id) },
        ) {
            keys.press(id, keyCode)
        }
    }

    fun showActivity(displayId: Int, activity: PendingIntent) {
        call(
            "Starting an activity on the car display $displayId",
            {
                activityRequestError(
                    displayId,
                    id,
                    activity.isActivity,
                    activity.creatorUid,
                    appUid,
                )
            },
        ) {
            display.showActivity(context, activity)
        }
    }

    fun showTask(displayId: Int, taskId: Int) {
        call(
            "Showing the task $taskId on the car display $displayId",
            { taskRequestError(displayId, taskId, id, tasks.taskIds) },
        ) {
            tasks.show(taskId)
        }
    }

    fun closeTask(displayId: Int, taskId: Int) {
        call(
            "Closing the task $taskId on the car display $displayId",
            { taskRequestError(displayId, taskId, id, tasks.taskIds) },
        ) {
            tasks.close(taskId)
        }
    }

    fun restoreTasks(displayId: Int, taskIds: IntArray, frontTaskId: Int) {
        call(
            "Bringing back tasks to the car display $displayId",
            { restoreRequestError(displayId, id, taskIds.toList(), frontTaskId) },
        ) {
            tasks.restore(taskIds.toList(), frontTaskId)
        }
    }

    override fun close() {
        synchronized(lock) {
            if (isClosed) return
            isClosed = true
            closeAll(TAG) {
                step("Moving the car display's tasks to the phone") { tasks.moveToPhone() }
                step("Stopping watching the tasks on the car display") { tasks.close() }
                step("Releasing the car display $id") { display.close() }
            }
        }
    }

    private fun currentId(): Int? = id.takeUnless { isClosed }

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
