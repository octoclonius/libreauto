package io.github.octoclonius.libreauto.privileged

import android.view.MotionEvent

private val TOUCH_ACTIONS =
    setOf(
        MotionEvent.ACTION_DOWN,
        MotionEvent.ACTION_UP,
        MotionEvent.ACTION_MOVE,
        MotionEvent.ACTION_POINTER_DOWN,
        MotionEvent.ACTION_POINTER_UP,
    )

internal fun touchRequestError(
    eventTimeNanos: Long,
    action: Int,
    actionIndex: Int,
    pointerIds: IntArray,
    xs: IntArray,
    ys: IntArray,
): String? =
    when {
        eventTimeNanos < 0 -> "The touch's event time $eventTimeNanos is negative"
        action !in TOUCH_ACTIONS -> "The touch action $action isn't known"
        pointerIds.isEmpty() -> "The touch has no pointers"
        xs.size != pointerIds.size || ys.size != pointerIds.size ->
            "The touch has ${pointerIds.size} pointer ids for ${xs.size} x and ${ys.size} y " +
                "positions"
        actionIndex !in pointerIds.indices ->
            "The touch's action index $actionIndex isn't one of its ${pointerIds.size} pointers"
        pointerIds.any { it !in 0 until TOUCHSCREEN_POINTERS } ->
            "The touch's pointer ids ${pointerIds.toList()} aren't all in " +
                "0..${TOUCHSCREEN_POINTERS - 1}"
        else -> null
    }
