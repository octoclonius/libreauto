package io.github.octoclonius.libreauto.session.video

import android.content.Context
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.util.Log
import android.view.Surface
import java.io.Closeable

internal class OverlayVirtualDisplay(
    context: Context,
    surface: Surface,
    layout: CarLayout,
    onShown: (Boolean) -> Unit,
) : Closeable {

    companion object {
        private const val TAG = "OverlayVirtualDisplay"
        private const val NAME = "LibreAuto overlay"
    }

    private val display: VirtualDisplay =
        context
            .getSystemService(DisplayManager::class.java)
            .createVirtualDisplay(
                NAME,
                layout.apps.width,
                layout.apps.height,
                layout.format.density,
                surface,
                0,
            ) ?: error("No overlay display")

    private val id = display.display.displayId

    init {
        Log.i(
            TAG,
            "Created the overlay display $id: ${layout.apps.width}x${layout.apps.height} at " +
                "${layout.format.density} dpi",
        )
        OverlayDisplay.onCreated(id, onShown)
    }

    fun resize(layout: CarLayout) {
        display.resize(layout.apps.width, layout.apps.height, layout.format.density)
        Log.i(TAG, "Resized the overlay display $id to ${layout.apps.width}x${layout.apps.height}")
    }

    override fun close() {
        OverlayDisplay.onReleased(id)
        display.release()
        Log.i(TAG, "Released the overlay display $id")
    }
}
