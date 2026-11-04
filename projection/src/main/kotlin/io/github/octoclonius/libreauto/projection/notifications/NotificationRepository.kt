package io.github.octoclonius.libreauto.projection.notifications

import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.NotificationListenerService.Ranking
import android.service.notification.NotificationListenerService.RankingMap
import android.service.notification.StatusBarNotification
import android.util.Log
import io.github.octoclonius.libreauto.projection.CarSessionHolder
import io.github.octoclonius.libreauto.projection.privileged.PrivilegedServiceConnection
import io.github.octoclonius.libreauto.projection.privileged.call
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class NotificationRepository(
    context: Context,
    private val privileged: PrivilegedServiceConnection,
    private val carSessionHolder: CarSessionHolder,
    private val externalScope: CoroutineScope,
) {

    private companion object {
        const val TAG = "NotificationRepository"
    }

    private val listenerComponent = ComponentName(context, NotificationListener::class.java)
    private val notificationManager = context.getSystemService(NotificationManager::class.java)

    private val _notifications = MutableStateFlow<List<PhoneNotification>>(emptyList())
    val notifications: StateFlow<List<PhoneNotification>> = _notifications.asStateFlow()

    private val _alerts =
        MutableSharedFlow<PhoneNotification>(
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
    val alerts: SharedFlow<PhoneNotification> = _alerts.asSharedFlow()

    private var listener: NotificationListenerService? = null
    private val entries = HashMap<String, NotificationEntry>()
    private var ranking: RankingMap? = null
    private var listed: List<NotificationEntry> = emptyList()

    fun start() {
        externalScope.launch(Dispatchers.IO) {
            privileged.service.collect { service ->
                if (notificationManager.isNotificationListenerAccessGranted(listenerComponent)) {
                    return@collect
                }
                service?.call("allow the phone's notifications") {
                    it.allowNotificationAccess(listenerComponent)
                } ?: return@collect
                if (carSessionHolder.session.value != null) requestRebind()
            }
        }
        externalScope.launch {
            carSessionHolder.session
                .map { it != null }
                .distinctUntilChanged()
                .collect { hasSession -> if (hasSession) requestRebind() else requestUnbind() }
        }
    }

    fun cancel(key: String) {
        val current = listener ?: return
        listenerCall("cancel the notification $key") { current.cancelNotification(key) }
    }

    fun onClicked(key: String) {
        val rank = listed.indexOfFirst { it.key == key }
        if (rank < 0) return
        privileged.call("report a notification click") {
            it.onNotificationClick(key, rank, listed.size)
        }
    }

    internal fun onConnected(listener: NotificationListenerService) {
        if (carSessionHolder.session.value == null) {
            Log.i(TAG, "No car session; unbinding the notification listener")
            listenerCall("unbind the notification listener") { listener.requestUnbind() }
            return
        }
        this.listener = listener
        entries.clear()
        listenerCall("read the phone's notifications") { listener.activeNotifications }
            ?.forEach { entries[it.key] = NotificationEntry(it) }
        ranking = listenerCall("read the notification ranking") { listener.currentRanking }
        publish()
    }

    internal fun onDisconnected(listener: NotificationListenerService) {
        if (this.listener === listener) clear()
    }

    internal fun onPosted(posted: StatusBarNotification, ranking: RankingMap) {
        val entry = NotificationEntry(posted)
        val isUpdate = entries.put(posted.key, entry) != null
        this.ranking = ranking
        publish()
        val postedRanking = ranking.of(posted.key) ?: return
        val isAlert =
            isAlerting(
                importance = postedRanking.importance,
                isPeekSuppressed =
                    (postedRanking.suppressedVisualEffects and
                        NotificationManager.Policy.SUPPRESSED_EFFECT_PEEK) != 0,
                isGroupAlertSuppressed = entry.isGroupAlertSuppressed,
                isUpdate = isUpdate,
                isOnlyAlertOnce = entry.isOnlyAlertOnce,
            )
        if (isAlert) alertTarget(entry)?.let { _alerts.tryEmit(it.notification) }
    }

    internal fun onRemoved(removed: StatusBarNotification, ranking: RankingMap) {
        entries.remove(removed.key)
        this.ranking = ranking
        publish()
    }

    internal fun onRankingUpdate(ranking: RankingMap) {
        this.ranking = ranking
        publish()
    }

    private fun requestRebind() {
        listenerCall("rebind the notification listener") {
            NotificationListenerService.requestRebind(listenerComponent)
        }
    }

    private fun requestUnbind() {
        val current = listener ?: return
        listenerCall("unbind the notification listener") { current.requestUnbind() }
        clear()
    }

    private fun clear() {
        listener = null
        entries.clear()
        ranking = null
        publish()
    }

    private fun alertTarget(posted: NotificationEntry): NotificationEntry? =
        when {
            listed.any { it.key == posted.key } -> posted
            posted.isGroupSummary ->
                listed.filter { it.groupKey == posted.groupKey }.maxByOrNull { it.postedWhen }
            else -> null
        }

    private fun publish() {
        val current = ranking
        listed = if (current == null) emptyList() else listed(current)
        _notifications.value = listed.map { it.notification }
    }

    private fun listed(current: RankingMap): List<NotificationEntry> {
        val listed =
            current.orderedKeys.mapNotNull { key ->
                entries[key]?.takeIf { entry ->
                    isListed(
                        isClearable = entry.isClearable,
                        isForegroundService = entry.isForegroundService,
                        isMedia = entry.isMedia,
                        isHiddenOnPhone = current.of(key)?.isHiddenOnPhone() ?: true,
                    )
                }
            }
        val groupsWithChildren =
            listed.filterNot { it.isGroupSummary }.mapTo(HashSet()) { it.groupKey }
        return listed.filterNot { it.isGroupSummary && it.groupKey in groupsWithChildren }
    }

    private fun <T> listenerCall(action: String, call: () -> T): T? =
        try {
            call()
        } catch (e: RuntimeException) {
            Log.w(TAG, "Couldn't $action", e)
            null
        }
}

private class NotificationEntry(posted: StatusBarNotification) {
    private val flags = posted.notification.flags
    val key: String = posted.key
    val notification =
        PhoneNotification(
            key = posted.key,
            packageName = posted.packageName,
            user = posted.user,
            title = posted.notification.extras.text(Notification.EXTRA_TITLE),
            text = posted.notification.extras.text(Notification.EXTRA_TEXT),
            contentIntent = posted.notification.contentIntent,
        )
    val groupKey: String = posted.groupKey
    val isGroupSummary = (flags and Notification.FLAG_GROUP_SUMMARY) != 0
    val postedWhen = posted.notification.`when`
    val isClearable = posted.isClearable
    val isForegroundService = (flags and Notification.FLAG_FOREGROUND_SERVICE) != 0
    val isMedia = posted.notification.extras.containsKey(Notification.EXTRA_MEDIA_SESSION)
    val isOnlyAlertOnce = (flags and Notification.FLAG_ONLY_ALERT_ONCE) != 0
    val isGroupAlertSuppressed =
        isGroupAlertSuppressed(
            isInGroup = posted.notification.group != null,
            isGroupSummary = isGroupSummary,
            groupAlertBehavior = posted.notification.groupAlertBehavior,
        )
}

private fun Bundle.text(key: String): String = getCharSequence(key)?.toString().orEmpty()

private fun RankingMap.of(key: String): Ranking? = Ranking().takeIf { getRanking(key, it) }

private fun Ranking.isHiddenOnPhone(): Boolean =
    isSuspended ||
        (suppressedVisualEffects and
            NotificationManager.Policy.SUPPRESSED_EFFECT_NOTIFICATION_LIST) != 0
