package io.github.octoclonius.libreauto.session.calls

import android.os.RemoteException
import android.util.Log
import io.github.octoclonius.libreauto.privileged.IPrivilegedService

internal object PrivilegedCalls {

    private const val TAG = "PrivilegedCalls"

    fun allowAccess(service: IPrivilegedService) {
        try {
            service.allowCallAccess()
        } catch (e: RemoteException) {
            Log.w(TAG, "The privileged service didn't allow the phone's calls", e)
        }
    }
}
