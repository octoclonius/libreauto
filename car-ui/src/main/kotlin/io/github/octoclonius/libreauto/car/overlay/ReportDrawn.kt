package io.github.octoclonius.libreauto.car.overlay

import android.view.SurfaceControl
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import io.github.octoclonius.libreauto.projection.display.OverlayDisplay

@Composable
internal fun ReportDrawn(overlay: OverlayDisplay, contentId: Int) {
    val view = LocalView.current
    DisposableEffect(overlay, contentId) {
        val drawn =
            SurfaceControl.Transaction().addTransactionCompletedListener(
                view.context.mainExecutor
            ) {
                overlay.reportDrawn(contentId, it.latchTimeNanos)
            }
        view.rootSurfaceControl?.applyTransactionOnDraw(drawn)
        view.invalidate()
        onDispose {}
    }
}
