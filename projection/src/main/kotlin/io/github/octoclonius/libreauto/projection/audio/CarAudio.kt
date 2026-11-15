package io.github.octoclonius.libreauto.projection.audio

import android.content.Context
import android.os.ParcelFileDescriptor
import android.os.Process
import android.util.Log
import io.github.octoclonius.libreauto.privileged.api.AudioFocusHolder
import io.github.octoclonius.libreauto.privileged.api.AudioInput
import io.github.octoclonius.libreauto.privileged.api.AudioOutput
import io.github.octoclonius.libreauto.privileged.api.AudioPlayer
import io.github.octoclonius.libreauto.projection.calls.CallRepository
import io.github.octoclonius.libreauto.projection.channel.ChannelSender
import io.github.octoclonius.libreauto.projection.closeAll
import io.github.octoclonius.libreauto.projection.media.MediaResumption
import io.github.octoclonius.libreauto.projection.privileged.PrivilegedServiceConnection
import io.github.octoclonius.libreauto.protocol.ChannelMessage
import io.github.octoclonius.libreauto.protocol.control.MessageType
import io.github.octoclonius.libreauto.protocol.control.audioFocusRequest
import io.github.octoclonius.libreauto.protocol.control.parseAudioFocusNotification
import io.github.octoclonius.libreauto.protocol.media.AudioType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal class CarAudio(
    private val context: Context,
    private val control: ChannelSender,
    private val calls: CallRepository,
    private val privileged: PrivilegedServiceConnection,
    private val resumption: MediaResumption,
    private val scope: CoroutineScope,
) {

    companion object {
        private const val TAG = "CarAudio"
        private const val BECOMING_NOISY_DELAY_MS = 1_000L
    }

    private val negotiation = FocusNegotiation()
    private val androidFocus = AndroidAudioFocus(context)
    private var callState: Job? = null
    private var channels: List<AudioChannel> = emptyList()
    private var streaming: List<AudioChannel> = emptyList()
    private var microphone: MicrophoneChannel? = null
    private var privilegedAudio: PrivilegedAudio? = null
    private var mediaResumption: MediaResumption? = null
    private var isMediaResumed = false
    private var isMediaPlaying = false
    private var isClosing = false
    private var isClosed = false

    fun start(channels: List<AudioChannel>, microphone: MicrophoneChannel?) {
        this.channels = channels
        this.microphone = microphone
        callState = scope.launch { calls.isInCall.collect(::onCall) }
    }

    fun onChannelSettled() {
        divertWhenSettled()
    }

    fun onStreamStartedOrStopped() {
        post { act(negotiation.onIdle(isIdle())) }
    }

    private fun onCall(isInCall: Boolean) {
        Log.i(TAG, if (isInCall) "In a call" else "Not in a call")
        act(negotiation.onCall(isInCall))
    }

    fun onFocusNotification(message: ChannelMessage) {
        val notification = parseAudioFocusNotification(message.body)
        val state = notification.state ?: return
        val isUnsolicited = notification.unsolicited == true
        Log.i(TAG, "Car audio focus $state, unsolicited $isUnsolicited")
        act(negotiation.onCarFocus(state, isUnsolicited))
        for (channel in streaming) {
            channel.ready
                ?.stream
                ?.setAllowed(isStreamAllowed(channel.audioType, negotiation.carState))
        }
        act(negotiation.onIdle(isIdle()))
    }

    suspend fun close() {
        if (isClosing) return
        callState?.cancel()
        if (pauseMedia()) delay(BECOMING_NOISY_DELAY_MS)
        isClosed = true
        closeAll(TAG) {
            step("Stopping the diverted audio") { privilegedAudio?.close() }
            privilegedAudio = null
            for (channel in channels) {
                step("Closing the audio channel ${channel.channel}") { channel.close() }
            }
            step("Closing the microphone") { microphone?.close() }
            step("Abandoning Android audio focus") { androidFocus.abandon() }
        }
    }

    private fun post(block: () -> Unit) {
        scope.launch { if (!isClosed) block() }
    }

    private fun divertWhenSettled() {
        if (privilegedAudio != null || !channels.all(AudioChannel::isSettled)) return
        if (microphone?.isSettled == false) return
        streaming = channels.filter { it.ready != null }
        if (streaming.isEmpty()) {
            Log.w(TAG, "No audio channel of the car is usable; audio stays on the phone")
        }
        val input =
            microphone
                ?.takeIf { it.isReady }
                ?.let { AudioInput(it.config.sampleRate, it.config.channelCount) }
        if (input == null) Log.i(TAG, "Recordings keep the phone's microphone")
        if (streaming.isEmpty() && input == null) return
        val outputs = streaming.map { channel ->
            val config = checkNotNull(channel.ready).config
            AudioOutput(channel.usages, config.sampleRate, config.channelCount)
        }
        if (streaming.any { it.audioType == AudioType.MEDIA }) {
            mediaResumption = resumption
        }
        privilegedAudio =
            PrivilegedAudio(
                    privileged,
                    scope,
                    outputs,
                    input,
                    ::onPipes,
                    ::onActivity,
                    ::onMicrophoneActivity,
                )
                .also(PrivilegedAudio::start)
    }

    private fun pauseMedia(): Boolean {
        isClosing = true
        val resumption = mediaResumption ?: return false
        if (!isMediaPlaying) {
            resumption.forget()
            return false
        }
        resumption.pause()
        return true
    }

    private fun onPipes(pipes: List<ParcelFileDescriptor>) {
        post {
            for ((channel, pipe) in streaming.zip(pipes)) channel.ready?.stream?.read(pipe)
            pipes.getOrNull(streaming.size)?.let { microphone?.inject(it) }
            val resumption = mediaResumption
            if (resumption != null && !isMediaResumed) {
                isMediaResumed = true
                resumption.resume()
            }
        }
    }

    private fun onMicrophoneActivity(isRecording: Boolean) {
        post { microphone?.setRecording(isRecording) }
    }

    private fun onActivity(players: List<AudioPlayer>, focusHolders: List<AudioFocusHolder>) {
        post {
            val divertedPlayers = players.filter { isDiverted(it.usage) }
            isMediaPlaying = isMediaPlaying(divertedPlayers)
            for (channel in streaming) {
                channel.ready?.stream?.setActive(divertedPlayers.any { it.usage in channel.usages })
            }
            act(negotiation.onIdle(isIdle()))
            act(
                negotiation.onActivity(
                    divertedPlayers,
                    focusHolders.filter { isCountedFocus(it, Process.myUid(), ::isDiverted) },
                )
            )
        }
    }

    private fun isDiverted(usage: Int): Boolean = streaming.any { usage in it.usages }

    private fun isIdle(): Boolean = streaming.all { it.ready?.stream?.isIdle != false }

    private fun act(actions: List<FocusNegotiation.Action>) {
        if (isClosing) return
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
