package io.github.octoclonius.libreauto.projection.notifications

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import io.github.octoclonius.libreauto.projection.projectionContainer

class NotificationListener : NotificationListenerService() {

    private companion object {
        const val TAG = "NotificationListener"
    }

    private val notifications: NotificationRepository
        get() = projectionContainer.notificationRepository

    override fun onListenerConnected() {
        Log.i(TAG, "Android connected the notification listener")
        notifications.onConnected(this)
    }

    override fun onListenerDisconnected() {
        Log.i(TAG, "Android disconnected the notification listener")
        notifications.onDisconnected(this)
    }

    override fun onNotificationPosted(notification: StatusBarNotification, ranking: RankingMap) {
        notifications.onPosted(notification, ranking)
    }

    override fun onNotificationRemoved(notification: StatusBarNotification, ranking: RankingMap) {
        notifications.onRemoved(notification, ranking)
    }

    override fun onNotificationRankingUpdate(ranking: RankingMap) {
        notifications.onRankingUpdate(ranking)
    }
}
