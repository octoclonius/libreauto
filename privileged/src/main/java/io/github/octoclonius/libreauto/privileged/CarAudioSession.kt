package io.github.octoclonius.libreauto.privileged

import android.content.Context
import android.os.Looper
import android.os.ParcelFileDescriptor
import java.io.Closeable

internal class CarAudioSession(
    private val context: Context,
    private val outputs: List<AudioOutput>,
    private val input: AudioInput?,
    private val listener: IAudioActivityListener,
    private val looper: Looper,
) : Closeable {

    private companion object {
        const val TAG = "CarAudioSession"
    }

    private var monitor: AudioActivityMonitor? = null
    private var policy: CarAudioPolicy? = null

    fun start(): List<ParcelFileDescriptor> =
        reportingCall(TAG, "Diverting audio to the car", onFailure = ::close) {
            val activity = AudioActivityMonitor(context, listener, looper)
            monitor = activity
            val registered =
                CarAudioPolicy.register(context, outputs, input, looper, activity::onFocusChanged)
            policy = registered
            activity.start(registered::focusStack, input != null)
            registered.pipes
        }

    override fun close() {
        val activity = monitor
        val registered = policy
        monitor = null
        policy = null
        closeAll(TAG) {
            step("Unregistering the car audio policy") { registered?.close() }
            step("Stopping the audio activity monitor") { activity?.close() }
        }
    }
}
