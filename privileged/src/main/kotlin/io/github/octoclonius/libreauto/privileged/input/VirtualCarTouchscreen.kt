package io.github.octoclonius.libreauto.privileged.input

import android.hardware.input.VirtualTouchEvent
import android.hardware.input.VirtualTouchscreen
import android.hardware.input.VirtualTouchscreenConfig
import java.io.Closeable

internal class VirtualCarTouchscreen
private constructor(private val touchscreen: VirtualTouchscreen) : Closeable {

    companion object {
        private const val NAME = "LibreAuto touchscreen"
        private const val PRESSURE = 255f

        fun create(
            device: AutoCloseable,
            displayId: Int,
            width: Int,
            height: Int,
        ): VirtualCarTouchscreen {
            val config =
                VirtualTouchscreenConfig.Builder(width, height)
                    .setAssociatedDisplayId(displayId)
                    .setInputDeviceName(NAME)
                    .build()
            val touchscreen =
                device.javaClass
                    .getMethod("createVirtualTouchscreen", VirtualTouchscreenConfig::class.java)
                    .invoke(device, config) as VirtualTouchscreen
            return VirtualCarTouchscreen(touchscreen)
        }
    }

    fun onTouch(eventTimeNanos: Long, action: Int, actionIndex: Int, pointers: List<TouchPointer>) {
        for (event in touchscreenEvents(action, actionIndex, pointers)) send(event, eventTimeNanos)
    }

    override fun close() {
        touchscreen.close()
    }

    private fun send(event: TouchscreenEvent, eventTimeNanos: Long) {
        touchscreen.sendTouchEvent(
            VirtualTouchEvent.Builder()
                .setPointerId(event.pointerId)
                .setToolType(VirtualTouchEvent.TOOL_TYPE_FINGER)
                .setAction(event.action)
                .setX(event.x.toFloat())
                .setY(event.y.toFloat())
                .setPressure(PRESSURE)
                .setEventTimeNanos(eventTimeNanos)
                .build()
        )
    }
}
