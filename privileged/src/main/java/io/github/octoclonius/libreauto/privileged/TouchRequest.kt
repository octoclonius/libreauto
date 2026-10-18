package io.github.octoclonius.libreauto.privileged

import android.view.MotionEvent

internal const val MAX_POINTER_ID = 31

private val TOUCH_ACTIONS =
    setOf(
        MotionEvent.ACTION_DOWN,
        MotionEvent.ACTION_UP,
        MotionEvent.ACTION_MOVE,
        MotionEvent.ACTION_POINTER_DOWN,
        MotionEvent.ACTION_POINTER_UP,
    )

internal fun touchRequestError(
    action: Int,
    actionIndex: Int,
    pointerIds: IntArray,
    xs: IntArray,
    ys: IntArray,
): String? =
    when {
        action !in TOUCH_ACTIONS -> "The touch action $action isn't known"
        pointerIds.isEmpty() -> "The touch has no pointers"
        xs.size != pointerIds.size || ys.size != pointerIds.size ->
            "The touch has ${pointerIds.size} pointer ids for ${xs.size} x and ${ys.size} y " +
                "positions"
        actionIndex !in pointerIds.indices ->
            "The touch's action index $actionIndex isn't one of its ${pointerIds.size} pointers"
        pointerIds.any { it !in 0..MAX_POINTER_ID } ->
            "The touch's pointer ids ${pointerIds.toList()} aren't all in 0..$MAX_POINTER_ID"
        pointerIds.distinct().size != pointerIds.size ->
            "The touch uses a pointer id twice in ${pointerIds.toList()}"
        else -> null
    }
