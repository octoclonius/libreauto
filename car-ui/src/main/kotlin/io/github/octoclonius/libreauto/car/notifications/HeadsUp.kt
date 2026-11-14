package io.github.octoclonius.libreauto.car.notifications

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import io.github.octoclonius.libreauto.car.overlay.PopUp
import io.github.octoclonius.libreauto.car.overlay.PopUpDefaults

@Composable
internal fun HeadsUp(
    headsUp: NotificationUiState?,
    onClick: (NotificationUiState) -> Unit,
    onHide: () -> Unit,
    onDismiss: (NotificationUiState) -> Unit,
    onHold: (Boolean) -> Unit,
    onOnScreen: (Boolean) -> Unit,
    onBounds: (Rect?) -> Unit,
) {
    PopUp(
        headsUp,
        key = { it.notification.key },
        onHide = onHide,
        onOnScreen = onOnScreen,
        onBounds = onBounds,
        modifier = Modifier.holdOnTouch(onHold),
    ) { shown ->
        SwipeToDismissBox(
            rememberSwipeToDismissBoxState(),
            backgroundContent = {},
            onDismiss = { onDismiss(shown) },
        ) {
            NotificationCard(
                shown,
                PopUpDefaults.color,
                onClick = { onClick(shown) },
                border = PopUpDefaults.border,
            )
        }
    }
}

private fun Modifier.holdOnTouch(onHold: (Boolean) -> Unit): Modifier =
    pointerInput(onHold) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            onHold(true)
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
            } while (event.changes.any { it.pressed })
            onHold(false)
        }
    }
