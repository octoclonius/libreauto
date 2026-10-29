package io.github.octoclonius.libreauto.projection.audio

import android.util.Log
import io.github.octoclonius.libreauto.projection.channel.CarChannel
import io.github.octoclonius.libreauto.projection.channel.ChannelSender
import io.github.octoclonius.libreauto.projection.channel.isChannelOpened
import io.github.octoclonius.libreauto.protocol.ChannelMessage
import io.github.octoclonius.libreauto.protocol.control.MessageType
import io.github.octoclonius.libreauto.protocol.media.MediaMessageType
import io.github.octoclonius.libreauto.protocol.media.PcmConfig
import io.github.octoclonius.libreauto.protocol.media.audioSetupRequest
import io.github.octoclonius.libreauto.protocol.media.parseMediaAck
import io.github.octoclonius.libreauto.protocol.media.parseMediaSetupResponse
import java.net.ProtocolException

internal class AudioChannel(
    val audioType: Int,
    private val sender: ChannelSender,
    private val configs: List<PcmConfig>,
    private val onSettled: () -> Unit,
    private val onStreamStartedOrStopped: () -> Unit,
) : CarChannel {

    companion object {
        private const val TAG = "AudioChannel"
        private const val SETUP_STATUS_WAIT = 1
        private const val SETUP_STATUS_READY = 2
    }

    private var state: AudioChannelState? = AudioChannelState.Opening

    val usages: IntArray = checkNotNull(divertedUsages(audioType)) { "No usages for $audioType" }

    private val frameSamples =
        checkNotNull(audioFrameSamples(audioType)) { "No frame size for $audioType" }

    override val channel: Int
        get() = sender.channel

    val ready: AudioChannelState.Ready?
        get() = state as? AudioChannelState.Ready

    val isSettled: Boolean
        get() = state == null || state is AudioChannelState.Ready

    override fun open() {
        Log.i(TAG, "Opening the audio channel $channel for audio type $audioType")
        sender.sendOpenRequest()
    }

    override fun onMessage(message: ChannelMessage): Boolean {
        val current = state
        when {
            current == AudioChannelState.Opening &&
                message.type == MessageType.CHANNEL_OPEN_RESPONSE ->
                if (isChannelOpened(TAG, message, "audio")) {
                    sender.send(MediaMessageType.SETUP_REQUEST, audioSetupRequest())
                    state = AudioChannelState.SettingUp
                } else {
                    settleUnusable()
                }
            current == AudioChannelState.SettingUp &&
                message.type == MediaMessageType.SETUP_RESPONSE -> onSetupResponse(message)
            current is AudioChannelState.Ready && message.type == MediaMessageType.ACK -> {
                val ack = parseMediaAck(message.body)
                current.stream.onAck(ack.session, ack.count)
            }
            else -> return false
        }
        return true
    }

    override fun close() {
        (state as? AudioChannelState.Ready)?.stream?.close()
        state = null
    }

    private fun onSetupResponse(message: ChannelMessage) {
        val response = parseMediaSetupResponse(message.body)
        Log.i(
            TAG,
            "The car set up the audio channel $channel: status ${response.status}, " +
                "up to ${response.max_unacked} unacked frames, configs ${response.config_indexes}",
        )
        if (response.status == SETUP_STATUS_WAIT) return
        if (response.status != SETUP_STATUS_READY) {
            throw ProtocolException(
                "The car refused to set up the audio channel $channel: status ${response.status}"
            )
        }
        val maxUnacked = response.max_unacked
        if (maxUnacked == null || maxUnacked <= 0) {
            throw ProtocolException(
                "The car allows $maxUnacked unacked frames on the audio channel $channel"
            )
        }
        val config =
            response.config_indexes.firstNotNullOfOrNull { index ->
                configs.firstOrNull { it.index == index }
            }
        if (config == null) {
            Log.w(
                TAG,
                "The car accepted none of the audio configs LibreAuto plays on channel $channel: " +
                    "accepted ${response.config_indexes}, playable ${configs.map { it.index }}",
            )
            settleUnusable()
            return
        }
        state =
            AudioChannelState.Ready(
                config,
                AudioStream(
                    config,
                    frameSamples,
                    maxUnacked,
                    sender::send,
                    onStreamStartedOrStopped,
                ),
            )
        onSettled()
    }

    private fun settleUnusable() {
        state = null
        onSettled()
    }
}

internal sealed interface AudioChannelState {

    data object Opening : AudioChannelState

    data object SettingUp : AudioChannelState

    data class Ready(val config: PcmConfig, val stream: AudioStream) : AudioChannelState
}
