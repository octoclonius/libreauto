package io.github.octoclonius.libreauto.privileged.display

import android.content.Context
import android.os.PowerManager

internal class AmbientDisplay(context: Context) {

    companion object {
        private const val TOKEN = "LibreAuto car display"
    }

    private val powerManager = context.getSystemService(PowerManager::class.java)

    fun suppress() {
        setSuppressed(true)
    }

    fun allow() {
        setSuppressed(false)
    }

    private fun setSuppressed(isSuppressed: Boolean) {
        PowerManager::class
            .java
            .getMethod(
                "suppressAmbientDisplay",
                String::class.java,
                Boolean::class.javaPrimitiveType,
            )
            .invoke(powerManager, TOKEN, isSuppressed)
    }
}
