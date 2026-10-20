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

    fun route(touch: Touch, layout: CarLayout): RoutedTouch {
        val onDisplay = touch.onDisplay(layout.format)
        val routed =
            target.takeUnless { touch.action == TouchAction.DOWN } ?: targetAt(onDisplay, layout)
        target = routed.takeUnless { touch.action == TouchAction.UP }
        val area = if (routed == TouchTarget.Rail) layout.rail else layout.apps
        return RoutedTouch(routed, onDisplay.relativeTo(area))
    }

    private fun targetAt(touch: Touch, layout: CarLayout): TouchTarget {
        val pointer = touch.pointers[touch.actionIndex]
        return if (layout.rail.contains(pointer.x, pointer.y)) {
            TouchTarget.Rail
        } else {
            TouchTarget.Apps
        }
    }
}
