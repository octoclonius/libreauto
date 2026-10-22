package io.github.octoclonius.libreauto.session.audio

import android.content.Context
import android.os.ParcelFileDescriptor
import android.util.Log
import io.github.octoclonius.libreauto.privileged.AudioFocusHolder
import io.github.octoclonius.libreauto.privileged.AudioOutput
import io.github.octoclonius.libreauto.privileged.AudioPlayer
import io.github.octoclonius.libreauto.protocol.ChannelMessage
import io.github.octoclonius.libreauto.protocol.control.MessageType
import io.github.octoclonius.libreauto.protocol.control.audioFocusRequest
import io.github.octoclonius.libreauto.protocol.control.parseAudioFocusNotification
import io.github.octoclonius.libreauto.session.ChannelSender
import java.io.Closeable
import java.util.concurrent.Executors

internal class CarAudio(context: Context, private val control: ChannelSender) : Closeable {

    companion object {
        private const val TAG = "CarAudio"
    }

    private val executor = Executors.newSingleThreadExecutor { Thread(it, "car-audio") }
    private val negotiation = FocusNegotiation()
    private val androidFocus = AndroidAudioFocus(context)
    private var channels: List<AudioChannel> = emptyList()
    private var privileged: PrivilegedAudio? = null

    fun start(channels: List<AudioChannel>) {
        executor.execute { this.channels = channels }
    }

    fun onChannelReady() {
        executor.execute(::divertWhenReady)
    }

    fun onStreamStartedOrStopped() {
        executor.execute { act(negotiation.onIdle(isIdle())) }
    }

    fun onFocusNotification(message: ChannelMessage) {
        val notification = parseAudioFocusNotification(message.body)
        val state = notification.state ?: return
        val isUnsolicited = notification.unsolicited == true
        executor.execute {
            Log.i(TAG, "Car audio focus $state, unsolicited $isUnsolicited")
            act(negotiation.onCarFocus(state, isUnsolicited))
            for (channel in channels) {
                channel.ready?.stream?.setAllowed(
                    isStreamAllowed(channel.audioType, negotiation.carState)
                )
            }
            act(negotiation.onIdle(isIdle()))
        }
    }

    override fun close() {
        executor
            .submit {
                privileged?.close()
                privileged = null
                channels.forEach(AudioChannel::close)
                androidFocus.abandon()
            }
            .get()
        executor.shutdown()
    }

    private fun divertWhenReady() {
        if (privileged != null || channels.isEmpty()) return
        val ready = channels.map { it.ready ?: return }
        val outputs =
            channels.zip(ready) { channel, state ->
                AudioOutput(channel.usages, state.config.sampleRate, state.config.channelCount)
            }
        privileged =
            PrivilegedAudio(outputs, ::onPipes, ::onActivity).also(PrivilegedAudio::start)
    }

    private fun onPipes(pipes: List<ParcelFileDescriptor>) {
        executor.execute {
            for ((channel, pipe) in channels.zip(pipes)) channel.ready?.stream?.read(pipe)
        }
    }

    private fun onActivity(players: List<AudioPlayer>, focusHolders: List<AudioFocusHolder>) {
        executor.execute {
            val divertedPlayers = players.filter { isDiverted(it.usage) }
            for (channel in channels) {
                channel.ready?.stream?.setActive(
                    divertedPlayers.any { it.usage in channel.usages }
                )
            }
            act(negotiation.onIdle(isIdle()))
            act(
                negotiation.onActivity(
                    divertedPlayers,
                    focusHolders.filter { isDiverted(it.usage) },
                )
            )
        }
    }

    private fun isDiverted(usage: Int): Boolean = channels.any { usage in it.usages }

    private fun isIdle(): Boolean = channels.all { it.ready?.stream?.isIdle != false }

    private fun act(actions: List<FocusNegotiation.Action>) {
        for (action in actions) {
            when (action) {
                is FocusNegotiation.Action.RequestCarFocus -> {
                    Log.i(TAG, "Requesting car audio focus ${action.type}")
                    control.send(MessageType.AUDIO_FOCUS_REQUEST, audioFocusRequest(action.type))
                }
                is FocusNegotiation.Action.RequestAndroidFocus -> androidFocus.request(action.gain)
                FocusNegotiation.Action.AbandonAndroidFocus -> androidFocus.abandon()
            }
        }
    }
}
