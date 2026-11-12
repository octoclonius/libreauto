package io.github.octoclonius.libreauto.projection

sealed interface CarConnectionState {
    data object Disconnected : CarConnectionState

    data object Connecting : CarConnectionState

    data class Connected(val car: CarInfo) : CarConnectionState

    data object Failed : CarConnectionState
}

data class CarInfo(val name: String?, val isDriverOnRight: Boolean, val statusBar: CarStatusBar)

data class CarStatusBar(
    val isPhoneSignalShown: Boolean,
    val isBatteryShown: Boolean,
)
