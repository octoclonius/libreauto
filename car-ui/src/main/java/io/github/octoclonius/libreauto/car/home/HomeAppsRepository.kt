package io.github.octoclonius.libreauto.car.home

import android.content.ComponentName
import android.content.Context
import android.util.Log
import io.github.octoclonius.libreauto.session.jsonDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

class HomeAppsRepository(context: Context, private val externalScope: CoroutineScope) {

    private companion object {
        const val TAG = "HomeAppsRepository"
        const val NAME = "car_home"
    }

    private val dataStore =
        jsonDataStore(context, NAME, ListSerializer(SavedComponent.serializer()), emptyList())

    private val _components = MutableStateFlow<List<ComponentName>>(emptyList())
    val components: StateFlow<List<ComponentName>> = _components.asStateFlow()

    private val loading = externalScope.launch {
        _components.value =
            dataStore.data.first().map { ComponentName(it.packageName, it.className) }
    }

    internal fun add(component: ComponentName, at: Int? = null) {
        update { withAdded(it, component, at ?: it.size) }
    }

    internal fun move(from: Int, to: Int) {
        update { withMoved(it, from, to) }
    }

    internal fun remove(at: Int) {
        update { withRemoved(it, at) }
    }

    internal fun removeAll(gone: Set<ComponentName>) {
        update { components ->
            if (components.none { it in gone }) return@update components
            Log.i(TAG, "Removed the uninstalled or disabled $gone from the home")
            components.filter { it !in gone }
        }
    }

    private fun update(transform: (List<ComponentName>) -> List<ComponentName>) {
        externalScope.launch {
            loading.join()
            val components = transform(_components.value)
            if (components == _components.value) return@launch
            _components.value = components
            dataStore.updateData {
                components.map { SavedComponent(it.packageName, it.className) }
            }
        }
    }
}

internal fun <T> withAdded(list: List<T>, item: T, at: Int): List<T> =
    list.toMutableList().apply { add(at.coerceIn(0, size), item) }

internal fun <T> withMoved(list: List<T>, from: Int, to: Int): List<T> {
    if (from !in list.indices) return list
    return list.toMutableList().apply {
        val item = removeAt(from)
        add(to.coerceIn(0, size), item)
    }
}

internal fun <T> withRemoved(list: List<T>, at: Int): List<T> =
    if (at in list.indices) list.toMutableList().apply { removeAt(at) } else list

@Serializable internal data class SavedComponent(val packageName: String, val className: String)
