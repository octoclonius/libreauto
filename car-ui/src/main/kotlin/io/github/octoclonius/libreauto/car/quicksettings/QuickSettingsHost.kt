package io.github.octoclonius.libreauto.car.quicksettings

import android.content.Context
import android.hardware.display.DisplayManager
import android.util.Log
import android.view.WindowManager
import io.github.octoclonius.libreauto.projection.CarSession
import io.github.octoclonius.libreauto.projection.display.OverlayDisplayState
import io.github.octoclonius.libreauto.projection.settings.PhoneSettingsRepository
import java.io.Closeable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal class QuickSettingsHost(
    context: Context,
    private val session: CarSession,
    private val state: QuickSettingsState,
    private val phoneSettings: PhoneSettingsRepository,
) : Closeable {

    companion object {
        private const val TAG = "QuickSettingsHost"
    }

    private val context = context.applicationContext
    private var window: QuickSettingsWindow? = null

    fun start(scope: CoroutineScope) {
        val overlay = session.displays.overlay
        scope.launch { overlay.state.collect(::show) }
        scope.launch { overlay.touches.collect { window?.dispatchTouchEvent(it) } }
        scope.launch { state.isOpen.collect(overlay::show) }
    }

    override fun close() {
        window?.dismiss()
        window = null
    }

    private fun show(overlay: OverlayDisplayState?) {
        window?.dismiss()
        window = null
        if (overlay == null) {
            state.close()
            return
        }
        val display = context.getSystemService(DisplayManager::class.java).getDisplay(overlay.id)
        if (display == null) {
            Log.w(TAG, "The overlay display ${overlay.id} is already gone")
            return
        }
        val created = QuickSettingsWindow(context, display, session, state, phoneSettings)
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
