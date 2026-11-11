package io.github.octoclonius.libreauto.projection.input

import android.util.Log
import io.github.octoclonius.libreauto.projection.channel.CarChannel
import io.github.octoclonius.libreauto.projection.channel.ChannelSender
import io.github.octoclonius.libreauto.projection.channel.isChannelOpened
import io.github.octoclonius.libreauto.projection.display.CarDisplays
import io.github.octoclonius.libreauto.projection.display.CarLayout
import io.github.octoclonius.libreauto.protocol.ChannelMessage
import io.github.octoclonius.libreauto.protocol.MessageStatus
import io.github.octoclonius.libreauto.protocol.control.MessageType
import io.github.octoclonius.libreauto.protocol.input.InputMessageType
import io.github.octoclonius.libreauto.protocol.input.InputReport
import io.github.octoclonius.libreauto.protocol.input.TouchAction
import io.github.octoclonius.libreauto.protocol.input.buttonPresses
import io.github.octoclonius.libreauto.protocol.input.inputBindingRequest
import io.github.octoclonius.libreauto.protocol.input.parseInputBindingResponse
import io.github.octoclonius.libreauto.protocol.input.parseInputReport

internal class InputChannel(
    private val sender: ChannelSender,
    keycodes: List<Int>,
    private val layout: () -> CarLayout?,
    private val buttons: CarButtons,
    private val displays: CarDisplays,
) : CarChannel {

    companion object {
        private const val TAG = "InputChannel"
    }

    private var state: InputChannelState? = InputChannelState.Opening(keycodes)
    private val router = TouchRouter()
    private val presses = ButtonPresses()
    private val railTouches = WindowTouches("rail", displays.rail::onTouch)
    private val overlayTouches =
        displays.overlays.associateWith { WindowTouches(it.name, it::onTouch) }
    private var appsGesture: AppsGesture = AppsGesture.App
    private var isRailGestureCaptured = false

    override val channel: Int
        get() = sender.channel

    override fun open() {
        Log.i(TAG, "Opening the input channel $channel")
        sender.sendOpenRequest()
    }

    override fun onMessage(message: ChannelMessage): Boolean =
        when (val current = state) {
            is InputChannelState.Opening ->
                message.type == MessageType.CHANNEL_OPEN_RESPONSE &&
                    onOpenResponse(current, message)
            InputChannelState.Open -> onOpenMessage(message)
            null -> false
        }

    private fun onOpenResponse(
        current: InputChannelState.Opening,
        message: ChannelMessage,
    ): Boolean {
        state =
            if (isChannelOpened(TAG, message, "input")) {
                Log.i(TAG, "Binding the keys ${current.keycodes}")
                sender.send(InputMessageType.BINDING_REQUEST, inputBindingRequest(current.keycodes))
                InputChannelState.Open
            } else {
                null
            }
        return true
    }

    private fun onOpenMessage(message: ChannelMessage): Boolean {
        when (message.type) {
            InputMessageType.BINDING_RESPONSE -> {
                val status = parseInputBindingResponse(message.body)
                if (status == MessageStatus.SUCCESS) {
                    Log.i(TAG, "The car bound the keys")
                } else {
                    Log.w(TAG, "The car refused the key binding: status $status")
                }
            }
            InputMessageType.REPORT -> {
                val report = parseInputReport(message.body)
                val buttonPresses = buttonPresses(report)
                if (report.touch == null && buttonPresses.isEmpty()) return false
                buttonPresses
                    .filter { presses.isNewPress(it.code, it.isPressed) }
                    .forEach { buttons.onPress(it.code) }
                if (report.touch != null) onTouch(report, message.receivedNanos)
            }
            else -> return false
        }
        return true
    }

    private fun onTouch(report: InputReport, receivedNanos: Long) {
        val touch = touch(report)
        val layout = layout()
        when {
            touch == null -> Log.w(TAG, "Dropped touch report $report")
            layout == null -> Log.w(TAG, "Dropped touch $touch before the video started")
            else -> router.route(touch, layout)?.let { send(receivedNanos, it) }
        }
    }

    private fun send(eventTimeNanos: Long, routed: RoutedTouch) {
        when (routed.target) {
            TouchTarget.Apps -> {
                if (routed.touch.action == TouchAction.DOWN) {
                    appsGesture = appsGesture(routed.touch.pointers[routed.touch.actionIndex])
                }
                when (val gesture = appsGesture) {
                    AppsGesture.App -> sendToApps(eventTimeNanos, routed.touch)
                    is AppsGesture.Overlay -> gesture.touches.send(eventTimeNanos, routed.touch)
                    AppsGesture.Captured -> {}
                }
            }
            TouchTarget.Rail -> {
                if (routed.touch.action == TouchAction.DOWN) {
                    isRailGestureCaptured = displays.overlays.any { it.outsideTouches.capture() }
                }
                if (!isRailGestureCaptured) railTouches.send(eventTimeNanos, routed.touch)
            }
        }
    }

    private fun appsGesture(pointer: TouchPointer): AppsGesture {
        if (displays.rail.outsideTouches.capture()) return AppsGesture.Captured
        val overlay =
            displays.overlays.lastOrNull { it.takesTouchAt(pointer.x, pointer.y) }
                ?: return AppsGesture.App
        return AppsGesture.Overlay(overlayTouches.getValue(overlay))
    }

    private fun sendToApps(eventTimeNanos: Long, touch: Touch) {
        displays.car.call("take a touch") {
            it.sendTouch(
                eventTimeNanos,
                touch.action,
                touch.actionIndex,
                IntArray(touch.pointers.size) { index -> touch.pointers[index].id },
                IntArray(touch.pointers.size) { index -> touch.pointers[index].x },
                IntArray(touch.pointers.size) { index -> touch.pointers[index].y },
            )
        }
    }
}

private sealed interface AppsGesture {
    data object App : AppsGesture

    class Overlay(val touches: WindowTouches) : AppsGesture

    data object Captured : AppsGesture
}

private sealed interface InputChannelState {

    data class Opening(val keycodes: List<Int>) : InputChannelState

    data object Open : InputChannelState
}
