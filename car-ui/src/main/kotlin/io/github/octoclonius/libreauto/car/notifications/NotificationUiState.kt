package io.github.octoclonius.libreauto.car.notifications

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.os.UserHandle
import io.github.octoclonius.libreauto.car.apps.loadCarAppIcon
import io.github.octoclonius.libreauto.projection.notifications.PhoneNotification

internal sealed interface NotificationIcon {
    class App(val drawable: Drawable) : NotificationIcon

    class Small(val drawable: Drawable) : NotificationIcon
}

internal data class NotificationUiState(
    val notification: PhoneNotification,
    val appName: String,
    val icon: NotificationIcon?,
)

internal class NotificationApps(private val context: Context) {

    private data class AppKey(
        val packageName: String,
        val user: UserHandle,
        val sourceDir: String?,
        val carDensity: Int,
    )

    private class App(val label: String, val icon: Drawable?, val isHeadlessSystemApp: Boolean)

    private data class StateKey(val notification: PhoneNotification, val carDensity: Int)

    private val packageManager = context.packageManager
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val apps = HashMap<AppKey, App>()
    private val states = HashMap<StateKey, NotificationUiState>()

    fun uiStates(
        notifications: List<PhoneNotification>,
        carDensity: Int,
    ): List<NotificationUiState> {
        val stateKeys = notifications.map { StateKey(it, carDensity) }
        states.keys.retainAll(stateKeys.toSet())
        apps.keys.retainAll(notifications.mapTo(HashSet()) { it.appKey(carDensity) })
        return stateKeys.map { states.getOrPut(it) { uiState(it.notification, carDensity) } }
    }

    private fun uiState(notification: PhoneNotification, carDensity: Int): NotificationUiState {
        val app = apps.getOrPut(notification.appKey(carDensity)) { load(notification, carDensity) }
        val smallIcon =
            if (notification.prefersSmallIcon || app.isHeadlessSystemApp) {
                loadSmallIcon(notification, carDensity)
            } else {
                null
            }
        return NotificationUiState(
            notification,
            notification.substituteAppName ?: app.label,
            smallIcon?.let(NotificationIcon::Small) ?: app.icon?.let(NotificationIcon::App),
        )
    }

    private fun load(notification: PhoneNotification, carDensity: Int): App {
        val info =
            notification.app
                ?: return App(notification.packageName, icon = null, isHeadlessSystemApp = false)
        return App(
            info.loadLabel(packageManager).toString(),
            loadCarAppIcon(
                packageManager,
                info,
                notification.user,
                carDensity,
                NOTIFICATION_ICON_SIZE,
            ),
            isHeadlessSystemApp =
                (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
                    launcherApps.getActivityList(info.packageName, notification.user).isEmpty(),
        )
    }

    private fun PhoneNotification.appKey(carDensity: Int) =
        AppKey(packageName, user, app?.sourceDir, carDensity)

    private fun loadSmallIcon(notification: PhoneNotification, carDensity: Int): Drawable? {
        val icon = notification.smallIcon ?: return null
        val app = notification.app
        return try {
                if (icon.type == Icon.TYPE_RESOURCE && icon.resPackage == app?.packageName) {
                    packageManager
                        .getResourcesForApplication(app)
                        .getDrawableForDensity(icon.resId, carDensity, null)
                } else {
                    icon.loadDrawable(context)
                }
            } catch (e: PackageManager.NameNotFoundException) {
                null
            } catch (e: Resources.NotFoundException) {
                null
            }
            ?.mutate()
    }
}
