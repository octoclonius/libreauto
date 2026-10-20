package io.github.octoclonius.libreauto.session

sealed interface CarConnectionState {
    data object Disconnected : CarConnectionState

    data object Connecting : CarConnectionState

    data class Connected(
        val carName: String?,
        val isDriverOnRight: Boolean,
        val carStatusBar: CarStatusBar,
    ) : CarConnectionState

    data object Failed : CarConnectionState
}

data class CarStatusBar(
    val isClockShown: Boolean,
    val isPhoneSignalShown: Boolean,
    val isBatteryShown: Boolean,
)
