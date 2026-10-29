package io.github.octoclonius.libreauto.privileged

import android.os.Binder
import android.os.IBinder
import android.util.Log
import rikka.shizuku.ShizukuApiConstants

internal fun isCallerAllowed(code: Int, callingUid: Int, appUid: Int, serviceUid: Int): Boolean =
    when (code) {
        ShizukuApiConstants.USER_SERVICE_TRANSACTION_destroy -> callingUid == serviceUid
        else -> isAppCallAllowed(code, callingUid, appUid, serviceUid)
    }

internal fun isAppCallAllowed(code: Int, callingUid: Int, appUid: Int, serviceUid: Int): Boolean =
    when (code) {
        IBinder.INTERFACE_TRANSACTION -> callingUid == serviceUid || callingUid == appUid
        else -> callingUid == appUid
    }

internal fun checkCaller(tag: String, code: Int, isAllowed: (callingUid: Int) -> Boolean) {
    val caller = Binder.getCallingUid()
    if (isAllowed(caller)) return
    Log.w(tag, "Refused the call $code from uid $caller")
    throw SecurityException("The uid $caller may not make the call $code")
}
