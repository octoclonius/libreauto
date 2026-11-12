package io.github.octoclonius.libreauto.privileged

import android.os.Binder
import android.os.RemoteException
import android.util.Log

internal fun <T> checkedCall(tag: String, action: String, refusal: String?, call: () -> T): T? {
    if (refusal != null) {
        Log.w(tag, "$action refused: $refusal")
        return null
    }
    return identityCall(tag, action, call)
}

internal fun <T> identityCall(tag: String, action: String, call: () -> T): T? {
    val identity = Binder.clearCallingIdentity()
    return try {
        containedCall(tag, action, call)
    } finally {
        Binder.restoreCallingIdentity(identity)
    }
}

internal fun <T> containedCall(tag: String, action: String, call: () -> T): T? =
    try {
        call()
    } catch (e: Throwable) {
        Log.e(tag, "$action failed", e.containedCause())
        null
    }

internal fun listenerCall(tag: String, dropped: String, call: () -> Unit) {
    try {
        call()
    } catch (e: RemoteException) {
        Log.w(tag, "The app is gone; dropped $dropped", e)
    }
}

internal fun <T> reportingCall(
    tag: String,
    action: String,
    onFailure: () -> Unit = {},
    call: () -> T,
): T {
    val identity = Binder.clearCallingIdentity()
    try {
        return call()
    } catch (e: Throwable) {
        val cause = e.containedCause()
        onFailure()
        Log.e(tag, "$action failed", cause)
        throw IllegalStateException(
            "$action failed: ${cause.javaClass.simpleName}: ${cause.message}"
        )
    } finally {
        Binder.restoreCallingIdentity(identity)
    }
}
