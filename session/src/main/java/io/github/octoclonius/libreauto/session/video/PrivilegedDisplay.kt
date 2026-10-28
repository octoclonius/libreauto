package io.github.octoclonius.libreauto.session.video

import android.util.Log
import android.view.Surface
import io.github.octoclonius.libreauto.privileged.IDisplayTaskListener
import io.github.octoclonius.libreauto.privileged.IPrivilegedService
import io.github.octoclonius.libreauto.privileged.NO_TASK
import io.github.octoclonius.libreauto.session.privileged.PrivilegedServiceConnection
import io.github.octoclonius.libreauto.session.privileged.call
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
    private val privileged: PrivilegedServiceConnection,
    private val openTasks: OpenCarTasks,
    private val carDisplay: CarDisplay,
    private val carTasks: CarTasks,
) : Closeable {

    companion object {
        private const val TAG = "PrivilegedDisplay"
    }

    private val scope = CoroutineScope(Dispatchers.IO)
    private val lock = Any()
    private var current: OwnedDisplay<IPrivilegedService>? = null
    private var tasks: IDisplayTaskListener? = null

    fun start() {
        scope.launch { privileged.service.collect(::follow) }
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
            display.service.call("resize the car display") {
                it.resizeDisplay(display.id, apps.width, apps.height, layout.appsDensity)
            } ?: return
            Log.i(
                TAG,
                "Resized the car display ${display.id} to ${apps.width}x${apps.height} at " +
                    "${layout.appsDensity} dpi",
            )
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
        val home = carDisplay.home.component
        val remembered = openTasks.load()
        val listener = carTasks.listener(home.packageName, openTasks::save)
        val id =
            service.call("create the car display") {
                it.createDisplay(
                    surface,
                    layout.apps.width,
                    layout.apps.height,
                    layout.appsDensity,
                    home,
                    listener,
                )
            }
        if (id == null) {
            carTasks.onReleased(listener)
            return null
        }
        tasks = listener
        Log.i(TAG, "The privileged service created the car display $id")
        carDisplay.onCreated(id, layout.format.density)
        restore(service, id, remembered)
        return id
    }

    private fun restore(service: IPrivilegedService, id: Int, remembered: OpenTasks) {
        if (remembered.taskIds.isEmpty()) return
        service.call("bring back the car's tasks") {
            it.restoreTasks(id, remembered.taskIds.toIntArray(), remembered.frontTaskId ?: NO_TASK)
        } ?: return
        Log.i(TAG, "Asked to bring back the tasks ${remembered.taskIds} to the car display $id")
    }

    private fun release(display: OwnedDisplay<IPrivilegedService>) {
        carDisplay.onReleased(display.id)
        tasks?.let(carTasks::onReleased)
        tasks = null
        display.service.call("release the car display ${display.id}") {
            it.releaseDisplay(display.id)
        } ?: return
        Log.i(TAG, "Released the car display ${display.id}")
    }
}
