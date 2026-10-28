package io.github.octoclonius.libreauto.session.tasks

import io.github.octoclonius.libreauto.privileged.DisplayTask
import io.github.octoclonius.libreauto.privileged.IDisplayTaskListener
import io.github.octoclonius.libreauto.session.privileged.PrivilegedServiceConnection
import io.github.octoclonius.libreauto.session.video.CarDisplay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CarTask(val taskId: Int, val packageName: String)

data class CarTasksState(val tasks: List<CarTask>, val frontTaskId: Int?) {
    companion object {
        val NONE = CarTasksState(emptyList(), null)
    }
}

class CarTasks
internal constructor(
    private val display: CarDisplay,
    private val privileged: PrivilegedServiceConnection,
) {
    private val lock = Any()
    private var listener: IDisplayTaskListener? = null

    private val _state = MutableStateFlow(CarTasksState.NONE)
    val state: StateFlow<CarTasksState> = _state.asStateFlow()

    fun show(task: CarTask) {
        val id = display.state.value?.id ?: return
        privileged.call("show the task ${task.taskId}") { it.showTask(id, task.taskId) }
    }

    fun close(task: CarTask) {
        val id = display.state.value?.id ?: return
        privileged.call("close the task ${task.taskId}") { it.closeTask(id, task.taskId) }
    }

    internal fun listener(
        homePackage: String,
        onState: (CarTasksState) -> Unit,
    ): IDisplayTaskListener {
        val created =
            object : IDisplayTaskListener.Stub() {
                override fun onTasks(tasks: Array<DisplayTask>) {
                    synchronized(lock) {
                        if (listener !== this) return
                        val state = carTasks(tasks.toList(), homePackage)
                        _state.value = state
                        onState(state)
                    }
                }
            }
        synchronized(lock) {
            listener = created
            _state.value = CarTasksState.NONE
        }
        return created
    }

    internal fun onReleased(released: IDisplayTaskListener) {
        synchronized(lock) {
            if (listener !== released) return
            listener = null
            _state.value = CarTasksState.NONE
        }
    }
}

internal fun carTasks(tasks: List<DisplayTask>, homePackage: String): CarTasksState {
    val front = tasks.firstOrNull()?.takeIf { it.packageName != homePackage }
    return CarTasksState(
        tasks.filter { it.packageName != homePackage }.map { CarTask(it.taskId, it.packageName) },
        front?.taskId,
    )
}
