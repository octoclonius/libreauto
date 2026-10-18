package io.github.octoclonius.libreauto.phone.connection

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.octoclonius.libreauto.phone.R
import io.github.octoclonius.libreauto.phone.ui.StatusRow
import io.github.octoclonius.libreauto.session.CarConnectionState

@Composable
fun CarRow(state: CarConnectionState, modifier: Modifier = Modifier) {
    StatusRow(
        title = stringResource(R.string.car_title),
        status =
            when (state) {
                CarConnectionState.Disconnected -> stringResource(R.string.car_disconnected)
                CarConnectionState.Connecting -> stringResource(R.string.car_connecting)
                is CarConnectionState.Connected ->
                    state.carName ?: stringResource(R.string.car_connected)
                CarConnectionState.Failed -> stringResource(R.string.car_failed)
            },
        modifier = modifier,
    )
}
