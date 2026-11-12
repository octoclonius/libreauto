package io.github.octoclonius.libreauto.car.notifications

import android.content.Context
import android.graphics.Rect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.toAndroidRectF
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.octoclonius.libreauto.car.overlay.OverlayHost
import io.github.octoclonius.libreauto.projection.CarSession
import io.github.octoclonius.libreauto.projection.display.OverlayDisplay
import java.io.Closeable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal class HeadsUpHost(
    context: Context,
    session: CarSession,
    private val notifications: NotificationsState,
) : Closeable {

    private val popUp = session.displays.popUp
    private val host = OverlayHost(context, popUp) { HeadsUpContent(popUp, notifications) }

    fun start(scope: CoroutineScope) {
        host.start(scope)
        scope.launch { notifications.isHeadsUpOnScreen.collect(popUp::show) }
    }

    override fun close() {
        host.close()
    }
}

@Composable
private fun HeadsUpContent(popUp: OverlayDisplay, notifications: NotificationsState) {
    val headsUp by notifications.headsUp.collectAsStateWithLifecycle()
    HeadsUp(
        headsUp = headsUp,
        onClick = notifications::openHeadsUp,
        onHide = notifications::hideHeadsUp,
        onDismiss = notifications::dismissHeadsUp,
        onHold = notifications::holdHeadsUp,
        onOnScreen = notifications::onHeadsUpOnScreen,
        onBounds = { bounds ->
            popUp.setTouchArea(Rect().also { bounds?.toAndroidRectF()?.roundOut(it) })
        },
    )
}
