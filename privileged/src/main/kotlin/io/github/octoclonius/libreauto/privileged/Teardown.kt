package io.github.octoclonius.libreauto.privileged

import android.util.Log

internal class Teardown(private val onFailure: (action: String, cause: Throwable) -> Unit) {

    fun step(action: String, close: () -> Unit) {
        try {
            close()
        } catch (e: Throwable) {
            onFailure(action, e.containedCause())
        }
    }
}

internal fun closeAll(tag: String, steps: Teardown.() -> Unit) {
    closeAll({ action, cause -> Log.e(tag, "$action failed", cause) }, steps)
}

internal fun closeAll(
    onFailure: (action: String, cause: Throwable) -> Unit,
    steps: Teardown.() -> Unit,
) {
    Teardown(onFailure).steps()
}
