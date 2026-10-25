package io.github.octoclonius.libreauto.session.tasks

import android.os.RemoteException
import android.util.Log
import io.github.octoclonius.libreauto.session.privileged.PrivilegedServiceConnection

object PrivilegedTasks {

    private const val TAG = "PrivilegedTasks"

    fun show(displayId: Int, taskId: Int) {
        val service = PrivilegedServiceConnection.service.value
        if (service == null) {
            Log.w(TAG, "Didn't show the task $taskId while the privileged service isn't running")
            return
        }
        try {
            service.showTask(displayId, taskId)
        } catch (e: RemoteException) {
            Log.w(TAG, "The privileged service didn't show the task $taskId", e)
        }
    }

    fun close(displayId: Int, taskId: Int) {
        val service = PrivilegedServiceConnection.service.value
        if (service == null) {
            Log.w(TAG, "Didn't close the task $taskId while the privileged service isn't running")
            return
        }
        try {
            service.closeTask(displayId, taskId)
        } catch (e: RemoteException) {
            Log.w(TAG, "The privileged service didn't close the task $taskId", e)
        }
    }
}
