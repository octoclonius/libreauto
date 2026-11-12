package io.github.octoclonius.libreauto.privileged.notifications

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.util.Log

internal class NotificationAccess(context: Context) {

    private companion object {
        const val TAG = "NotificationAccess"
    }

    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val setGranted by lazy {
        NotificationManager::class
            .java
            .getMethod(
                "setNotificationListenerAccessGranted",
                ComponentName::class.java,
                Boolean::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType,
            )
    }

    fun allow(listener: ComponentName) {
        setGranted.invoke(notifications, listener, true, false)
        Log.i(
            TAG,
            "Allowed ${listener.flattenToShortString()} the phone's notifications unless the user chose otherwise",
        )
    }
}
