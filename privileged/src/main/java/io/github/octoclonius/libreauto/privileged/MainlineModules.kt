package io.github.octoclonius.libreauto.privileged

import android.app.ActivityThread
import android.util.Log

internal object MainlineModules {

    private const val TAG = "MainlineModules"

    fun initialize() {
        try {
            ActivityThread.initializeMainlineModules()
            Log.i(TAG, "Initialized the mainline modules' service managers")
        } catch (e: IllegalStateException) {
            Log.i(TAG, "The mainline modules' service managers were already initialized", e)
        }
    }
}
