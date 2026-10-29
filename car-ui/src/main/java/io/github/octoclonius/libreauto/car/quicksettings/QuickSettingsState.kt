package io.github.octoclonius.libreauto.car.quicksettings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update

internal class QuickSettingsState {
    private val _isOpen = MutableStateFlow(false)
    val isOpen: StateFlow<Boolean> = _isOpen.asStateFlow()

    fun toggle() {
        _isOpen.update { !it }
    }

    fun close(): Boolean = _isOpen.getAndUpdate { false }
}
