package io.github.octoclonius.libreauto.car.home

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object HomeState {
    private val _isHomeShown = MutableStateFlow(false)
    val isHomeShown: StateFlow<Boolean> = _isHomeShown.asStateFlow()

    private val _isDrawerOpen = MutableStateFlow(false)
    val isDrawerOpen: StateFlow<Boolean> = _isDrawerOpen.asStateFlow()

    internal fun onHomeShown(isShown: Boolean) {
        _isHomeShown.value = isShown
        if (!isShown) _isDrawerOpen.value = false
    }

    internal fun toggleDrawer() {
        _isDrawerOpen.value = !_isDrawerOpen.value
    }
}
