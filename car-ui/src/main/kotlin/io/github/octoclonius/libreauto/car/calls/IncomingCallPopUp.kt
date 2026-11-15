package io.github.octoclonius.libreauto.car.calls

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Rect
import io.github.octoclonius.libreauto.car.overlay.PopUp
import io.github.octoclonius.libreauto.car.overlay.PopUpDefaults

@Composable
internal fun IncomingCallPopUp(
    incoming: CallUiState?,
    onAnswer: (Int) -> Unit,
    onDecline: (Int) -> Unit,
    onOpen: (Int) -> Unit,
    onHide: () -> Unit,
    onOnScreen: (Boolean) -> Unit,
    onBounds: (Rect?) -> Unit,
) {
    PopUp(
        incoming,
        key = { it.call.id },
        onHide = onHide,
        onOnScreen = onOnScreen,
        onBounds = onBounds,
    ) { shown ->
        CallCard(
            shown,
            PopUpDefaults.color,
            border = PopUpDefaults.border,
            onClick = { onOpen(shown.call.id) },
        ) {
            RingingCallButtons(
                onDecline = { onDecline(shown.call.id) },
                onAnswer = { onAnswer(shown.call.id) },
            )
        }
    }
}
