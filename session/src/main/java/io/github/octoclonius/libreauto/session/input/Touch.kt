package io.github.octoclonius.libreauto.session.input

import io.github.octoclonius.libreauto.protocol.input.TouchAction
import io.github.octoclonius.libreauto.protocol.input.TouchEvent
import io.github.octoclonius.libreauto.protocol.media.VideoFormat

internal const val MAX_POINTER_ID = 31

private val KNOWN_ACTIONS =
    setOf(
        TouchAction.DOWN,
        TouchAction.UP,
        TouchAction.MOVE,
        TouchAction.POINTER_DOWN,
        TouchAction.POINTER_UP,
    )

internal data class Touch(val action: Int, val actionIndex: Int, val pointers: List<TouchPointer>)

internal data class TouchPointer(val id: Int, val x: Int, val y: Int)

internal fun touch(event: TouchEvent): Touch? {
    val pointers = event.pointers.map { TouchPointer(it.id ?: 0, it.x ?: 0, it.y ?: 0) }
    val actionIndex = event.action_index ?: 0
    if (actionIndex !in pointers.indices) return null
    if (pointers.any { it.id !in 0..MAX_POINTER_ID }) return null
    if (pointers.distinctBy { it.id }.size != pointers.size) return null
    val action = event.action?.takeIf { it in KNOWN_ACTIONS } ?: TouchAction.DOWN
    return Touch(action, actionIndex, pointers)
}

internal fun Touch.onDisplay(format: VideoFormat): Touch =
    copy(
        pointers =
            pointers.map {
                it.copy(x = format.displayX(it.x), y = format.displayY(it.y))
            }
    )
