package io.github.octoclonius.libreauto.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class CarSessionHolder {

    private val _state = MutableStateFlow<CarConnectionState>(CarConnectionState.Disconnected)
    val state: StateFlow<CarConnectionState> = _state.asStateFlow()

    private val _session = MutableStateFlow<CarSession?>(null)
    val session: StateFlow<CarSession?> = _session.asStateFlow()

    internal fun onState(state: CarConnectionState) {
        _state.value = state
    }

    internal fun onSession(session: CarSession?) {
        _session.value = session
    }
}
