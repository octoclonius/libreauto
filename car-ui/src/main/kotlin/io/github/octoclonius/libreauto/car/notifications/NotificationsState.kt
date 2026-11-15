package io.github.octoclonius.libreauto.car.notifications

import android.content.Context
import android.os.SystemClock
import android.view.accessibility.AccessibilityManager
import io.github.octoclonius.libreauto.car.apps.carDensities
import io.github.octoclonius.libreauto.car.overlay.isTouchAccepted
import io.github.octoclonius.libreauto.car.quicksettings.QuickSettingsState
import io.github.octoclonius.libreauto.projection.display.CarDisplay
import io.github.octoclonius.libreauto.projection.notifications.NotificationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

private const val HUN_DEFAULT_HEADS_UP_TIMEOUT_MILLIS = 8_000

private class HeadsUpAlert(val key: String, val shownAt: Long)

internal class NotificationsState(
    context: Context,
    private val repository: NotificationRepository,
    private val quickSettings: QuickSettingsState,
    private val carDisplay: CarDisplay,
    private val isCallShown: StateFlow<Boolean>,
) {
    private val apps = NotificationApps(context)
    private val accessibility = context.getSystemService(AccessibilityManager::class.java)

    private val uiStates = MutableStateFlow<List<NotificationUiState>>(emptyList())

    private val _notifications = MutableStateFlow<List<NotificationUiState>>(emptyList())
    val notifications: StateFlow<List<NotificationUiState>> = _notifications.asStateFlow()

    private val alert = MutableStateFlow<HeadsUpAlert?>(null)
    private val heldAlert = MutableStateFlow<HeadsUpAlert?>(null)
    private var waitingAlertKey: String? = null

    private val _headsUp = MutableStateFlow<NotificationUiState?>(null)
    val headsUp: StateFlow<NotificationUiState?> = _headsUp.asStateFlow()

    fun start(scope: CoroutineScope) {
        scope.launch {
            combine(repository.notifications, carDensities(carDisplay)) { list, carDensity ->
                    apps.uiStates(list, carDensity)
                }
                .flowOn(Dispatchers.IO)
                .collect { uiStates.value = it }
        }
        scope.launch {
            combine(alert, uiStates) { shown, list ->
                    shown?.let { list.firstOrNull { item -> item.notification.key == it.key } }
                }
                .collect { _headsUp.value = it }
        }
        scope.launch {
            combine(uiStates, alert) { list, shown ->
                    list.filterNot { it.notification.key == shown?.key }
                }
                .collect { _notifications.value = it }
        }
        scope.launch { repository.alerts.collect { showHeadsUp(it.key) } }
        scope.launch {
            combine(alert, heldAlert) { shown, held -> shown?.takeUnless { it === held } }
                .distinctUntilChanged()
                .collectLatest { shown ->
                    if (shown == null) return@collectLatest
                    delay(headsUpMillis())
                    alert.compareAndSet(shown, null)
                }
        }
        scope.launch {
            repository.notifications.collect { list ->
                val shown = alert.value ?: return@collect
                if (list.none { it.key == shown.key }) hideHeadsUp()
            }
        }
        scope.launch {
            isCallShown.collect { isShown ->
                if (isShown) {
                    hideHeadsUp()
                } else {
                    val key = waitingAlertKey ?: return@collect
                    waitingAlertKey = null
                    if (repository.notifications.value.any { it.key == key }) showHeadsUp(key)
                }
            }
        }
        scope.launch { repository.isHeadsUpEnabled.collect { if (!it) hideHeadsUp() } }
    }

    fun open(item: NotificationUiState) {
        val notification = item.notification
        val activity = notification.contentIntent?.takeIf { it.isActivity } ?: return
        quickSettings.close()
        hideHeadsUp()
        carDisplay.show(activity)
        repository.onClicked(notification.key)
    }

    fun openHeadsUp(item: NotificationUiState) {
        val shown = alert.value
        if (shown == null || isTouchAccepted(shown.shownAt, SystemClock.elapsedRealtime())) {
            open(item)
        }
    }

    fun dismiss(item: NotificationUiState) {
        repository.dismiss(item.notification.key, isHeadsUp = false)
    }

    fun dismissHeadsUp(item: NotificationUiState) {
        hideHeadsUp()
        repository.dismiss(item.notification.key, isHeadsUp = true)
    }

    fun hideHeadsUp() {
        alert.value = null
    }

    fun holdHeadsUp(isHeld: Boolean) {
        heldAlert.value = if (isHeld) alert.value else null
    }

    private fun showHeadsUp(key: String) {
        if (isCallShown.value) {
            waitingAlertKey = key
            return
        }
        alert.value = HeadsUpAlert(key, SystemClock.elapsedRealtime())
    }

    private fun headsUpMillis(): Long =
        accessibility
            .getRecommendedTimeoutMillis(
                HUN_DEFAULT_HEADS_UP_TIMEOUT_MILLIS,
                AccessibilityManager.FLAG_CONTENT_CONTROLS or
                    AccessibilityManager.FLAG_CONTENT_ICONS or
                    AccessibilityManager.FLAG_CONTENT_TEXT,
            )
            .toLong()
}
