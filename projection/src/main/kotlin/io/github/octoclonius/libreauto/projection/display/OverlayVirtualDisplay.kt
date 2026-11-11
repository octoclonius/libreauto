package io.github.octoclonius.libreauto.projection.display

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
    private val overlay: OverlayDisplay,
    onShown: (Boolean) -> Unit,
) : Closeable {

    companion object {
        private const val TAG = "OverlayVirtualDisplay"
    }

    val name: String
        get() = overlay.name

    private val display: VirtualDisplay =
        context
            .getSystemService(DisplayManager::class.java)
            .createVirtualDisplay(
                "LibreAuto $name",
                layout.apps.width,
                layout.apps.height,
                layout.format.density,
                surface,
                0,
            ) ?: error("No $name display")

    private val id = display.display.displayId

    init {
        Log.i(
            TAG,
            "Created the $name display $id: ${layout.apps.width}x${layout.apps.height} at " +
                "${layout.format.density} dpi",
        )
        overlay.onCreated(id, onShown)
    }

    fun resize(layout: CarLayout) {
        display.resize(layout.apps.width, layout.apps.height, layout.format.density)
        Log.i(TAG, "Resized the $name display $id to ${layout.apps.width}x${layout.apps.height}")
    }

    override fun close() {
        overlay.onReleased(id)
        display.release()
        Log.i(TAG, "Released the $name display $id")
    }
}
