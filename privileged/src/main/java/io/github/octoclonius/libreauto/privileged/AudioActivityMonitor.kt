package io.github.octoclonius.libreauto.privileged

import android.content.Context
import android.media.AudioFocusInfo
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.RemoteException
import android.util.Log
import java.io.Closeable

internal class AudioActivityMonitor(
    context: Context,
    private val listener: IAudioActivityListener,
) : Closeable {

    companion object {
        private const val TAG = "AudioActivityMonitor"
        private val CONFIGURATION = AudioPlaybackConfiguration::class.java
        private val PLAYER_STATE_STARTED =
            CONFIGURATION.getField("PLAYER_STATE_STARTED").getInt(null)
        private val getPlayerState = CONFIGURATION.getMethod("getPlayerState")
        private val getClientUid = CONFIGURATION.getMethod("getClientUid")
        private val getPlayerInterfaceId = CONFIGURATION.getMethod("getPlayerInterfaceId")
    }

    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val thread = HandlerThread("audio-activity").apply { start() }
    private val handler = Handler(thread.looper)
    private var focusStack: () -> List<AudioFocusInfo> = { emptyList() }
    private val callback =
        object : AudioManager.AudioPlaybackCallback() {
            override fun onPlaybackConfigChanged(configs: List<AudioPlaybackConfiguration>) {
                report()
            }
        }

    val looper: Looper
        get() = thread.looper

    fun start(focusStack: () -> List<AudioFocusInfo>) {
        this.focusStack = focusStack
        audioManager.registerAudioPlaybackCallback(callback, handler)
        handler.post(::report)
    }

    fun onFocusChanged() {
        handler.post(::report)
    }

    override fun close() {
        audioManager.unregisterAudioPlaybackCallback(callback)
        thread.quitSafely()
        thread.join()
    }

    private fun report() {
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
        try {
            listener.onAudioActivity(players.toTypedArray(), focusHolders.toTypedArray())
        } catch (e: RemoteException) {
            Log.w(TAG, "The app is gone; dropped the audio activity", e)
        }
    }
}
