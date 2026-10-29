package io.github.octoclonius.libreauto.projection.tasks

import android.content.Context
import io.github.octoclonius.libreauto.projection.datastore.jsonDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable internal data class OpenTasks(val taskIds: List<Int>, val frontTaskId: Int?)

internal class OpenCarTasksRepository(
    context: Context,
    private val externalScope: CoroutineScope,
) {

    private companion object {
        const val NAME = "car_tasks"
    }

    private val dataStore =
        jsonDataStore(context, NAME, OpenTasks.serializer(), OpenTasks(emptyList(), null))

    val openTasks: Flow<OpenTasks> = dataStore.data.map { it.sanitized() }

    fun save(state: CarTasksState) {
        val tasks = OpenTasks(state.tasks.map { it.taskId }, state.frontTaskId)
        externalScope.launch { dataStore.updateData { tasks } }
    }
}

internal fun OpenTasks.sanitized(): OpenTasks {
    val distinct = taskIds.distinct()
    return OpenTasks(distinct, frontTaskId?.takeIf { it in distinct })
}
