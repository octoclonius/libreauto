package io.github.octoclonius.libreauto.session.input

import android.os.RemoteException
import android.util.Log
import io.github.octoclonius.libreauto.session.privileged.PrivilegedServiceConnection

object PrivilegedKeys {

    private const val TAG = "PrivilegedKeys"

    fun send(displayId: Int, keyCode: Int) {
        val service = PrivilegedServiceConnection.service.value
        if (service == null) {
            Log.w(TAG, "Dropped key $keyCode while the privileged service isn't running")
            return
        }
        try {
            service.sendKey(displayId, keyCode)
        } catch (e: RemoteException) {
            Log.w(TAG, "The privileged service didn't take key $keyCode", e)
        }
    }
}
