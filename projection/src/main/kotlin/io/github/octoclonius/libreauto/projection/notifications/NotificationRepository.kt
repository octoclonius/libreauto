package io.github.octoclonius.libreauto.projection.notifications

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.NotificationListenerService.Ranking
import android.service.notification.NotificationListenerService.RankingMap
import android.service.notification.StatusBarNotification
import android.util.Log
import io.github.octoclonius.libreauto.projection.CarSessionHolder
import io.github.octoclonius.libreauto.projection.permissions.PermissionsRepository
import io.github.octoclonius.libreauto.projection.privileged.PrivilegedServiceConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val FLAG_NO_DISMISS = 0x00002000
private const val FLAG_USER_INITIATED_JOB = 0x00008000
private const val FLAG_SILENT = 0x00020000
private const val EXTRA_BUILDER_APPLICATION_INFO = "android.appInfo"
private const val EXTRA_SUBSTITUTE_APP_NAME = "android.substName"

class NotificationRepository(
    context: Context,
    private val privileged: PrivilegedServiceConnection,
    private val carSessionHolder: CarSessionHolder,
    private val permissions: PermissionsRepository,
    private val externalScope: CoroutineScope,
) {

    private companion object {
        const val TAG = "NotificationRepository"
    }

    private val _notifications = MutableStateFlow<List<PhoneNotification>>(emptyList())
    val notifications: StateFlow<List<PhoneNotification>> = _notifications.asStateFlow()

    val isHeadsUpEnabled: StateFlow<Boolean> =
        headsUpEnabled(context.contentResolver)
            .stateIn(
                externalScope,
                SharingStarted.Eagerly,
                isHeadsUpEnabled(context.contentResolver),
            )

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
        externalScope.launch {
            combine(
                    carSessionHolder.session.map { it != null },
                    permissions.state.map { it.isNotificationAccessAllowed },
                ) { hasSession, isAllowed ->
                    hasSession && isAllowed
                }
                .distinctUntilChanged()
                .collect { isListening -> if (isListening) requestRebind() else onListeningEnded() }
        }
    }

    fun dismiss(key: String, isHeadsUp: Boolean) {
        val rank = rankOf(key) ?: return
        val notification = listed[rank].notification
        privileged.call("dismiss the notification $key") {
            it.onNotificationClear(
                notification.packageName,
                notification.user,
                key,
                isHeadsUp,
                rank,
                listed.size,
            )
        }
    }

    fun onClicked(key: String) {
        val rank = rankOf(key) ?: return
        privileged.call("report a notification click") {
            it.onNotificationClick(key, rank, listed.size)
        }
    }

    internal fun onConnected(listener: NotificationListenerService) {
        if (!hasSession()) {
            Log.i(TAG, "No car session; unbinding the notification listener")
            unbind(listener)
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
        entry.hasInterrupted = entries.put(posted.key, entry)?.hasInterrupted == true
        this.ranking = ranking
        publish()
        val postedRanking = ranking.of(posted.key) ?: return
        val isAlert =
            isAlerting(
                isHeadsUpEnabled = isHeadsUpEnabled.value,
                importance = postedRanking.importance,
                isPeekSuppressed =
                    (postedRanking.suppressedVisualEffects and
                        NotificationManager.Policy.SUPPRESSED_EFFECT_PEEK) != 0,
                isGroupAlertSuppressed = entry.isGroupAlertSuppressed,
                isSilent = entry.isSilent,
                isTooOld =
                    isTooOldToAlert(
                        whenMillis = entry.postedWhen,
                        nowMillis = System.currentTimeMillis(),
                        hasFullScreenIntent = entry.hasFullScreenIntent,
                        isForegroundService = entry.isForegroundService,
                        isUserInitiatedJob = entry.isUserInitiatedJob,
                    ),
                hasInterrupted = entry.hasInterrupted,
                isOnlyAlertOnce = entry.isOnlyAlertOnce,
            )
        if (!isAlert) return
        val target = headsUpTarget(entry, listed) ?: return
        entry.hasInterrupted = true
        _alerts.tryEmit(target.notification)
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

    private fun onListeningEnded() {
        val current = listener ?: return
        unbind(current)
        clear()
    }

    private fun hasSession(): Boolean = carSessionHolder.session.value != null

    private fun requestRebind() {
        listenerCall("rebind the notification listener") {
            NotificationListenerService.requestRebind(permissions.notificationListener)
        }
    }

    private fun unbind(listener: NotificationListenerService) {
        requestRebind()
        listenerCall("unbind the notification listener") { listener.requestUnbind() }
    }

    private fun clear() {
        listener = null
        entries.clear()
        ranking = null
        publish()
    }

    private fun rankOf(key: String): Int? = listed.indexOfFirst { it.key == key }.takeIf { it >= 0 }

    private fun publish() {
        val current = ranking
        listed = if (current == null) emptyList() else listed(current)
        _notifications.value = listed.map { it.notification }
    }

    private fun listed(current: RankingMap): List<NotificationEntry> =
        withoutCoveredSummaries(
            current.orderedKeys.mapNotNull { key ->
                entries[key]?.takeIf { entry ->
                    isListed(
                        isOngoing = entry.isOngoing,
                        isNonDismissable = entry.isNonDismissable,
                        isMedia = entry.isMedia,
                        isHiddenOnPhone = current.of(key)?.isHiddenOnPhone() ?: true,
                    )
                }
            }
        )

    private fun <T> listenerCall(action: String, call: () -> T): T? =
        try {
            call()
        } catch (e: RuntimeException) {
            Log.w(TAG, "Couldn't $action", e)
            null
        }
}

private class NotificationEntry(posted: StatusBarNotification) : GroupedNotification {
    private val flags = posted.notification.flags
    val key: String = posted.key
    val notification =
        PhoneNotification(
            key = posted.key,
            packageName = posted.packageName,
            user = posted.user,
            app =
                posted.notification.extras.getParcelable(
                    EXTRA_BUILDER_APPLICATION_INFO,
                    ApplicationInfo::class.java,
                ),
            substituteAppName =
                posted.notification.extras.getString(EXTRA_SUBSTITUTE_APP_NAME)?.ifEmpty { null },
            title = posted.notification.extras.text(Notification.EXTRA_TITLE),
            text = posted.notification.extras.text(Notification.EXTRA_TEXT),
            smallIcon = posted.notification.smallIcon,
            prefersSmallIcon =
                posted.notification.extras.getBoolean(Notification.EXTRA_PREFER_SMALL_ICON),
            contentIntent = posted.notification.contentIntent,
        )
    override val groupKey: String = posted.groupKey
    override val isGroupSummary = (flags and Notification.FLAG_GROUP_SUMMARY) != 0
    override val postedWhen = posted.notification.`when`
    val isOngoing = posted.isOngoing
    val isNonDismissable = (flags and FLAG_NO_DISMISS) != 0
    val isMedia = posted.notification.extras.containsKey(Notification.EXTRA_MEDIA_SESSION)
    val isOnlyAlertOnce = (flags and Notification.FLAG_ONLY_ALERT_ONCE) != 0
    val isSilent = (flags and FLAG_SILENT) != 0
    val hasFullScreenIntent = posted.notification.fullScreenIntent != null
    val isForegroundService = (flags and Notification.FLAG_FOREGROUND_SERVICE) != 0
    val isUserInitiatedJob = (flags and FLAG_USER_INITIATED_JOB) != 0
    var hasInterrupted = false
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
