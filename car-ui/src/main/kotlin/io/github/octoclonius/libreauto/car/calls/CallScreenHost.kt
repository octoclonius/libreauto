package io.github.octoclonius.libreauto.car.calls

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.octoclonius.libreauto.car.overlay.OverlayHost
import io.github.octoclonius.libreauto.projection.CarSession
import java.io.Closeable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal class CallScreenHost(
    context: Context,
    session: CarSession,
    private val state: CallScreenState,
) : Closeable {

    private val display = session.displays.callScreen
    private val host = OverlayHost(context, display) { CallScreenContent(state) }

    fun start(scope: CoroutineScope) {
        host.start(scope)
        scope.launch { state.isShown.collect(display::show) }
    }

    override fun close() {
        host.close()
    }
}

@Composable
private fun CallScreenContent(state: CallScreenState) {
    val screen by state.screen.collectAsStateWithLifecycle()
    screen?.let { CallScreen(it, state::perform) }
}
