package io.github.octoclonius.libreauto.car.rail

import android.content.Context
import android.hardware.display.DisplayManager
import android.util.Log
import android.view.WindowManager
import io.github.octoclonius.libreauto.projection.CarSession
import io.github.octoclonius.libreauto.projection.calls.PhoneCall
import io.github.octoclonius.libreauto.projection.display.RailDisplayState
import io.github.octoclonius.libreauto.projection.tasks.CarTask
import java.io.Closeable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

internal class RailHost(
    context: Context,
    private val session: CarSession,
    private val state: RailState,
    private val railOrder: RailOrderRepository,
    private val actions: RailActions,
    private val calls: StateFlow<List<PhoneCall>>,
) : Closeable {

    companion object {
        private const val TAG = "RailHost"
    }

    private val context = context.applicationContext
    private val shownCount = MutableStateFlow(MAX_RAIL_APPS)
    private val order = MutableStateFlow<List<CarTask>>(emptyList())
    private var window: RailWindow? = null

    fun start(scope: CoroutineScope) {
        val display = session.displays.rail
        val carTasks = session.tasks
        scope.launch { display.state.collect(::show) }
        scope.launch { display.touches.collect { window?.dispatchTouchEvent(it) } }
        scope.launch { display.outsideTouches.captured.collect { state.cancelClosing() } }
        scope.launch {
            state.closingTaskId.collect { display.outsideTouches.setCapturing(it != null) }
        }
        scope.launch { carTasks.state.collect(state::onTasks) }
        scope.launch {
            combine(carTasks.state, state.closedTaskIds, shownCount) { tasks, closed, shown ->
                    nextRailOrder(
                        order.value,
                        railOrder.taskIds.value,
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
        railOrder.save(next.map { it.taskId })
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
            RailWindow(
                context,
                display,
                rail,
                session,
                state,
                actions,
                order.asStateFlow(),
                calls,
            ) {
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
