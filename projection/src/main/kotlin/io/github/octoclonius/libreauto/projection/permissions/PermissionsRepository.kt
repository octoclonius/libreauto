package io.github.octoclonius.libreauto.projection.permissions

import android.Manifest
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import io.github.octoclonius.libreauto.projection.CarSessionHolder
import io.github.octoclonius.libreauto.projection.notifications.NotificationListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

data class PermissionsState(
    val isNotificationAccessAllowed: Boolean,
    val isNearbyDevicesAllowed: Boolean,
    val isContactsAllowed: Boolean,
)

class PermissionsRepository(
    context: Context,
    private val carSessionHolder: CarSessionHolder,
    private val externalScope: CoroutineScope,
) {

    private companion object {
        const val TAG = "PermissionsRepository"
    }

    private val context = context.applicationContext
    private val notificationManager = context.getSystemService(NotificationManager::class.java)
    val notificationListener = ComponentName(context, NotificationListener::class.java)

    private val _state = MutableStateFlow(read())
    val state: StateFlow<PermissionsState> = _state.asStateFlow()

    fun start() {
        externalScope.launch {
            carSessionHolder.session
                .map { it != null }
                .distinctUntilChanged()
                .filter { it }
                .collect { refresh() }
        }
    }

    fun refresh() {
        val next = read()
        if (next == _state.value) return
        Log.i(TAG, "LibreAuto's permissions: $next")
        _state.value = next
    }

    private fun read(): PermissionsState =
        PermissionsState(
            isNotificationAccessAllowed =
                notificationManager.isNotificationListenerAccessGranted(notificationListener),
            isNearbyDevicesAllowed = isGranted(Manifest.permission.BLUETOOTH_CONNECT),
            isContactsAllowed = isGranted(Manifest.permission.READ_CONTACTS),
        )

    private fun isGranted(permission: String): Boolean =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
}
