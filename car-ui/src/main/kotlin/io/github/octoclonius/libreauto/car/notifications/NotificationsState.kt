package io.github.octoclonius.libreauto.car.notifications

import android.content.Context
import android.os.SystemClock
import android.view.accessibility.AccessibilityManager
import io.github.octoclonius.libreauto.car.apps.carDensities
import io.github.octoclonius.libreauto.car.quicksettings.QuickSettingsState
import io.github.octoclonius.libreauto.projection.display.CarDisplay
import io.github.octoclonius.libreauto.projection.notifications.NotificationRepository
import io.github.octoclonius.libreauto.projection.notifications.PhoneNotification
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

private const val HEADS_UP_MILLIS = 8_000
private const val TOUCH_ACCEPTANCE_DELAY_MILLIS = 700L

private class HeadsUpAlert(val key: String, val shownAt: Long)

internal class NotificationsState(
    context: Context,
    private val repository: NotificationRepository,
    private val quickSettings: QuickSettingsState,
    private val carDisplay: CarDisplay,
) {
    private val apps = NotificationApps(context)
    private val accessibility = context.getSystemService(AccessibilityManager::class.java)

    private val _notifications = MutableStateFlow<List<NotificationUiState>>(emptyList())
    val notifications: StateFlow<List<NotificationUiState>> = _notifications.asStateFlow()

    private val alert = MutableStateFlow<HeadsUpAlert?>(null)
    private val heldAlert = MutableStateFlow<HeadsUpAlert?>(null)

    private val _headsUp = MutableStateFlow<NotificationUiState?>(null)
    val headsUp: StateFlow<NotificationUiState?> = _headsUp.asStateFlow()

    private val _isHeadsUpOnScreen = MutableStateFlow(false)
    val isHeadsUpOnScreen: StateFlow<Boolean> = _isHeadsUpOnScreen.asStateFlow()

    fun start(scope: CoroutineScope) {
        scope.launch {
            combine(repository.notifications, carDensities(carDisplay)) { list, carDensity ->
                    list.map { apps.uiState(it, carDensity) }
                }
                .flowOn(Dispatchers.IO)
                .collect { _notifications.value = it }
        }
        scope.launch {
            combine(alert, _notifications) { shown, list ->
                    shown?.let { list.firstOrNull { item -> item.notification.key == it.key } }
                }
                .collect { _headsUp.value = it }
        }
        scope.launch { repository.alerts.collect(::showHeadsUp) }
        scope.launch {
            repository.notifications.collect { list ->
                val shown = alert.value ?: return@collect
                if (list.none { it.key == shown.key }) hideHeadsUp()
            }
        }
        scope.launch {
            combine(alert, heldAlert) { shown, held -> shown?.takeUnless { it === held } }
                .distinctUntilChanged()
                .collectLatest { shown ->
                    if (shown == null) return@collectLatest
                    delay(headsUpMillis())
                    alert.compareAndSet(shown, null)
                }
        }
        scope.launch { quickSettings.isOpen.collect { if (it) hideHeadsUp() } }
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
        val isJustShown =
            shown != null &&
                SystemClock.elapsedRealtime() - shown.shownAt < TOUCH_ACCEPTANCE_DELAY_MILLIS
        if (!isJustShown) open(item)
    }

    fun dismiss(item: NotificationUiState) {
        if (alert.value?.key == item.notification.key) hideHeadsUp()
        repository.cancel(item.notification.key)
    }

    fun hideHeadsUp() {
        alert.value = null
    }

    fun holdHeadsUp(isHeld: Boolean) {
        heldAlert.value = if (isHeld) alert.value else null
    }

    fun onHeadsUpOnScreen(isOnScreen: Boolean) {
        _isHeadsUpOnScreen.value = isOnScreen
    }

    private fun showHeadsUp(notification: PhoneNotification) {
        if (quickSettings.isOpen.value) return
        if (repository.notifications.value.none { it.key == notification.key }) return
        alert.value = HeadsUpAlert(notification.key, SystemClock.elapsedRealtime())
    }

    private fun headsUpMillis(): Long =
        accessibility
            .getRecommendedTimeoutMillis(
                HEADS_UP_MILLIS,
                AccessibilityManager.FLAG_CONTENT_CONTROLS or
                    AccessibilityManager.FLAG_CONTENT_ICONS or
                    AccessibilityManager.FLAG_CONTENT_TEXT,
            )
            .toLong()
}
