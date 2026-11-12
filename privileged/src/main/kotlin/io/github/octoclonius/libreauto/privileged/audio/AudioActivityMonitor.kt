package io.github.octoclonius.libreauto.privileged.audio

import android.content.Context
import android.media.AudioFocusInfo
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.media.AudioRecordingConfiguration
import android.os.Handler
import android.os.Looper
import io.github.octoclonius.libreauto.privileged.api.AudioFocusHolder
import io.github.octoclonius.libreauto.privileged.api.AudioPlayer
import io.github.octoclonius.libreauto.privileged.api.IAudioActivityListener
import io.github.octoclonius.libreauto.privileged.containedCall
import io.github.octoclonius.libreauto.privileged.listenerCall
import java.io.Closeable
import java.util.concurrent.CountDownLatch

internal class AudioActivityMonitor(
    context: Context,
    private val listener: IAudioActivityListener,
    looper: Looper,
) : Closeable {

    companion object {
        private const val TAG = "AudioActivityMonitor"
        private val CONFIGURATION = AudioPlaybackConfiguration::class.java
        private val PLAYER_STATE_STARTED by lazy {
            CONFIGURATION.getField("PLAYER_STATE_STARTED").getInt(null)
        }
        private val getPlayerState by lazy {
            CONFIGURATION.getMethod("getPlayerState")
        }
        private val getClientUid by lazy {
            CONFIGURATION.getMethod("getClientUid")
        }
        private val getPlayerInterfaceId by lazy {
            CONFIGURATION.getMethod("getPlayerInterfaceId")
        }
    }

    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val handler = Handler(looper)
    @Volatile private var isClosed = false
    private var focusStack: () -> List<AudioFocusInfo> = { emptyList() }
    private var isMicrophoneWatched = false
    private val callback =
        object : AudioManager.AudioPlaybackCallback() {
            override fun onPlaybackConfigChanged(configs: List<AudioPlaybackConfiguration>) {
                report()
            }
        }
    private val recordingCallback =
        object : AudioManager.AudioRecordingCallback() {
            override fun onRecordingConfigChanged(configs: List<AudioRecordingConfiguration>) {
                reportRecording()
            }
        }

    fun start(focusStack: () -> List<AudioFocusInfo>, isMicrophoneWatched: Boolean) {
        this.focusStack = focusStack
        audioManager.registerAudioPlaybackCallback(callback, handler)
        handler.post(::report)
        this.isMicrophoneWatched = isMicrophoneWatched
        if (isMicrophoneWatched) {
            audioManager.registerAudioRecordingCallback(recordingCallback, handler)
            handler.post(::reportRecording)
        }
    }

    fun onFocusChanged() {
        if (!isClosed) handler.post(::report)
    }

    override fun close() {
        audioManager.unregisterAudioPlaybackCallback(callback)
        if (isMicrophoneWatched) audioManager.unregisterAudioRecordingCallback(recordingCallback)
        isClosed = true
        val reported = CountDownLatch(1)
        handler.post(reported::countDown)
        reported.await()
    }

    private fun report() {
        if (isClosed) return
        containedCall(TAG, "Reporting the audio activity") { reportActivity() }
    }

    private fun reportActivity() {
        val players =
            audioManager.activePlaybackConfigurations
                .filter { getPlayerState.invoke(it) == PLAYER_STATE_STARTED }
                .map {
                    AudioPlayer(
                        getPlayerInterfaceId.invoke(it) as Int,
                        getClientUid.invoke(it) as Int,
                        it.audioAttributes.usage,
                    )
                }
        val focusHolders =
            focusStack().map {
                AudioFocusHolder(it.clientId, it.clientUid, it.attributes.usage, it.gainRequest)
            }
        listenerCall(TAG, "the audio activity") {
            listener.onAudioActivity(players.toTypedArray(), focusHolders.toTypedArray())
        }
    }

    private fun reportRecording() {
        if (isClosed) return
        containedCall(TAG, "Reporting the microphone activity") { reportMicrophone() }
    }

    private fun reportMicrophone() {
        val isRecording =
            audioManager.activeRecordingConfigurations.any {
                it.clientAudioSource in CarAudioPolicy.MICROPHONE_SOURCES && !it.isClientSilenced
            }
        listenerCall(TAG, "the microphone activity") { listener.onMicrophoneActivity(isRecording) }
    }
}
