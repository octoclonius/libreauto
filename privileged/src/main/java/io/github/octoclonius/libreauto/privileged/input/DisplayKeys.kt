package io.github.octoclonius.libreauto.privileged.input

import android.content.Context
import android.hardware.input.InputManager
import android.os.SystemClock
import android.view.InputEvent
import android.view.KeyEvent

internal class DisplayKeys(context: Context) {

    private val inputManager = context.getSystemService(InputManager::class.java)
    private val setDisplayId =
        InputEvent::class.java.getMethod("setDisplayId", Int::class.javaPrimitiveType)
    private val injectInputEvent =
        InputManager::class
            .java
            .getMethod("injectInputEvent", InputEvent::class.java, Int::class.javaPrimitiveType)
    private val asyncMode =
        InputManager::class.java.getField("INJECT_INPUT_EVENT_MODE_ASYNC").getInt(null)

    fun press(displayId: Int, keyCode: Int) {
        val downTime = SystemClock.uptimeMillis()
        inject(displayId, KeyEvent(downTime, downTime, KeyEvent.ACTION_DOWN, keyCode, 0))
        val upTime = SystemClock.uptimeMillis()
        inject(displayId, KeyEvent(downTime, upTime, KeyEvent.ACTION_UP, keyCode, 0))
    }

    private fun inject(displayId: Int, event: KeyEvent) {
        setDisplayId.invoke(event, displayId)
        injectInputEvent.invoke(inputManager, event, asyncMode)
    }
}
