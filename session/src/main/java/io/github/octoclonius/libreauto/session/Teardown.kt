package io.github.octoclonius.libreauto.session

import android.util.Log

internal class Teardown(private val onFailure: (action: String, cause: Exception) -> Unit) {

    fun step(action: String, close: () -> Unit) {
        try {
            close()
        } catch (e: Exception) {
            onFailure(action, e)
        }
    }
}

internal fun closeAll(tag: String, steps: Teardown.() -> Unit) {
    closeAll({ action, cause -> Log.e(tag, "$action failed", cause) }, steps)
}

internal fun closeAll(
    onFailure: (action: String, cause: Exception) -> Unit,
    steps: Teardown.() -> Unit,
) {
    Teardown(onFailure).steps()
}
