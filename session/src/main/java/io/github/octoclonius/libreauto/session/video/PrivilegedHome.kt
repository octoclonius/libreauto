package io.github.octoclonius.libreauto.session.video

import android.os.RemoteException
import android.util.Log
import io.github.octoclonius.libreauto.session.privileged.PrivilegedServiceConnection

object PrivilegedHome {

    private const val TAG = "PrivilegedHome"

    fun show(displayId: Int) {
        val service = PrivilegedServiceConnection.service.value
        if (service == null) {
            Log.w(TAG, "Didn't show the home while the privileged service isn't running")
            return
        }
        try {
            service.showActivity(displayId, PrivilegedServiceConnection.carHomeIntent)
        } catch (e: RemoteException) {
            Log.w(TAG, "The privileged service didn't show the home", e)
        }
    }
}
