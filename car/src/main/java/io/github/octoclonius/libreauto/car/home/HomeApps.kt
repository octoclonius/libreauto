package io.github.octoclonius.libreauto.car.home

import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import io.github.octoclonius.libreauto.session.decodeSaved
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

object HomeApps {
    private const val TAG = "HomeApps"
    private const val PREFS = "car_home"
    private const val KEY_APPS = "apps"

    private var prefs: SharedPreferences? = null

    private val _components = MutableStateFlow<List<ComponentName>>(emptyList())
    val components: StateFlow<List<ComponentName>> = _components.asStateFlow()

    fun load(context: Context) {
        if (prefs != null) return
        val loaded = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = loaded
        _components.value =
            decodeHomeApps(loaded.getString(KEY_APPS, null)).map {
                ComponentName(it.packageName, it.className)
            }
    }

    internal fun add(component: ComponentName, at: Int? = null) {
        save(withAdded(_components.value, component, at ?: _components.value.size))
    }

    internal fun move(from: Int, to: Int) {
        save(withMoved(_components.value, from, to))
    }

    internal fun remove(at: Int) {
        save(withRemoved(_components.value, at))
    }

    internal fun removeAll(gone: Set<ComponentName>) {
        if (_components.value.none { it in gone }) return
        Log.i(TAG, "Removed the uninstalled or disabled $gone from the home")
        save(_components.value.filter { it !in gone })
    }

    private fun save(components: List<ComponentName>) {
        _components.value = components
        val saved = components.map { SavedComponent(it.packageName, it.className) }
        prefs?.edit()?.putString(KEY_APPS, encodeHomeApps(saved))?.apply()
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

internal fun encodeHomeApps(apps: List<SavedComponent>): String = Json.encodeToString(apps)

internal fun decodeHomeApps(encoded: String?): List<SavedComponent> =
    decodeSaved<List<SavedComponent>>(encoded).orEmpty()
