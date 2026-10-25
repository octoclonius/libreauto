package io.github.octoclonius.libreauto.car.home

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

object HomeState {
    private val _isDrawerOpen = MutableStateFlow(false)
    val isDrawerOpen: StateFlow<Boolean> = _isDrawerOpen.asStateFlow()

    private val _firstPageRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val firstPageRequests: SharedFlow<Unit> = _firstPageRequests.asSharedFlow()

    internal fun showFirstPage() {
        _firstPageRequests.tryEmit(Unit)
    }

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
