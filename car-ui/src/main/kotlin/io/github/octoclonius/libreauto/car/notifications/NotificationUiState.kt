package io.github.octoclonius.libreauto.car.notifications

import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.UserHandle
import android.util.Log
import io.github.octoclonius.libreauto.car.apps.loadCarIcon
import io.github.octoclonius.libreauto.projection.notifications.PhoneNotification
import java.util.concurrent.ConcurrentHashMap

internal class NotificationApp(val label: String, val icon: Drawable?)

internal data class NotificationUiState(
    val notification: PhoneNotification,
    val app: NotificationApp,
)

internal class NotificationApps(context: Context) {

    private companion object {
        const val TAG = "NotificationApps"
    }

    private data class AppKey(val packageName: String, val user: UserHandle, val carDensity: Int)

    private val packageManager = context.packageManager
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val apps = ConcurrentHashMap<AppKey, NotificationApp>()

    fun uiState(notification: PhoneNotification, carDensity: Int): NotificationUiState =
        NotificationUiState(
            notification,
            apps.getOrPut(AppKey(notification.packageName, notification.user, carDensity)) {
                load(notification.packageName, notification.user, carDensity)
            },
        )

    private fun load(packageName: String, user: UserHandle, carDensity: Int): NotificationApp =
        try {
            val info = launcherApps.getApplicationInfo(packageName, 0, user)
            val icon =
                loadCarIcon(packageManager, info, info.icon, carDensity, NOTIFICATION_ICON_SIZE) {
                    info.loadIcon(packageManager)
                }
            NotificationApp(
                info.loadLabel(packageManager).toString(),
                packageManager.getUserBadgedIcon(icon, user),
            )
        } catch (e: PackageManager.NameNotFoundException) {
            Log.w(TAG, "No app info for $packageName of $user", e)
            NotificationApp(packageName, icon = null)
        }
}
