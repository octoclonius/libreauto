package io.github.octoclonius.libreauto.car.rail

import android.content.Context
import io.github.octoclonius.libreauto.session.decodeSaved
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

class RailOrderRepository(context: Context) {

    private companion object {
        const val PREFS = "car_rail"
        const val KEY_ORDER = "order"
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _taskIds = MutableStateFlow(decodeRailOrder(prefs.getString(KEY_ORDER, null)))
    val taskIds: StateFlow<List<Int>> = _taskIds.asStateFlow()

    internal fun save(taskIds: List<Int>) {
        if (taskIds.isEmpty() || taskIds == _taskIds.value) return
        _taskIds.value = taskIds
        prefs.edit().putString(KEY_ORDER, encodeRailOrder(taskIds)).apply()
    }
}

internal fun encodeRailOrder(taskIds: List<Int>): String = Json.encodeToString(taskIds)

internal fun decodeRailOrder(encoded: String?): List<Int> =
    decodeSaved<List<Int>>(encoded).orEmpty()
