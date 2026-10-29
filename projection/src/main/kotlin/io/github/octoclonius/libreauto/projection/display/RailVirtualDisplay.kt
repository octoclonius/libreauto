package io.github.octoclonius.libreauto.projection.display

import android.content.Context
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.util.Log
import android.view.Surface
import java.io.Closeable

internal class RailVirtualDisplay(
    context: Context,
    surface: Surface,
    layout: CarLayout,
    private val rail: RailDisplay,
) : Closeable {

    companion object {
        private const val TAG = "RailVirtualDisplay"
        private const val NAME = "LibreAuto rail"
    }

    private val display: VirtualDisplay =
        context
            .getSystemService(DisplayManager::class.java)
            .createVirtualDisplay(
                NAME,
                layout.rail.width,
                layout.rail.height,
                layout.format.density,
                surface,
                0,
            ) ?: error("No rail display")

    private val id = display.display.displayId

    init {
        Log.i(
            TAG,
            "Created the rail display $id: ${layout.rail.width}x${layout.rail.height} at " +
                "${layout.format.density} dpi on the ${layout.railSide} side",
        )
        rail.onCreated(id, layout.railSide, layout.railContentInsets)
    }

    fun resize(layout: CarLayout) {
        display.resize(layout.rail.width, layout.rail.height, layout.format.density)
        Log.i(
            TAG,
            "Resized the rail display $id to ${layout.rail.width}x${layout.rail.height} on the " +
                "${layout.railSide} side",
        )
        rail.onCreated(id, layout.railSide, layout.railContentInsets)
    }

    override fun close() {
        rail.onReleased(id)
        display.release()
        Log.i(TAG, "Released the rail display $id")
    }
}
