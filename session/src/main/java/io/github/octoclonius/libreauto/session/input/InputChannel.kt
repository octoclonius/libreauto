package io.github.octoclonius.libreauto.session.input

import android.util.Log
import io.github.octoclonius.libreauto.protocol.ChannelMessage
import io.github.octoclonius.libreauto.protocol.MessageStatus
import io.github.octoclonius.libreauto.protocol.control.MessageType
import io.github.octoclonius.libreauto.protocol.input.InputMessageType
import io.github.octoclonius.libreauto.protocol.input.inputBindingRequest
import io.github.octoclonius.libreauto.protocol.input.parseInputBindingResponse
import io.github.octoclonius.libreauto.protocol.input.parseInputReport
import io.github.octoclonius.libreauto.protocol.media.VideoFormat
import io.github.octoclonius.libreauto.session.ChannelSender
import io.github.octoclonius.libreauto.session.isChannelOpened

class InputChannel(
    private val sender: ChannelSender,
    keycodes: List<Int>,
    private val videoFormat: () -> VideoFormat?,
) {

    companion object {
        private const val TAG = "InputChannel"
    }

    private var state: InputChannelState? = InputChannelState.Opening(keycodes)

    val channel: Int
        get() = sender.channel

    fun open() {
        Log.i(TAG, "Opening the input channel $channel")
        sender.sendOpenRequest()
    }

    fun onMessage(message: ChannelMessage): Boolean =
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
                val event = parseInputReport(message.body).touch ?: return false
                val touch = touch(event)
                val format = videoFormat()
                when {
                    touch == null -> Log.w(TAG, "Dropped touch $event")
                    format == null -> Log.w(TAG, "Dropped touch $touch before the video started")
                    !CarTouches.emit(touch.onDisplay(format)) -> Log.w(TAG, "Lost touch $touch")
                }
            }
            else -> return false
        }
        return true
    }
}

private sealed interface InputChannelState {

    data class Opening(val keycodes: List<Int>) : InputChannelState

    data object Open : InputChannelState
}
