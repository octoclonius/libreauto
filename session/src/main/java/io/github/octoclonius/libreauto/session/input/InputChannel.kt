package io.github.octoclonius.libreauto.session.input

import android.util.Log
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
import io.github.octoclonius.libreauto.session.CarChannel
import io.github.octoclonius.libreauto.session.ChannelSender
import io.github.octoclonius.libreauto.session.isChannelOpened
import io.github.octoclonius.libreauto.session.video.CarLayout
import io.github.octoclonius.libreauto.session.video.OverlayDisplay
import io.github.octoclonius.libreauto.session.video.RailDisplay

internal class InputChannel(
    private val sender: ChannelSender,
    keycodes: List<Int>,
    private val layout: () -> CarLayout?,
    private val buttons: CarButtons,
) : CarChannel {

    companion object {
        private const val TAG = "InputChannel"
    }

    private var state: InputChannelState? = InputChannelState.Opening(keycodes)
    private val router = TouchRouter()
    private val presses = ButtonPresses()
    private val railTouches = WindowTouches("rail", RailDisplay::onTouch)
    private val overlayTouches = WindowTouches("overlay", OverlayDisplay::onTouch)
    private var appsGesture = AppsGesture.App

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
                    appsGesture =
                        when {
                            RailDisplay.captureOutsideTouch() -> AppsGesture.Captured
                            OverlayDisplay.isShown -> AppsGesture.Overlay
                            else -> AppsGesture.App
                        }
                }
                when (appsGesture) {
                    AppsGesture.App -> PrivilegedTouches.send(eventTimeNanos, routed.touch)
                    AppsGesture.Overlay -> overlayTouches.send(eventTimeNanos, routed.touch)
                    AppsGesture.Captured -> {}
                }
            }
            TouchTarget.Rail -> railTouches.send(eventTimeNanos, routed.touch)
        }
    }
}

private enum class AppsGesture {
    App,
    Overlay,
    Captured,
}

private sealed interface InputChannelState {

    data class Opening(val keycodes: List<Int>) : InputChannelState

    data object Open : InputChannelState
}
