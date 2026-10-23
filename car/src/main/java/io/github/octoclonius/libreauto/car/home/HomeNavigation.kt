package io.github.octoclonius.libreauto.car.home

import android.view.KeyEvent
import io.github.octoclonius.libreauto.session.input.PrivilegedKeys
import io.github.octoclonius.libreauto.session.video.CarDisplay

fun onAppsButton() {
    if (HomeState.isHomeShown.value) HomeState.toggleDrawer() else showHome()
}

fun onHomeButton() {
    if (HomeState.isHomeShown.value) HomeState.closeDrawer() else showHome()
}

fun onBackButton() {
    when {
        HomeState.isDrawerOpen.value -> HomeState.closeDrawer()
        HomeState.isHomeShown.value -> {}
        else -> CarDisplay.state.value?.let { PrivilegedKeys.send(it.id, KeyEvent.KEYCODE_BACK) }
    }
}

private fun showHome() {
    CarDisplay.state.value?.let { PrivilegedKeys.send(it.id, KeyEvent.KEYCODE_HOME) }
}
