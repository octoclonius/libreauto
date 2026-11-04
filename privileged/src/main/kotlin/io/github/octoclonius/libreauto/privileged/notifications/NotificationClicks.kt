package io.github.octoclonius.libreauto.privileged.notifications

import com.android.internal.statusbar.NotificationVisibility
import io.github.octoclonius.libreauto.privileged.SystemServices

internal class NotificationClicks {

    private val statusBar = SystemServices.statusBarService()

    fun click(key: String, rank: Int, count: Int) {
        statusBar.onNotificationClick(key, NotificationVisibility.obtain(key, rank, count, true))
    }
}
