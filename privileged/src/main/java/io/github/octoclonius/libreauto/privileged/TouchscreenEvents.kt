package io.github.octoclonius.libreauto.privileged

import android.view.MotionEvent

internal const val TOUCHSCREEN_POINTERS = 16

internal data class TouchPointer(val id: Int, val x: Int, val y: Int)

internal data class TouchscreenEvent(val pointerId: Int, val action: Int, val x: Int, val y: Int)

internal fun touchscreenEvents(
    action: Int,
    actionIndex: Int,
    pointers: List<TouchPointer>,
): List<TouchscreenEvent> =
    pointers.mapIndexed { index, pointer ->
        val pointerAction =
            if (index == actionIndex) fingerAction(action) else MotionEvent.ACTION_MOVE
        TouchscreenEvent(pointer.id, pointerAction, pointer.x, pointer.y)
    }

private fun fingerAction(action: Int): Int =
    when (action) {
        MotionEvent.ACTION_POINTER_DOWN -> MotionEvent.ACTION_DOWN
        MotionEvent.ACTION_POINTER_UP -> MotionEvent.ACTION_UP
        else -> action
    }
