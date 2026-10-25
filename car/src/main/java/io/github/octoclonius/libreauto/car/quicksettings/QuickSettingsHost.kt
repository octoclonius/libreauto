package io.github.octoclonius.libreauto.car.quicksettings

import android.content.Context
import android.hardware.display.DisplayManager
import android.util.Log
import android.view.WindowManager
import io.github.octoclonius.libreauto.session.video.OverlayDisplay
import io.github.octoclonius.libreauto.session.video.OverlayDisplayState
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

class QuickSettingsHost(context: Context) {

    companion object {
        private const val TAG = "QuickSettingsHost"
    }

    private val context = context.applicationContext
    private val scope = MainScope()
    private var window: QuickSettingsWindow? = null

    fun start() {
        scope.launch { OverlayDisplay.state.collect(::show) }
        scope.launch { OverlayDisplay.touches.collect { window?.dispatchTouchEvent(it) } }
        scope.launch { QuickSettingsState.isOpen.collect(OverlayDisplay::show) }
    }

    private fun show(overlay: OverlayDisplayState?) {
        window?.dismiss()
        window = null
        if (overlay == null) {
            QuickSettingsState.close()
            return
        }
        val display = context.getSystemService(DisplayManager::class.java).getDisplay(overlay.id)
        if (display == null) {
            Log.w(TAG, "The overlay display ${overlay.id} is already gone")
            return
        }
        val created = QuickSettingsWindow(context, display)
        try {
            created.show()
        } catch (e: WindowManager.InvalidDisplayException) {
            Log.w(
                TAG,
                "The overlay display ${overlay.id} went away before quick settings showed",
                e,
            )
            return
        }
        window = created
        Log.i(TAG, "Showing quick settings on display ${overlay.id}")
    }
}
