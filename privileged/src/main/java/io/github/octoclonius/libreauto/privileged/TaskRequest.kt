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
