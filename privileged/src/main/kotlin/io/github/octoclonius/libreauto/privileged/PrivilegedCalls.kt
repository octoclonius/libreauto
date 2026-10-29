package io.github.octoclonius.libreauto.privileged

import android.os.Binder
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
        call()
    } catch (e: Exception) {
        Log.e(tag, "$action failed", e.unwrapped())
        null
    } finally {
        Binder.restoreCallingIdentity(identity)
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
    } catch (e: Exception) {
        onFailure()
        val cause = e.unwrapped()
        Log.e(tag, "$action failed", cause)
        throw IllegalStateException(
            "$action failed: ${cause.javaClass.simpleName}: ${cause.message}"
        )
    } finally {
        Binder.restoreCallingIdentity(identity)
    }
}
