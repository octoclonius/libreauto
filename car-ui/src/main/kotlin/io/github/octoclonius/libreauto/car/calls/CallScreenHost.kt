package io.github.octoclonius.libreauto.car.calls

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.octoclonius.libreauto.car.overlay.OverlayHost
import io.github.octoclonius.libreauto.car.overlay.ReportDrawn
import io.github.octoclonius.libreauto.projection.CarSession
import io.github.octoclonius.libreauto.projection.display.OverlayDisplay
import java.io.Closeable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

internal class CallScreenHost(context: Context, session: CarSession, state: CallScreenState) :
    Closeable {

    private val hosts =
        listOf(
                session.displays.ongoingCallScreen to state.ongoingScreen,
                session.displays.ringingCallScreen to state.ringingScreen,
            )
            .map { (display, screen) ->
                OverlayHost(context, display) { CallScreenContent(display, screen, state::perform) }
            }

    fun start(scope: CoroutineScope) {
        hosts.forEach { it.start(scope) }
    }

    override fun close() {
        hosts.forEach { it.close() }
    }
}

@Composable
private fun CallScreenContent(
    display: OverlayDisplay,
    screen: StateFlow<CallScreenUiState?>,
    onAction: (CallAction) -> Unit,
) {
    val uiState by screen.collectAsStateWithLifecycle()
    val shown = uiState ?: return
    ReportDrawn(display, shown.call.call.id)
    CallScreen(shown, onAction)
}
