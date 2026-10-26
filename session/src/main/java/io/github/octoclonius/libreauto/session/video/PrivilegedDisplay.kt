package io.github.octoclonius.libreauto.session.video

import android.os.RemoteException
import android.util.Log
import android.view.Surface
import io.github.octoclonius.libreauto.privileged.IDisplayTaskListener
import io.github.octoclonius.libreauto.privileged.IPrivilegedService
import io.github.octoclonius.libreauto.privileged.NO_TASK
import io.github.octoclonius.libreauto.session.privileged.PrivilegedServiceConnection
import io.github.octoclonius.libreauto.session.tasks.CarTasks
import io.github.octoclonius.libreauto.session.tasks.OpenCarTasks
import io.github.octoclonius.libreauto.session.tasks.OpenTasks
import java.io.Closeable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

internal class PrivilegedDisplay(
    private val surface: Surface,
    private var layout: CarLayout,
    private val openTasks: OpenCarTasks,
) : Closeable {

    companion object {
        private const val TAG = "PrivilegedDisplay"
    }

    private val scope = CoroutineScope(Dispatchers.IO)
    private val lock = Any()
    private var current: OwnedDisplay<IPrivilegedService>? = null
    private var tasks: IDisplayTaskListener? = null

    fun start() {
        scope.launch { PrivilegedServiceConnection.service.collect(::follow) }
    }

    override fun close() {
        runBlocking { scope.coroutineContext.job.cancelAndJoin() }
        synchronized(lock) {
            current?.let(::release)
            current = null
        }
    }

    fun resize(layout: CarLayout) {
        synchronized(lock) {
            this.layout = layout
            val display = current ?: return
            val apps = layout.apps
            try {
                display.service.resizeDisplay(
                    display.id,
                    apps.width,
                    apps.height,
                    layout.appsDensity,
                )
                Log.i(
                    TAG,
                    "Resized the car display ${display.id} to ${apps.width}x${apps.height} at " +
                        "${layout.appsDensity} dpi",
                )
            } catch (e: RemoteException) {
                Log.w(TAG, "The privileged service didn't resize the car display", e)
            } catch (e: RuntimeException) {
                Log.w(TAG, "The privileged service refused to resize the car display", e)
            }
        }
    }

    private fun follow(service: IPrivilegedService?) {
        if (service == null) Log.i(TAG, "No car display while the privileged service isn't running")
        synchronized(lock) {
            val change = displayChange(current, service)
            change.release?.let {
                release(it)
                current = null
            }
            change.createOn?.let { owner ->
                current = create(owner)?.let { OwnedDisplay(owner, it) }
            }
        }
    }

    private fun create(service: IPrivilegedService): Int? {
        val home = PrivilegedServiceConnection.carHome
        val remembered = openTasks.load()
        val listener = CarTasks.listener(home.packageName, openTasks::save)
        val id =
            try {
                service.createDisplay(
                    surface,
                    layout.apps.width,
                    layout.apps.height,
                    layout.appsDensity,
                    home,
                    listener,
                )
            } catch (e: RemoteException) {
                Log.w(TAG, "The privileged service didn't create the car display", e)
                CarTasks.onReleased(listener)
                return null
            } catch (e: RuntimeException) {
                Log.w(TAG, "The privileged service refused to create the car display", e)
                CarTasks.onReleased(listener)
                return null
            }
        tasks = listener
        Log.i(TAG, "The privileged service created the car display $id")
        CarDisplay.onCreated(id, layout.format.density)
        restore(service, id, remembered)
        return id
    }

    private fun restore(service: IPrivilegedService, id: Int, remembered: OpenTasks) {
        if (remembered.taskIds.isEmpty()) return
        try {
            service.restoreTasks(
                id,
                remembered.taskIds.toIntArray(),
                remembered.frontTaskId ?: NO_TASK,
            )
            Log.i(TAG, "Asked to bring back the tasks ${remembered.taskIds} to the car display $id")
        } catch (e: RemoteException) {
            Log.w(TAG, "The privileged service didn't bring back the car's tasks", e)
        }
    }

    private fun release(display: OwnedDisplay<IPrivilegedService>) {
        CarDisplay.onReleased(display.id)
        tasks?.let(CarTasks::onReleased)
        tasks = null
        try {
            display.service.releaseDisplay(display.id)
            Log.i(TAG, "Released the car display ${display.id}")
        } catch (e: RemoteException) {
            Log.i(TAG, "The privileged service is gone with the car display ${display.id}", e)
        } catch (e: RuntimeException) {
            Log.w(TAG, "Releasing the car display ${display.id} failed", e)
        }
    }
}
