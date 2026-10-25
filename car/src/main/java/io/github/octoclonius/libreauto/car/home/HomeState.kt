package io.github.octoclonius.libreauto.car.home

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object HomeState {
    private val _isDrawerOpen = MutableStateFlow(false)
    val isDrawerOpen: StateFlow<Boolean> = _isDrawerOpen.asStateFlow()

    internal fun onHomeHidden() {
        _isDrawerOpen.value = false
    }

    internal fun closeDrawer() {
        _isDrawerOpen.value = false
    }

    internal fun toggleDrawer() {
        _isDrawerOpen.value = !_isDrawerOpen.value
    }
}
