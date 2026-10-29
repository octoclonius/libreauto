package io.github.octoclonius.libreauto.car.rail

import android.content.Context
import android.hardware.display.DisplayManager
import android.util.Log
import android.view.WindowManager
import io.github.octoclonius.libreauto.car.CarUi
import io.github.octoclonius.libreauto.session.tasks.CarTask
import io.github.octoclonius.libreauto.session.display.RailDisplayState
import java.io.Closeable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

internal class RailHost(context: Context, private val carUi: CarUi) : Closeable {

    companion object {
        private const val TAG = "RailHost"
    }

    private val context = context.applicationContext
    private val shownCount = MutableStateFlow(MAX_RAIL_APPS)
    private val order = MutableStateFlow<List<CarTask>>(emptyList())
    private var window: RailWindow? = null

    fun start(scope: CoroutineScope) {
        val display = carUi.session.displays.rail
        val rail = carUi.rail
        val carTasks = carUi.session.tasks
        scope.launch { display.state.collect(::show) }
        scope.launch { display.touches.collect { window?.dispatchTouchEvent(it) } }
        scope.launch { display.outsideTouches.collect { rail.cancelClosing() } }
        scope.launch { rail.closingTaskId.collect { display.captureOutsideTouches(it != null) } }
        scope.launch { carTasks.state.collect(rail::onTasks) }
        scope.launch {
            combine(carTasks.state, rail.closedTaskIds, shownCount) { tasks, closed, shown ->
                    nextRailOrder(
                        order.value,
                        carUi.railOrder.taskIds.value,
                        withoutClosed(tasks, closed),
                        shown,
                    )
                }
                .collect(::onOrder)
        }
    }

    override fun close() {
        window?.dismiss()
        window = null
    }

    private fun onOrder(next: List<CarTask>) {
        order.value = next
        carUi.railOrder.save(next.map { it.taskId })
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
            RailWindow(context, display, rail, carUi, order.asStateFlow()) {
                shownCount.value = it
            }
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
