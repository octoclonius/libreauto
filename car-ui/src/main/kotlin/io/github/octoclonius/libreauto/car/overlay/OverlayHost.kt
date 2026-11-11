package io.github.octoclonius.libreauto.car.overlay

import android.content.Context
import android.hardware.display.DisplayManager
import android.util.Log
import android.view.WindowManager
import androidx.compose.runtime.Composable
import io.github.octoclonius.libreauto.projection.display.OverlayDisplay
import io.github.octoclonius.libreauto.projection.display.OverlayDisplayState
import java.io.Closeable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal class OverlayHost(
    context: Context,
    private val overlay: OverlayDisplay,
    private val content: @Composable () -> Unit,
) : Closeable {

    companion object {
        private const val TAG = "OverlayHost"
    }

    private val context = context.applicationContext
    private var window: OverlayWindow? = null

    fun start(scope: CoroutineScope) {
        scope.launch { overlay.state.collect(::show) }
        scope.launch { overlay.touches.collect { window?.dispatchTouchEvent(it) } }
    }

    override fun close() {
        window?.dismiss()
        window = null
    }

    private fun show(state: OverlayDisplayState?) {
        window?.dismiss()
        window = null
        if (state == null) return
        val name = overlay.name
        val display = context.getSystemService(DisplayManager::class.java).getDisplay(state.id)
        if (display == null) {
            Log.w(TAG, "The $name display ${state.id} is already gone")
            return
        }
        val created = OverlayWindow(context, display, content)
        try {
            created.show()
        } catch (e: WindowManager.InvalidDisplayException) {
            Log.w(TAG, "The $name display ${state.id} went away before its window showed", e)
            return
        }
        window = created
        Log.i(TAG, "Showing the $name window on display ${state.id}")
    }
}
