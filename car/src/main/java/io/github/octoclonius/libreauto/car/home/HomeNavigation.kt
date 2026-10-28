package io.github.octoclonius.libreauto.car.home

import android.view.KeyEvent
import io.github.octoclonius.libreauto.car.quicksettings.QuickSettingsState
import io.github.octoclonius.libreauto.car.rail.RailState
import io.github.octoclonius.libreauto.session.CarSession
import io.github.octoclonius.libreauto.session.input.PrivilegedKeys
import io.github.octoclonius.libreauto.session.tasks.CarTask
import io.github.octoclonius.libreauto.session.tasks.PrivilegedTasks
import io.github.octoclonius.libreauto.session.video.PrivilegedHome

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
            isAppInFront() -> carDisplayId()?.let { PrivilegedKeys.send(it, KeyEvent.KEYCODE_BACK) }
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
        carDisplayId()?.let { PrivilegedTasks.show(it, task.taskId) }
    }

    fun onRailAppClose(task: CarTask) {
        val displayId = carDisplayId()
        if (displayId == null) {
            rail.cancelClosing()
            return
        }
        rail.onClosed(task.taskId)
        if (session.tasks.state.value.frontTaskId == task.taskId) PrivilegedHome.show(displayId)
        PrivilegedTasks.close(displayId, task.taskId)
    }

    private fun isAppInFront(): Boolean = session.tasks.state.value.frontTaskId != null

    private fun showHome() {
        carDisplayId()?.let(PrivilegedHome::show)
    }

    private fun carDisplayId(): Int? = session.displays.car.state.value?.id
}
