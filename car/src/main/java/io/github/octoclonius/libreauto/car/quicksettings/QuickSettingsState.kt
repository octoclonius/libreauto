package io.github.octoclonius.libreauto.car.quicksettings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update

object QuickSettingsState {
    private val _isOpen = MutableStateFlow(false)
    val isOpen: StateFlow<Boolean> = _isOpen.asStateFlow()

    internal fun toggle() {
        _isOpen.update { !it }
    }

    internal fun close(): Boolean = _isOpen.getAndUpdate { false }
}
