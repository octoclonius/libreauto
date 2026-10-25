package io.github.octoclonius.libreauto.car.home

import android.view.KeyEvent
import io.github.octoclonius.libreauto.car.quicksettings.QuickSettingsState
import io.github.octoclonius.libreauto.car.rail.RailState
import io.github.octoclonius.libreauto.session.input.PrivilegedKeys
import io.github.octoclonius.libreauto.session.tasks.CarTask
import io.github.octoclonius.libreauto.session.tasks.CarTasks
import io.github.octoclonius.libreauto.session.tasks.PrivilegedTasks
import io.github.octoclonius.libreauto.session.video.CarDisplay
import io.github.octoclonius.libreauto.session.video.PrivilegedHome

fun onAppsButton() {
    QuickSettingsState.close()
    if (isAppInFront()) showHome() else HomeState.toggleDrawer()
}

fun onHomeButton() {
    RailState.cancelClosing()
    QuickSettingsState.close()
    if (isAppInFront()) {
        showHome()
    } else {
        HomeState.closeDrawer()
        HomeState.showFirstPage()
    }
}

fun onBackButton() {
    when {
        RailState.cancelClosing() -> {}
        QuickSettingsState.close() -> {}
        HomeState.isDrawerOpen.value -> HomeState.closeDrawer()
        isAppInFront() ->
            CarDisplay.state.value?.let { PrivilegedKeys.send(it.id, KeyEvent.KEYCODE_BACK) }
    }
}

fun onRailStatusClick() {
    QuickSettingsState.toggle()
}

fun onRailAppLongClick(task: CarTask) {
    QuickSettingsState.close()
    RailState.startClosing(task.taskId)
}

fun onRailAppClick(task: CarTask) {
    QuickSettingsState.close()
    CarDisplay.state.value?.let { PrivilegedTasks.show(it.id, task.taskId) }
}

fun onRailAppClose(task: CarTask) {
    val display = CarDisplay.state.value
    if (display == null) {
        RailState.cancelClosing()
        return
    }
    RailState.onClosed(task.taskId)
    if (CarTasks.state.value.frontTaskId == task.taskId) PrivilegedHome.show(display.id)
    PrivilegedTasks.close(display.id, task.taskId)
}

private fun isAppInFront(): Boolean = CarTasks.state.value.frontTaskId != null

private fun showHome() {
    CarDisplay.state.value?.let { PrivilegedHome.show(it.id) }
}
