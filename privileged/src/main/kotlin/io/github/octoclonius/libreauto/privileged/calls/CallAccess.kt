package io.github.octoclonius.libreauto.privileged.calls

import android.app.AppOpsManager
import android.content.Context
import android.util.Log

internal class CallAccess(context: Context) {

    private companion object {
        const val TAG = "CallAccess"
        const val OP_MANAGE_ONGOING_CALLS = "android:manage_ongoing_calls"
    }

    private val appOps = context.getSystemService(AppOpsManager::class.java)
    private val setMode by lazy {
        AppOpsManager::class
            .java
            .getMethod(
                "setMode",
                String::class.java,
                Int::class.javaPrimitiveType,
                String::class.java,
                Int::class.javaPrimitiveType,
            )
    }
    private val defaultMode by lazy {
        AppOpsManager::class
            .java
            .getMethod("opToDefaultMode", String::class.java)
            .invoke(null, OP_MANAGE_ONGOING_CALLS) as Int
    }

    fun allow(appUid: Int, appPackage: String) {
        val mode = appOps.checkOpRawNoThrow(OP_MANAGE_ONGOING_CALLS, appUid, appPackage, null)
        if (mode != defaultMode) {
            Log.i(TAG, "Kept $appPackage's access to the phone's calls at mode $mode")
            return
        }
        setMode.invoke(
            appOps,
            OP_MANAGE_ONGOING_CALLS,
            appUid,
            appPackage,
            AppOpsManager.MODE_ALLOWED,
        )
        Log.i(TAG, "Allowed $appPackage the phone's calls")
    }
}
