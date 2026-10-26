package io.github.octoclonius.libreauto.privileged

internal fun taskRequestError(
    displayId: Int,
    taskId: Int,
    carDisplayId: Int?,
    carTaskIds: Set<Int>,
): String? =
    when {
        carDisplayId == null -> "There is no car display"
        displayId != carDisplayId -> "The display $displayId isn't the car display $carDisplayId"
        taskId !in carTaskIds -> "The task $taskId isn't on the car display $carDisplayId"
        else -> null
    }

internal fun restoreRequestError(
    displayId: Int,
    carDisplayId: Int?,
    taskIds: List<Int>,
    frontTaskId: Int,
): String? =
    when {
        carDisplayId == null -> "There is no car display"
        displayId != carDisplayId -> "The display $displayId isn't the car display $carDisplayId"
        taskIds.isEmpty() -> "There are no tasks to bring back"
        taskIds.toSet().size != taskIds.size -> "The tasks $taskIds repeat"
        frontTaskId != NO_TASK && frontTaskId !in taskIds ->
            "The front task $frontTaskId isn't one of the tasks $taskIds"
        else -> null
    }
