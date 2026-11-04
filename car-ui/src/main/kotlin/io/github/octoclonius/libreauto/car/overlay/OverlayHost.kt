package io.github.octoclonius.libreauto.car.overlay

import android.content.Context
import android.hardware.display.DisplayManager
import android.util.Log
import android.view.WindowManager
import io.github.octoclonius.libreauto.car.notifications.NotificationsState
import io.github.octoclonius.libreauto.car.quicksettings.QuickSettingsState
import io.github.octoclonius.libreauto.projection.CarSession
import io.github.octoclonius.libreauto.projection.display.OverlayDisplayState
import io.github.octoclonius.libreauto.projection.settings.PhoneSettingsRepository
import java.io.Closeable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

internal class OverlayHost(
    context: Context,
    private val session: CarSession,
    private val quickSettings: QuickSettingsState,
    private val phoneSettings: PhoneSettingsRepository,
    private val notifications: NotificationsState,
) : Closeable {

    companion object {
        private const val TAG = "OverlayHost"
    }

    private val context = context.applicationContext
    private var window: OverlayWindow? = null

    fun start(scope: CoroutineScope) {
        val overlay = session.displays.overlay
        scope.launch { overlay.state.collect(::show) }
        scope.launch { overlay.touches.collect { window?.dispatchTouchEvent(it) } }
        scope.launch { overlay.outsideTouches.captured.collect { quickSettings.close() } }
        scope.launch { quickSettings.isOpen.collect(overlay.outsideTouches::setCapturing) }
        scope.launch {
            combine(quickSettings.isOpen, notifications.isHeadsUpOnScreen) { isOpen, isHeadsUp ->
                    isOpen || isHeadsUp
                }
                .collect(overlay::show)
        }
    }

    override fun close() {
        window?.dismiss()
        window = null
    }

    private fun show(overlay: OverlayDisplayState?) {
        window?.dismiss()
        window = null
        if (overlay == null) {
            quickSettings.close()
            return
        }
        val display = context.getSystemService(DisplayManager::class.java).getDisplay(overlay.id)
        if (display == null) {
            Log.w(TAG, "The overlay display ${overlay.id} is already gone")
            return
        }
        val created =
            OverlayWindow(context, display, session, quickSettings, phoneSettings, notifications)
        try {
            created.show()
        } catch (e: WindowManager.InvalidDisplayException) {
            Log.w(TAG, "The overlay display ${overlay.id} went away before the overlay showed", e)
            return
        }
        window = created
        Log.i(TAG, "Showing the overlay on display ${overlay.id}")
    }
}
