package io.github.octoclonius.libreauto.session.input

import android.os.RemoteException
import android.util.Log
import io.github.octoclonius.libreauto.session.privileged.PrivilegedServiceConnection

internal object PrivilegedTouches {

    private const val TAG = "PrivilegedTouches"

    fun send(eventTimeNanos: Long, touch: Touch) {
        val service = PrivilegedServiceConnection.service.value
        if (service == null) {
            Log.w(TAG, "Dropped touch $touch while the privileged service isn't running")
            return
        }
        try {
            service.sendTouch(
                eventTimeNanos,
                touch.action,
                touch.actionIndex,
                IntArray(touch.pointers.size) { touch.pointers[it].id },
                IntArray(touch.pointers.size) { touch.pointers[it].x },
                IntArray(touch.pointers.size) { touch.pointers[it].y },
            )
        } catch (e: RemoteException) {
            Log.w(TAG, "The privileged service didn't take touch $touch", e)
        }
    }
}
