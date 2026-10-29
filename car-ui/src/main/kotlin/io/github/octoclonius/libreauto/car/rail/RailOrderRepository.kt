package io.github.octoclonius.libreauto.car.rail

import android.content.Context
import io.github.octoclonius.libreauto.projection.datastore.jsonDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

class RailOrderRepository(context: Context, private val externalScope: CoroutineScope) {

    private companion object {
        const val NAME = "car_rail"
    }

    private val dataStore =
        jsonDataStore(context, NAME, ListSerializer(Int.serializer()), emptyList())

    val taskIds: StateFlow<List<Int>> =
        dataStore.data.stateIn(externalScope, SharingStarted.Eagerly, emptyList())

    internal fun save(taskIds: List<Int>) {
        if (taskIds.isEmpty()) return
        externalScope.launch { dataStore.updateData { taskIds } }
    }
}
