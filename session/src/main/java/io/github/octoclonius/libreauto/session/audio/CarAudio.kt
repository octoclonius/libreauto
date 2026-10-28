package io.github.octoclonius.libreauto.session.audio

import android.content.Context
import android.os.ParcelFileDescriptor
import android.os.Process
import android.util.Log
import io.github.octoclonius.libreauto.privileged.AudioFocusHolder
import io.github.octoclonius.libreauto.privileged.AudioInput
import io.github.octoclonius.libreauto.privileged.AudioOutput
import io.github.octoclonius.libreauto.privileged.AudioPlayer
import io.github.octoclonius.libreauto.protocol.ChannelMessage
import io.github.octoclonius.libreauto.protocol.control.MessageType
import io.github.octoclonius.libreauto.protocol.control.audioFocusRequest
import io.github.octoclonius.libreauto.protocol.control.parseAudioFocusNotification
import io.github.octoclonius.libreauto.protocol.media.AudioType
import io.github.octoclonius.libreauto.session.ChannelSender
import io.github.octoclonius.libreauto.session.calls.PhoneCalls
import io.github.octoclonius.libreauto.session.media.MediaResumption
import java.io.Closeable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

internal class CarAudio(private val context: Context, private val control: ChannelSender) :
    Closeable {

    companion object {
        private const val TAG = "CarAudio"
        private const val PAUSE_TIMEOUT_MILLIS = 1_000L
    }

    private val executor = Executors.newSingleThreadExecutor { Thread(it, "car-audio") }
    private val negotiation = FocusNegotiation()
    private val androidFocus = AndroidAudioFocus(context)
    private val calls = CoroutineScope(Dispatchers.Default)
    private var channels: List<AudioChannel> = emptyList()
    private var streaming: List<AudioChannel> = emptyList()
    private var microphone: MicrophoneChannel? = null
    private var privileged: PrivilegedAudio? = null
    private var mediaResumption: MediaResumption? = null
    private var isMediaResumed = false
    private var isMediaPlaying = false
    private var mediaPaused: CountDownLatch? = null
    private var isClosing = false

    fun start(channels: List<AudioChannel>, microphone: MicrophoneChannel?) {
        executor.execute {
            this.channels = channels
            this.microphone = microphone
        }
        calls.launch {
            PhoneCalls.state.map { it.isInCall }.distinctUntilChanged().collect(::onCall)
        }
    }

    fun onChannelSettled() {
        executor.execute(::divertWhenSettled)
    }

    fun onStreamStartedOrStopped() {
        executor.execute { act(negotiation.onIdle(isIdle())) }
    }

    private fun onCall(isInCall: Boolean) {
        executor.execute {
            Log.i(TAG, if (isInCall) "In a call" else "Not in a call")
            act(negotiation.onCall(isInCall))
        }
    }

    fun onFocusNotification(message: ChannelMessage) {
        val notification = parseAudioFocusNotification(message.body)
        val state = notification.state ?: return
        val isUnsolicited = notification.unsolicited == true
        executor.execute {
            Log.i(TAG, "Car audio focus $state, unsolicited $isUnsolicited")
            act(negotiation.onCarFocus(state, isUnsolicited))
            for (channel in streaming) {
                channel.ready?.stream?.setAllowed(
                    isStreamAllowed(channel.audioType, negotiation.carState)
                )
            }
            act(negotiation.onIdle(isIdle()))
        }
    }

    override fun close() {
        runBlocking { calls.coroutineContext.job.cancelAndJoin() }
        val paused = executor.submit(::pauseMedia).get()
        if (paused?.await(PAUSE_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS) == false) {
            Log.w(TAG, "Media still plays $PAUSE_TIMEOUT_MILLIS ms after pausing it")
        }
        executor
            .submit {
                privileged?.close()
                privileged = null
                channels.forEach(AudioChannel::close)
                microphone?.close()
                androidFocus.abandon()
            }
            .get()
        executor.shutdown()
    }

    private fun divertWhenSettled() {
        if (privileged != null || !channels.all(AudioChannel::isSettled)) return
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
        val outputs =
            streaming.map { channel ->
                val config = checkNotNull(channel.ready).config
                AudioOutput(channel.usages, config.sampleRate, config.channelCount)
            }
        if (streaming.any { it.audioType == AudioType.MEDIA }) {
            mediaResumption = MediaResumption(context)
        }
        privileged =
            PrivilegedAudio(outputs, input, ::onPipes, ::onActivity, ::onMicrophoneActivity)
                .also(PrivilegedAudio::start)
    }

    private fun pauseMedia(): CountDownLatch? {
        isClosing = true
        val resumption = mediaResumption ?: return null
        if (!isMediaPlaying) {
            resumption.forget()
            return null
        }
        resumption.pause()
        return CountDownLatch(1).also { mediaPaused = it }
    }

    private fun onPipes(pipes: List<ParcelFileDescriptor>) {
        executor.execute {
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
        executor.execute { microphone?.setRecording(isRecording) }
    }

    private fun onActivity(players: List<AudioPlayer>, focusHolders: List<AudioFocusHolder>) {
        executor.execute {
            val divertedPlayers = players.filter { isDiverted(it.usage) }
            isMediaPlaying = isMediaPlaying(divertedPlayers)
            if (!isMediaPlaying) mediaPaused?.countDown()
            for (channel in streaming) {
                channel.ready?.stream?.setActive(
                    divertedPlayers.any { it.usage in channel.usages }
                )
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
