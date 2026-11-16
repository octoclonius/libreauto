package io.github.octoclonius.libreauto.car.overlay

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toAndroidRectF
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.octoclonius.libreauto.car.calls.IncomingCallPopUp
import io.github.octoclonius.libreauto.car.calls.IncomingCallState
import io.github.octoclonius.libreauto.car.notifications.HeadsUp
import io.github.octoclonius.libreauto.car.notifications.NotificationsState
import io.github.octoclonius.libreauto.projection.CarSession
import java.io.Closeable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

internal class PopUpHost(
    context: Context,
    session: CarSession,
    notifications: NotificationsState,
    incomingCall: IncomingCallState,
    onIncomingCallClick: (Int) -> Unit,
) : Closeable {

    private val popUp = session.displays.popUp
    private val isHeadsUpOnScreen = MutableStateFlow(false)
    private val isCallOnScreen = MutableStateFlow(false)
    private val headsUpBounds = MutableStateFlow<Rect?>(null)
    private val callBounds = MutableStateFlow<Rect?>(null)
    private val _isShown = MutableStateFlow(false)
    val isShown: StateFlow<Boolean> = _isShown.asStateFlow()
    private val host =
        OverlayHost(context, popUp) {
            PopUpContent(
                notifications,
                incomingCall,
                onIncomingCallClick,
                onHeadsUpOnScreen = { isHeadsUpOnScreen.value = it },
                onHeadsUpBounds = { headsUpBounds.value = it },
                onCallOnScreen = { isCallOnScreen.value = it },
                onCallBounds = { callBounds.value = it },
            )
        }

    fun start(scope: CoroutineScope) {
        host.start(scope)
        scope.launch {
            combine(isHeadsUpOnScreen, isCallOnScreen) { headsUp, call -> headsUp || call }
                .collect { _isShown.value = it }
        }
        scope.launch {
            combine(headsUpBounds, callBounds, ::touchArea).collect(popUp::setTouchArea)
        }
    }

    override fun close() {
        host.close()
    }
}

@Composable
private fun PopUpContent(
    notifications: NotificationsState,
    incomingCall: IncomingCallState,
    onIncomingCallClick: (Int) -> Unit,
    onHeadsUpOnScreen: (Boolean) -> Unit,
    onHeadsUpBounds: (Rect?) -> Unit,
    onCallOnScreen: (Boolean) -> Unit,
    onCallBounds: (Rect?) -> Unit,
) {
    val headsUp by notifications.headsUp.collectAsStateWithLifecycle()
    val incoming by incomingCall.incoming.collectAsStateWithLifecycle()
    HeadsUp(
        headsUp = headsUp,
        onClick = notifications::openHeadsUp,
        onHide = notifications::hideHeadsUp,
        onDismiss = notifications::dismissHeadsUp,
        onHold = notifications::holdHeadsUp,
        onOnScreen = onHeadsUpOnScreen,
        onBounds = onHeadsUpBounds,
    )
    IncomingCallPopUp(
        incoming = incoming,
        onAnswer = incomingCall::answer,
        onDecline = incomingCall::decline,
        onOpen = onIncomingCallClick,
        onHide = incomingCall::hide,
        onOnScreen = onCallOnScreen,
        onBounds = onCallBounds,
    )
}

private fun touchArea(headsUp: Rect?, call: Rect?): android.graphics.Rect {
    val area = android.graphics.Rect()
    for (bounds in listOfNotNull(headsUp, call)) {
        val rounded = android.graphics.Rect()
        bounds.toAndroidRectF().roundOut(rounded)
        area.union(rounded)
    }
    return area
}
