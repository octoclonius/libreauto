package io.github.octoclonius.libreauto.car.home

import android.view.KeyEvent
import io.github.octoclonius.libreauto.car.quicksettings.QuickSettingsState
import io.github.octoclonius.libreauto.car.rail.RailState
import io.github.octoclonius.libreauto.projection.CarSession
import io.github.octoclonius.libreauto.projection.tasks.CarTask

internal class HomeNavigation(
    private val session: CarSession,
    private val home: HomeState,
    private val rail: RailState,
    private val quickSettings: QuickSettingsState,
) {

    fun onAppsButton() {
        quickSettings.close()
        if (isAppInFront()) showHome() else home.toggleDrawer()
    }

    fun onHomeButton() {
        rail.cancelClosing()
        quickSettings.close()
        if (isAppInFront()) {
            showHome()
        } else {
            home.closeDrawer()
            home.showFirstPage()
        }
    }

    fun onBackButton() {
        when {
            rail.cancelClosing() -> {}
            quickSettings.close() -> {}
            home.isDrawerOpen.value -> home.closeDrawer()
            isAppInFront() -> session.displays.car.sendKey(KeyEvent.KEYCODE_BACK)
        }
    }

    fun onRailStatusClick() {
        quickSettings.toggle()
    }

    fun onRailAppLongClick(task: CarTask) {
        quickSettings.close()
        rail.startClosing(task.taskId)
    }

    fun onRailAppClick(task: CarTask) {
        quickSettings.close()
        session.tasks.show(task)
    }

    fun onRailAppClose(task: CarTask) {
        if (session.displays.car.state.value == null) {
            rail.cancelClosing()
            return
        }
        rail.onClosed(task.taskId)
        if (session.tasks.state.value.frontTaskId == task.taskId) session.displays.car.showHome()
        session.tasks.close(task)
    }

    private fun isAppInFront(): Boolean = session.tasks.state.value.frontTaskId != null

    private fun showHome() {
        session.displays.car.showHome()
    }
}
