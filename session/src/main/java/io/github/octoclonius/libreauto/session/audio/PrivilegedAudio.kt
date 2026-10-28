package io.github.octoclonius.libreauto.session.audio

import android.os.ParcelFileDescriptor
import android.util.Log
import io.github.octoclonius.libreauto.privileged.AudioFocusHolder
import io.github.octoclonius.libreauto.privileged.AudioInput
import io.github.octoclonius.libreauto.privileged.AudioOutput
import io.github.octoclonius.libreauto.privileged.AudioPlayer
import io.github.octoclonius.libreauto.privileged.IAudioActivityListener
import io.github.octoclonius.libreauto.privileged.ICarAudioSession
import io.github.octoclonius.libreauto.privileged.IPrivilegedService
import io.github.octoclonius.libreauto.session.privileged.PrivilegedServiceConnection
import io.github.octoclonius.libreauto.session.privileged.call
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch

internal class PrivilegedAudio(
    private val privileged: PrivilegedServiceConnection,
    private val scope: CoroutineScope,
    private val outputs: List<AudioOutput>,
    private val input: AudioInput?,
    private val onPipes: (List<ParcelFileDescriptor>) -> Unit,
    private val onActivity: (List<AudioPlayer>, List<AudioFocusHolder>) -> Unit,
    private val onMicrophoneActivity: (Boolean) -> Unit,
) {

    companion object {
        private const val TAG = "PrivilegedAudio"
    }

    private var following: Job? = null
    private var current: IPrivilegedService? = null
    private var session: ICarAudioSession? = null
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
        following = scope.launch(Dispatchers.IO) { privileged.service.collect(::follow) }
    }

    suspend fun close() {
        following?.cancelAndJoin()
        stop()
    }

    private fun follow(service: IPrivilegedService?) {
        if (service === current) return
        stop()
        onActivity(emptyList(), emptyList())
        onMicrophoneActivity(false)
        if (service == null) {
            Log.i(TAG, "No audio for the car while the privileged service isn't running")
            return
        }
        val started =
            service.call("divert audio to the car") {
                it.startAudio(outputs.toTypedArray(), input, listener)
            } ?: return
        current = service
        session = started
        val pipes = started.call("take the car audio's pipes") { it.pipes } ?: return
        Log.i(
            TAG,
            "The privileged service diverts ${outputs.size} outputs to the car" +
                if (input != null) " and injects its microphone" else "",
        )
        onPipes(pipes.toList())
    }

    private fun stop() {
        session?.call("stop the car audio") { it.stop() }
        session = null
        current = null
    }
}
