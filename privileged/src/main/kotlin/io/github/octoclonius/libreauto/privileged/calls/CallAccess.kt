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
    private val setMode =
        AppOpsManager::class
            .java
            .getMethod(
                "setMode",
                String::class.java,
                Int::class.javaPrimitiveType,
                String::class.java,
                Int::class.javaPrimitiveType,
            )

    fun allow(appUid: Int, appPackage: String) {
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
