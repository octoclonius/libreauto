package io.github.octoclonius.libreauto.car.rail

import android.content.Context
import android.hardware.display.DisplayManager
import android.util.Log
import android.view.WindowManager
import io.github.octoclonius.libreauto.session.tasks.CarTask
import io.github.octoclonius.libreauto.session.tasks.CarTasks
import io.github.octoclonius.libreauto.session.video.RailDisplay
import io.github.octoclonius.libreauto.session.video.RailDisplayState
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class RailHost(context: Context) {

    companion object {
        private const val TAG = "RailHost"
    }

    private val context = context.applicationContext
    private val scope = MainScope()
    private val shownCount = MutableStateFlow(MAX_RAIL_APPS)
    private val order = MutableStateFlow<List<CarTask>>(emptyList())
    private var window: RailWindow? = null

    fun start() {
        scope.launch { RailDisplay.state.collect(::show) }
        scope.launch { RailDisplay.touches.collect { window?.dispatchTouchEvent(it) } }
        scope.launch { RailDisplay.outsideTouches.collect { RailState.cancelClosing() } }
        scope.launch {
            RailState.closingTaskId.collect { RailDisplay.captureOutsideTouches(it != null) }
        }
        scope.launch {
            CarTasks.state.collect(RailState::onTasks)
        }
        scope.launch {
            combine(CarTasks.state, RailState.closedTaskIds, shownCount) { tasks, closed, shown ->
                    railOrder(order.value, withoutClosed(tasks, closed), shown)
                }
                .collect { order.value = it }
        }
    }

    private fun show(rail: RailDisplayState?) {
        window?.dismiss()
        window = null
        if (rail == null) return
        val display = context.getSystemService(DisplayManager::class.java).getDisplay(rail.id)
        if (display == null) {
            Log.w(TAG, "The rail display ${rail.id} is already gone")
            return
        }
        val created =
            RailWindow(context, display, rail, order.asStateFlow()) { shownCount.value = it }
        try {
            created.show()
        } catch (e: WindowManager.InvalidDisplayException) {
            Log.w(TAG, "The rail display ${rail.id} went away before the rail showed", e)
            return
        }
        window = created
        Log.i(TAG, "Showing the rail on display ${rail.id}")
    }
}
