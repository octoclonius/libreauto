package io.github.octoclonius.libreauto.session.input

import android.util.Log
import android.view.InputDevice
import android.view.MotionEvent
import io.github.octoclonius.libreauto.protocol.input.TouchAction
import java.util.concurrent.TimeUnit

internal class WindowTouches(
    private val window: String,
    private val deliver: (MotionEvent) -> Boolean,
) {

    companion object {
        private const val TAG = "WindowTouches"
    }

    private var downTimeMillis = 0L

    fun send(eventTimeNanos: Long, touch: Touch) {
        val eventTimeMillis = TimeUnit.NANOSECONDS.toMillis(eventTimeNanos)
        if (touch.action == TouchAction.DOWN) downTimeMillis = eventTimeMillis
        val event =
            MotionEvent.obtain(
                downTimeMillis,
                eventTimeMillis,
                motionAction(touch),
                touch.pointers.size,
                Array(touch.pointers.size) { index ->
                    MotionEvent.PointerProperties().apply {
                        id = touch.pointers[index].id
                        toolType = MotionEvent.TOOL_TYPE_FINGER
                    }
                },
                Array(touch.pointers.size) { index ->
                    MotionEvent.PointerCoords().apply {
                        x = touch.pointers[index].x.toFloat()
                        y = touch.pointers[index].y.toFloat()
                        pressure = 1f
                        size = 1f
                    }
                },
                0,
                0,
                1f,
                1f,
                0,
                0,
                InputDevice.SOURCE_TOUCHSCREEN,
                0,
            )
        if (!deliver(event)) Log.w(TAG, "Dropped $window touch $touch")
    }

    private fun motionAction(touch: Touch): Int {
        val index = touch.actionIndex shl MotionEvent.ACTION_POINTER_INDEX_SHIFT
        return when (touch.action) {
            TouchAction.UP -> MotionEvent.ACTION_UP
            TouchAction.MOVE -> MotionEvent.ACTION_MOVE
            TouchAction.POINTER_DOWN -> MotionEvent.ACTION_POINTER_DOWN or index
            TouchAction.POINTER_UP -> MotionEvent.ACTION_POINTER_UP or index
            else -> MotionEvent.ACTION_DOWN
        }
    }
}
