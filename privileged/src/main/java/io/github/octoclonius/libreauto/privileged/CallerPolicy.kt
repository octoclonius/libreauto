package io.github.octoclonius.libreauto.privileged

import android.os.IBinder

internal fun isCallerAllowed(code: Int, callingUid: Int, appUid: Int, serviceUid: Int): Boolean =
    when (code) {
        IPrivilegedService.Stub.TRANSACTION_destroy -> callingUid == serviceUid
        IBinder.INTERFACE_TRANSACTION -> callingUid == serviceUid || callingUid == appUid
        else -> callingUid == appUid
    }
