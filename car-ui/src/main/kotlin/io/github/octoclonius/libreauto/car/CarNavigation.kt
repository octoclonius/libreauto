package io.github.octoclonius.libreauto.car

import android.view.KeyEvent
import io.github.octoclonius.libreauto.car.calls.CallScreenState
import io.github.octoclonius.libreauto.car.home.HomeState
import io.github.octoclonius.libreauto.car.quicksettings.QuickSettingsState
import io.github.octoclonius.libreauto.car.rail.RailActions
import io.github.octoclonius.libreauto.car.rail.RailState
import io.github.octoclonius.libreauto.projection.CarSession
import io.github.octoclonius.libreauto.projection.tasks.CarTask

internal class CarNavigation(
    private val session: CarSession,
    private val home: HomeState,
    private val rail: RailState,
    private val quickSettings: QuickSettingsState,
    private val callScreen: CallScreenState,
) : RailActions {

    override fun onAppsButton() {
        quickSettings.close()
        if (callScreen.close() || isAppInFront()) showHome() else home.toggleDrawer()
    }

    fun onHomeButton() {
        rail.cancelClosing()
        quickSettings.close()
        callScreen.close()
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
            callScreen.close() -> {}
            home.isDrawerOpen.value -> home.closeDrawer()
            isAppInFront() -> session.displays.car.sendKey(KeyEvent.KEYCODE_BACK)
        }
    }

    override fun onStatusClick() {
        quickSettings.toggle()
    }

    override fun onAppLongClick(task: CarTask) {
        quickSettings.close()
        rail.startClosing(task.taskId)
    }

    override fun onAppClick(task: CarTask) {
        quickSettings.close()
        callScreen.close()
        session.tasks.show(task)
    }

    fun onCallCardClick() {
        quickSettings.close()
        callScreen.open()
    }

    override fun onAppClose(task: CarTask) {
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
