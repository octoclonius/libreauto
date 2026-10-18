package io.github.octoclonius.libreauto.phone.connection

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.octoclonius.libreauto.phone.ui.StatusRow
import io.github.octoclonius.libreauto.session.CarConnectionState

@Composable
fun CarRow(state: CarConnectionState, modifier: Modifier = Modifier) {
    StatusRow(
        title = "Car",
        status =
            when (state) {
                CarConnectionState.Disconnected -> "Connect via USB"
                CarConnectionState.Connecting -> "Connecting..."
                is CarConnectionState.Connected -> state.carName ?: "Connected"
                CarConnectionState.Failed -> "Failed to connect"
            },
        modifier = modifier,
    )
}
