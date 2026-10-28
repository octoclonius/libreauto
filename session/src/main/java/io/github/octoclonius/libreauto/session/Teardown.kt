package io.github.octoclonius.libreauto.session

import android.util.Log

internal class Teardown(val onFailure: (action: String, cause: Exception) -> Unit) {

    inline fun step(action: String, close: () -> Unit) {
        try {
            close()
        } catch (e: Exception) {
            onFailure(action, e)
        }
    }
}

internal inline fun closeAll(tag: String, steps: Teardown.() -> Unit) {
    closeAll({ action, cause -> Log.e(tag, "$action failed", cause) }, steps)
}

internal inline fun closeAll(
    noinline onFailure: (action: String, cause: Exception) -> Unit,
    steps: Teardown.() -> Unit,
) {
    Teardown(onFailure).steps()
}
