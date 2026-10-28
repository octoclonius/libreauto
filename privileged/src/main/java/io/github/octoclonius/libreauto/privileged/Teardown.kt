package io.github.octoclonius.libreauto.privileged

import android.util.Log
import java.lang.reflect.InvocationTargetException

internal class Teardown(private val onFailure: (action: String, cause: Throwable) -> Unit) {

    fun step(action: String, close: () -> Unit) {
        try {
            close()
        } catch (e: Exception) {
            onFailure(action, if (e is InvocationTargetException) e.targetException else e)
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
