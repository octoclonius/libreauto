package io.github.octoclonius.libreauto.privileged.notifications

import android.service.notification.NotificationStats
import com.android.internal.statusbar.NotificationVisibility
import io.github.octoclonius.libreauto.privileged.SystemServices

internal class NotificationActions {

    private val statusBar = SystemServices.statusBarService()

    fun click(key: String, rank: Int, count: Int) {
        statusBar.onNotificationClick(key, NotificationVisibility.obtain(key, rank, count, true))
    }

    fun clear(pkg: String, userId: Int, key: String, isHeadsUp: Boolean, rank: Int, count: Int) {
        statusBar.onNotificationClear(
            pkg,
            userId,
            key,
            if (isHeadsUp) NotificationStats.DISMISSAL_PEEK else NotificationStats.DISMISSAL_SHADE,
            NotificationStats.DISMISS_SENTIMENT_NEUTRAL,
            NotificationVisibility.obtain(key, rank, count, true),
            false,
        )
    }
}
