package io.github.octoclonius.libreauto.session.input

import io.github.octoclonius.libreauto.protocol.input.TouchAction
import io.github.octoclonius.libreauto.session.video.CarLayout

internal enum class TouchTarget {
    Apps,
    Rail,
}

internal data class RoutedTouch(val target: TouchTarget, val touch: Touch)

internal class TouchRouter {

    private var target: TouchTarget? = null
    private var isInGesture = false

    fun route(touch: Touch, layout: CarLayout): RoutedTouch? {
        val onDisplay = touch.onDisplay(layout.format)
        if (touch.action == TouchAction.DOWN || !isInGesture) target = targetAt(onDisplay, layout)
        isInGesture = touch.action != TouchAction.UP
        val routed = target ?: return null
        val area = if (routed == TouchTarget.Rail) layout.rail else layout.apps
        return RoutedTouch(routed, onDisplay.relativeTo(area))
    }

    private fun targetAt(touch: Touch, layout: CarLayout): TouchTarget? {
        val pointer = touch.pointers[touch.actionIndex]
        return when {
            layout.rail.contains(pointer.x, pointer.y) -> TouchTarget.Rail
            layout.apps.contains(pointer.x, pointer.y) -> TouchTarget.Apps
            else -> null
        }
    }
}
