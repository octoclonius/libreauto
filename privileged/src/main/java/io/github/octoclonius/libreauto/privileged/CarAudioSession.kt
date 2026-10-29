package io.github.octoclonius.libreauto.privileged

import android.content.Context
import android.os.IBinder
import android.os.Looper
import android.os.Parcel
import android.os.ParcelFileDescriptor
import android.os.Process
import android.util.Log
import java.io.Closeable

internal class CarAudioSession(
    private val context: Context,
    private val outputs: List<AudioOutput>,
    private val input: AudioInput?,
    private val listener: IAudioActivityListener,
    private val looper: Looper,
    private val appUid: Int,
) : ICarAudioSession.Stub(), Closeable {

    private companion object {
        const val TAG = "CarAudioSession"
    }

    private val lock = Any()
    private var monitor: AudioActivityMonitor? = null
    private var policy: CarAudioPolicy? = null
    private var isWatchingApp = false
    private val appDeath =
        IBinder.DeathRecipient {
            Log.w(TAG, "The app died; stopping the car audio")
            close()
        }

    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        checkCaller(TAG, code) { isAppCallAllowed(code, it, appUid, Process.myUid()) }
        return super.onTransact(code, data, reply, flags)
    }

    fun start() {
        synchronized(lock) {
            reportingCall(TAG, "Diverting audio to the car", onFailure = ::closeLocked) {
                val activity = AudioActivityMonitor(context, listener, looper)
                monitor = activity
                val registered =
                    CarAudioPolicy.register(
                        context,
                        outputs,
                        input,
                        looper,
                        activity::onFocusChanged,
                    )
                policy = registered
                activity.start(registered::focusStack, input != null)
                listener.asBinder().linkToDeath(appDeath, 0)
                isWatchingApp = true
            }
        }
    }

    override fun getPipes(): Array<ParcelFileDescriptor> =
        synchronized(lock) { policy?.pipes.orEmpty().toTypedArray() }

    override fun stop() {
        close()
    }

    override fun close() {
        synchronized(lock) { identityCall(TAG, "Stopping the car audio", ::closeLocked) }
    }

    private fun closeLocked() {
        val activity = monitor
        val registered = policy
        monitor = null
        policy = null
        val wasWatchingApp = isWatchingApp
        isWatchingApp = false
        closeAll(TAG) {
            step("Stopping watching the app") {
                if (wasWatchingApp) listener.asBinder().unlinkToDeath(appDeath, 0)
            }
            step("Unregistering the car audio policy") { registered?.close() }
            step("Stopping the audio activity monitor") { activity?.close() }
        }
    }
}
