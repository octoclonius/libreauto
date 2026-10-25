package io.github.octoclonius.libreauto.car.rail

import io.github.octoclonius.libreauto.session.tasks.CarTasksState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update

object RailState {
    private val _closingTaskId = MutableStateFlow<Int?>(null)
    val closingTaskId: StateFlow<Int?> = _closingTaskId.asStateFlow()

    private val _closedTaskIds = MutableStateFlow<Set<Int>>(emptySet())
    val closedTaskIds: StateFlow<Set<Int>> = _closedTaskIds.asStateFlow()

    internal fun startClosing(taskId: Int) {
        _closingTaskId.value = taskId
    }

    internal fun cancelClosing(): Boolean = _closingTaskId.getAndUpdate { null } != null

    internal fun onClosed(taskId: Int) {
        _closedTaskIds.update { it + taskId }
        _closingTaskId.value = null
    }

    internal fun onTasks(state: CarTasksState) {
        val open = state.tasks.mapTo(HashSet()) { it.taskId }
        _closingTaskId.update { it?.takeIf { id -> id in open } }
        _closedTaskIds.update { closed -> closed.filterTo(HashSet()) { it in open } }
    }
}
