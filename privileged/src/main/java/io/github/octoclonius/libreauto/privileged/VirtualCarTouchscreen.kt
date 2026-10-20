package io.github.octoclonius.libreauto.privileged

import android.view.MotionEvent
import java.io.Closeable
import java.lang.reflect.Method

internal class VirtualCarTouchscreen private constructor(private val touchscreen: Closeable) :
    Closeable {

    companion object {
        private const val NAME = "LibreAuto touchscreen"
        private const val PRESSURE = 255f
        private const val CONFIG = "android.hardware.input.VirtualTouchscreenConfig"
        private const val EVENT = "android.hardware.input.VirtualTouchEvent"
        private const val TOUCHSCREEN = "android.hardware.input.VirtualTouchscreen"

        fun create(
            device: AutoCloseable,
            displayId: Int,
            width: Int,
            height: Int,
        ): VirtualCarTouchscreen {
            val builder =
                Class.forName("$CONFIG\$Builder")
                    .getConstructor(Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                    .newInstance(width, height)
            builder.javaClass
                .getMethod("setAssociatedDisplayId", Int::class.javaPrimitiveType)
                .invoke(builder, displayId)
            builder.javaClass
                .getMethod("setInputDeviceName", String::class.java)
                .invoke(builder, NAME)
            val config = builder.javaClass.getMethod("build").invoke(builder)
            val touchscreen =
                device.javaClass
                    .getMethod("createVirtualTouchscreen", Class.forName(CONFIG))
                    .invoke(device, config) as Closeable
            return VirtualCarTouchscreen(touchscreen)
        }
    }

    private val eventBuilder = Class.forName("$EVENT\$Builder").getConstructor()
    private val setPointerId = setter("setPointerId", Int::class.javaPrimitiveType)
    private val setToolType = setter("setToolType", Int::class.javaPrimitiveType)
    private val setAction = setter("setAction", Int::class.javaPrimitiveType)
    private val setX = setter("setX", Float::class.javaPrimitiveType)
    private val setY = setter("setY", Float::class.javaPrimitiveType)
    private val setPressure = setter("setPressure", Float::class.javaPrimitiveType)
    private val setEventTimeNanos = setter("setEventTimeNanos", Long::class.javaPrimitiveType)
    private val build = eventBuilder.declaringClass.getMethod("build")
    private val sendTouchEvent =
        Class.forName(TOUCHSCREEN).getMethod("sendTouchEvent", Class.forName(EVENT))

    fun onTouch(eventTimeNanos: Long, action: Int, actionIndex: Int, pointers: List<TouchPointer>) {
        for (event in touchscreenEvents(action, actionIndex, pointers)) send(event, eventTimeNanos)
    }

    override fun close() {
        touchscreen.close()
    }

    private fun send(event: TouchscreenEvent, eventTimeNanos: Long) {
        val builder = eventBuilder.newInstance()
        setPointerId.invoke(builder, event.pointerId)
        setToolType.invoke(builder, MotionEvent.TOOL_TYPE_FINGER)
        setAction.invoke(builder, event.action)
        setX.invoke(builder, event.x.toFloat())
        setY.invoke(builder, event.y.toFloat())
        setPressure.invoke(builder, PRESSURE)
        setEventTimeNanos.invoke(builder, eventTimeNanos)
        sendTouchEvent.invoke(touchscreen, build.invoke(builder))
    }

    private fun setter(name: String, type: Class<*>?): Method =
        eventBuilder.declaringClass.getMethod(name, type)
}
