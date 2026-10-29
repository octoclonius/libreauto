package io.github.octoclonius.libreauto.car.quicksettings

import android.content.Context
import android.hardware.display.DisplayManager
import android.util.Log
import android.view.WindowManager
import io.github.octoclonius.libreauto.car.CarUi
import io.github.octoclonius.libreauto.session.display.OverlayDisplayState
import java.io.Closeable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal class QuickSettingsHost(context: Context, private val carUi: CarUi) : Closeable {

    companion object {
        private const val TAG = "QuickSettingsHost"
    }

    private val context = context.applicationContext
    private var window: QuickSettingsWindow? = null

    fun start(scope: CoroutineScope) {
        val overlay = carUi.session.displays.overlay
        scope.launch { overlay.state.collect(::show) }
        scope.launch { overlay.touches.collect { window?.dispatchTouchEvent(it) } }
        scope.launch { carUi.quickSettings.isOpen.collect(overlay::show) }
    }

    override fun close() {
        window?.dismiss()
        window = null
    }

    private fun show(overlay: OverlayDisplayState?) {
        window?.dismiss()
        window = null
        if (overlay == null) {
            carUi.quickSettings.close()
            return
        }
        val display = context.getSystemService(DisplayManager::class.java).getDisplay(overlay.id)
        if (display == null) {
            Log.w(TAG, "The overlay display ${overlay.id} is already gone")
            return
        }
        val created = QuickSettingsWindow(context, display, carUi)
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
