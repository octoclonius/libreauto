package io.github.octoclonius.libreauto.session.video

import android.os.RemoteException
import android.util.Log
import android.view.Surface
import io.github.octoclonius.libreauto.privileged.IPrivilegedService
import io.github.octoclonius.libreauto.protocol.media.VideoFormat
import io.github.octoclonius.libreauto.session.privileged.PrivilegedServiceConnection
import java.io.Closeable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

internal class PrivilegedDisplay(private val surface: Surface, private val format: VideoFormat) :
    Closeable {

    companion object {
        private const val TAG = "PrivilegedDisplay"
    }

    private val scope = CoroutineScope(Dispatchers.IO)
    private var current: OwnedDisplay<IPrivilegedService>? = null

    fun start() {
        scope.launch { PrivilegedServiceConnection.service.collect(::follow) }
    }

    override fun close() {
        runBlocking { scope.coroutineContext.job.cancelAndJoin() }
        current?.let(::release)
        current = null
    }

    private fun follow(service: IPrivilegedService?) {
        if (service == null) Log.i(TAG, "No car display while the privileged service isn't running")
        val change = displayChange(current, service)
        change.release?.let {
            release(it)
            current = null
        }
        change.createOn?.let { owner -> current = create(owner)?.let { OwnedDisplay(owner, it) } }
    }

    private fun create(service: IPrivilegedService): Int? {
        val id =
            try {
                service.createDisplay(
                    surface,
                    format.displayWidth,
                    format.displayHeight,
                    format.density,
                    format.framesPerSecond,
                    PrivilegedServiceConnection.carHome,
                )
            } catch (e: RemoteException) {
                Log.w(TAG, "The privileged service didn't create the car display", e)
                return null
            } catch (e: RuntimeException) {
                Log.w(TAG, "The privileged service refused to create the car display", e)
                return null
            }
        Log.i(TAG, "The privileged service created the car display $id")
        CarDisplay.onCreated(id, format.displayContentInsets)
        return id
    }

    private fun release(display: OwnedDisplay<IPrivilegedService>) {
        CarDisplay.onReleased(display.id)
        try {
            display.service.releaseDisplay(display.id)
            Log.i(TAG, "Released the car display ${display.id}")
        } catch (e: RemoteException) {
            Log.i(TAG, "The privileged service is gone with the car display ${display.id}", e)
        } catch (e: RuntimeException) {
            Log.w(TAG, "Releasing the car display ${display.id} failed", e)
        }
    }
}
