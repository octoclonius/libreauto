package io.github.octoclonius.libreauto.session.display

import android.util.Log
import android.view.Surface
import io.github.octoclonius.libreauto.privileged.api.ICarDisplaySession
import io.github.octoclonius.libreauto.privileged.api.IDisplayTaskListener
import io.github.octoclonius.libreauto.privileged.api.IPrivilegedService
import io.github.octoclonius.libreauto.privileged.api.NO_TASK
import io.github.octoclonius.libreauto.session.privileged.PrivilegedServiceConnection
import io.github.octoclonius.libreauto.session.privileged.call
import io.github.octoclonius.libreauto.session.tasks.CarTasks
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
    private val carDisplay: CarDisplay,
    private val carTasks: CarTasks,
) : Closeable {

    companion object {
        private const val TAG = "PrivilegedDisplay"
    }

    private val scope = CoroutineScope(Dispatchers.IO)
    private val lock = Any()
    private var current: OwnedDisplay<IPrivilegedService>? = null
    private var session: ICarDisplaySession? = null
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
            session?.call("resize the car display") {
                it.resize(apps.width, apps.height, layout.appsDensity)
            } ?: return
            Log.i(
                TAG,
                "Resized the car display ${display.id} to ${apps.width}x${apps.height} at " +
                    "${layout.appsDensity} dpi",
            )
        }
    }

    private suspend fun follow(service: IPrivilegedService?) {
        if (service == null) Log.i(TAG, "No car display while the privileged service isn't running")
        val remembered = carTasks.savedOpenTasks()
        synchronized(lock) {
            val change = displayChange(current, service)
            change.release?.let {
                release(it)
                current = null
            }
            change.createOn?.let { owner ->
                current = create(owner, remembered)?.let { OwnedDisplay(owner, it) }
            }
        }
    }

    private fun create(service: IPrivilegedService, remembered: OpenTasks): Int? {
        val home = carDisplay.home.component
        val listener = carTasks.listener(home.packageName)
        val created =
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
        val id = created?.call("read the car display's id") { it.displayId }
        if (created == null || id == null) {
            created?.call("release the car display") { it.release() }
            carTasks.onReleased(listener)
            return null
        }
        session = created
        tasks = listener
        Log.i(TAG, "The privileged service created the car display $id")
        carDisplay.onCreated(created, id, layout.format.density)
        restore(created, id, remembered)
        return id
    }

    private fun restore(display: ICarDisplaySession, id: Int, remembered: OpenTasks) {
        if (remembered.taskIds.isEmpty()) return
        display.call("bring back the car's tasks") {
            it.restoreTasks(remembered.taskIds.toIntArray(), remembered.frontTaskId ?: NO_TASK)
        } ?: return
        Log.i(TAG, "Asked to bring back the tasks ${remembered.taskIds} to the car display $id")
    }

    private fun release(display: OwnedDisplay<IPrivilegedService>) {
        carDisplay.onReleased(display.id)
        tasks?.let(carTasks::onReleased)
        tasks = null
        val released = session
        session = null
        released?.call("release the car display ${display.id}") { it.release() } ?: return
        Log.i(TAG, "Released the car display ${display.id}")
    }
}
