package io.github.octoclonius.libreauto.privileged

internal fun taskRequestError(taskId: Int, carTaskIds: Set<Int>): String? =
    if (taskId !in carTaskIds) "The task $taskId isn't on the car display" else null

internal fun restoreRequestError(taskIds: List<Int>, frontTaskId: Int): String? =
    when {
        taskIds.isEmpty() -> "There are no tasks to bring back"
        taskIds.toSet().size != taskIds.size -> "The tasks $taskIds repeat"
        frontTaskId != NO_TASK && frontTaskId !in taskIds ->
            "The front task $frontTaskId isn't one of the tasks $taskIds"
        else -> null
    }
