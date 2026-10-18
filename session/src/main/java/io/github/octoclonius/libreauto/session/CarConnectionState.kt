package io.github.octoclonius.libreauto.session

sealed interface CarConnectionState {
    data object Disconnected : CarConnectionState

    data object Connecting : CarConnectionState

    data class Connected(val carName: String?) : CarConnectionState

    data object Failed : CarConnectionState
}
