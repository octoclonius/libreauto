package io.github.octoclonius.libreauto.session.tasks

import android.content.Context
import io.github.octoclonius.libreauto.privileged.NO_TASK

internal data class OpenTasks(val taskIds: List<Int>, val frontTaskId: Int?)

internal class OpenCarTasks(context: Context) {

    companion object {
        private const val PREFS = "car_tasks"
        private const val KEY_TASKS = "tasks"
        private const val KEY_FRONT = "front"
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(): OpenTasks {
        val taskIds = decodeTaskIds(prefs.getString(KEY_TASKS, null))
        val front = prefs.getInt(KEY_FRONT, NO_TASK).takeIf { it in taskIds }
        return OpenTasks(taskIds, front)
    }

    fun save(state: CarTasksState) {
        prefs
            .edit()
            .putString(KEY_TASKS, encodeTaskIds(state.tasks.map { it.taskId }))
            .putInt(KEY_FRONT, state.frontTaskId ?: NO_TASK)
            .apply()
    }
}

internal fun encodeTaskIds(taskIds: List<Int>): String = taskIds.joinToString(",")

internal fun decodeTaskIds(encoded: String?): List<Int> =
    encoded.orEmpty().split(",").mapNotNull { it.toIntOrNull() }.distinct()
