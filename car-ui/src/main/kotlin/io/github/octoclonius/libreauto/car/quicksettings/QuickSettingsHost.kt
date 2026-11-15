package io.github.octoclonius.libreauto.car.quicksettings

import android.app.PendingIntent
import android.content.Context
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.octoclonius.libreauto.car.calls.CallCard
import io.github.octoclonius.libreauto.car.calls.CallCardState
import io.github.octoclonius.libreauto.car.calls.CallUiState
import io.github.octoclonius.libreauto.car.calls.HangUpButton
import io.github.octoclonius.libreauto.car.calls.RingingCallButtons
import io.github.octoclonius.libreauto.car.notifications.NotificationList
import io.github.octoclonius.libreauto.car.notifications.NotificationsState
import io.github.octoclonius.libreauto.car.overlay.OverlayHost
import io.github.octoclonius.libreauto.projection.CarSession
import io.github.octoclonius.libreauto.projection.calls.CallStatus
import io.github.octoclonius.libreauto.projection.settings.PhoneSettingsRepository
import java.io.Closeable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal class QuickSettingsHost(
    context: Context,
    private val session: CarSession,
    private val state: QuickSettingsState,
    phoneSettings: PhoneSettingsRepository,
    notifications: NotificationsState,
    callCard: CallCardState,
) : Closeable {

    private val panel = session.displays.panel
    private val host =
        OverlayHost(context, panel) {
            QuickSettingsContent(
                session,
                state,
                phoneSettings,
                notifications,
                callCard,
                ::showPage,
            )
        }

    fun start(scope: CoroutineScope) {
        host.start(scope)
        scope.launch { panel.state.collect { if (it == null) state.close() } }
        scope.launch { panel.outsideTouches.captured.collect { state.close() } }
        scope.launch {
            state.isOpen.collect {
                panel.outsideTouches.setCapturing(it)
                panel.show(it)
            }
        }
    }

    override fun close() {
        host.close()
    }

    private fun showPage(page: PendingIntent) {
        state.close()
        session.displays.car.show(page)
    }
}

@Composable
private fun QuickSettingsContent(
    session: CarSession,
    state: QuickSettingsState,
    phoneSettings: PhoneSettingsRepository,
    notifications: NotificationsState,
    callCard: CallCardState,
    onShowPage: (PendingIntent) -> Unit,
) {
    val rail by session.displays.rail.state.collectAsStateWithLifecycle()
    val settings by remember { phoneSettings.settings }.collectAsStateWithLifecycle(null)
    val isOpen by state.isOpen.collectAsStateWithLifecycle()
    val list by notifications.notifications.collectAsStateWithLifecycle()
    val call by callCard.card.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    LaunchedEffect(isOpen) { if (!isOpen) listState.scrollToItem(0) }
    QuickSettingsPanel(
        settings = settings,
        railSide = rail?.side,
        isDriverOnRight = session.car.isDriverOnRight,
        onToggle = phoneSettings::set,
        onClose = { state.close() },
        onShowPage = onShowPage,
        hasNotifications = list.isNotEmpty() || call != null,
    ) { contentPadding ->
        NotificationList(
            list,
            onClick = notifications::open,
            onDismiss = notifications::dismiss,
            state = listState,
            contentPadding = contentPadding,
            header = call?.let { shown -> { CallEntry(shown, callCard) } },
        )
    }
}

@Composable
private fun CallEntry(uiState: CallUiState, state: CallCardState) {
    val id = uiState.call.id
    CallCard(uiState, MaterialTheme.colorScheme.surfaceContainerHighest) {
        if (uiState.call.status == CallStatus.Ringing) {
            RingingCallButtons(onDecline = { state.decline(id) }, onAnswer = { state.answer(id) })
        } else {
            HangUpButton { state.hangUp(id) }
        }
    }
}
