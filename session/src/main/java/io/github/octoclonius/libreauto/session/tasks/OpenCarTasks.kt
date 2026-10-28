package io.github.octoclonius.libreauto.session.tasks

import android.content.Context
import io.github.octoclonius.libreauto.session.decodeSaved
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable internal data class OpenTasks(val taskIds: List<Int>, val frontTaskId: Int?)

internal class OpenCarTasks(context: Context) {

    companion object {
        private const val PREFS = "car_tasks"
        private const val KEY_TASKS = "tasks"
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(): OpenTasks = decodeOpenTasks(prefs.getString(KEY_TASKS, null))

    fun save(state: CarTasksState) {
        val tasks = OpenTasks(state.tasks.map { it.taskId }, state.frontTaskId)
        prefs.edit().clear().putString(KEY_TASKS, encodeOpenTasks(tasks)).apply()
    }
}

internal fun encodeOpenTasks(tasks: OpenTasks): String = Json.encodeToString(tasks)

internal fun decodeOpenTasks(encoded: String?): OpenTasks {
    val decoded = decodeSaved<OpenTasks>(encoded) ?: return OpenTasks(emptyList(), null)
    val taskIds = decoded.taskIds.distinct()
    return OpenTasks(taskIds, decoded.frontTaskId?.takeIf { it in taskIds })
}
