package io.github.octoclonius.libreauto.session.input

import io.github.octoclonius.libreauto.protocol.input.InputReport
import io.github.octoclonius.libreauto.protocol.input.TouchAction
import io.github.octoclonius.libreauto.protocol.media.VideoFormat
import io.github.octoclonius.libreauto.session.video.Area

internal const val TOUCHSCREEN_POINTERS = 16

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

internal fun touch(report: InputReport): Touch? {
    val event = report.touch
    if (report.timestamp == null || event == null) return null
    val pointers =
        event.pointers.map {
            TouchPointer(it.id ?: return null, it.x ?: return null, it.y ?: return null)
        }
    val actionIndex = event.action_index ?: 0
    if (actionIndex !in pointers.indices) return null
    if (pointers.any { it.id !in 0 until TOUCHSCREEN_POINTERS }) return null
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

internal fun Touch.relativeTo(area: Area): Touch =
    copy(pointers = pointers.map { it.copy(x = it.x - area.left, y = it.y - area.top) })
