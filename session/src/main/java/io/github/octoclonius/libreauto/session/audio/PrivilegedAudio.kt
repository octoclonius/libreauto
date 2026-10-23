package io.github.octoclonius.libreauto.session.audio

import android.os.ParcelFileDescriptor
import android.os.RemoteException
import android.util.Log
import io.github.octoclonius.libreauto.privileged.AudioFocusHolder
import io.github.octoclonius.libreauto.privileged.AudioInput
import io.github.octoclonius.libreauto.privileged.AudioOutput
import io.github.octoclonius.libreauto.privileged.AudioPlayer
import io.github.octoclonius.libreauto.privileged.IAudioActivityListener
import io.github.octoclonius.libreauto.privileged.IPrivilegedService
import io.github.octoclonius.libreauto.session.privileged.PrivilegedServiceConnection
import java.io.Closeable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

internal class PrivilegedAudio(
    private val outputs: List<AudioOutput>,
    private val input: AudioInput?,
    private val onPipes: (List<ParcelFileDescriptor>) -> Unit,
    private val onActivity: (List<AudioPlayer>, List<AudioFocusHolder>) -> Unit,
    private val onMicrophoneActivity: (Boolean) -> Unit,
) : Closeable {

    companion object {
        private const val TAG = "PrivilegedAudio"
    }

    private val scope = CoroutineScope(Dispatchers.IO)
    private var current: IPrivilegedService? = null
    private val listener =
        object : IAudioActivityListener.Stub() {
            override fun onAudioActivity(
                players: Array<AudioPlayer>,
                focusHolders: Array<AudioFocusHolder>,
            ) {
                onActivity(players.toList(), focusHolders.toList())
            }

            override fun onMicrophoneActivity(isRecording: Boolean) {
                this@PrivilegedAudio.onMicrophoneActivity(isRecording)
            }
        }

    fun start() {
        scope.launch { PrivilegedServiceConnection.service.collect(::follow) }
    }

    override fun close() {
        runBlocking { scope.coroutineContext.job.cancelAndJoin() }
        current?.let(::stop)
        current = null
    }

    private fun follow(service: IPrivilegedService?) {
        if (service === current) return
        current?.let(::stop)
        current = null
        onActivity(emptyList(), emptyList())
        onMicrophoneActivity(false)
        if (service == null) {
            Log.i(TAG, "No audio for the car while the privileged service isn't running")
            return
        }
        val pipes =
            try {
                service.startAudio(outputs.toTypedArray(), input, listener)
            } catch (e: RemoteException) {
                Log.w(TAG, "The privileged service didn't divert audio to the car", e)
                return
            } catch (e: RuntimeException) {
                Log.w(TAG, "The privileged service refused to divert audio to the car", e)
                return
            }
        current = service
        Log.i(
            TAG,
            "The privileged service diverts ${outputs.size} outputs to the car" +
                if (input != null) " and injects its microphone" else "",
        )
        onPipes(pipes.toList())
    }

    private fun stop(service: IPrivilegedService) {
        try {
            service.stopAudio()
        } catch (e: RemoteException) {
            Log.i(TAG, "The privileged service is gone with the car audio", e)
        } catch (e: RuntimeException) {
            Log.w(TAG, "Stopping the car audio failed", e)
        }
    }
}
