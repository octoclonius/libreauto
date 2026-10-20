package io.github.octoclonius.libreauto.car.rail

import android.content.Context
import android.hardware.display.DisplayManager
import android.util.Log
import android.view.WindowManager
import io.github.octoclonius.libreauto.session.video.RailDisplay
import io.github.octoclonius.libreauto.session.video.RailDisplayState
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

class RailHost(context: Context) {

    companion object {
        private const val TAG = "RailHost"
    }

    private val context = context.applicationContext
    private val scope = MainScope()
    private var window: RailWindow? = null

    fun start() {
        scope.launch { RailDisplay.state.collect(::show) }
        scope.launch { RailDisplay.touches.collect { window?.dispatchTouchEvent(it) } }
    }

    private fun show(rail: RailDisplayState?) {
        window?.dismiss()
        window = null
        if (rail == null) return
        val display = context.getSystemService(DisplayManager::class.java).getDisplay(rail.id)
        if (display == null) {
            Log.w(TAG, "The rail display ${rail.id} is already gone")
            return
        }
        val created = RailWindow(context, display, rail)
        try {
            created.show()
        } catch (e: WindowManager.InvalidDisplayException) {
            Log.w(TAG, "The rail display ${rail.id} went away before the rail showed", e)
            return
        }
        window = created
        Log.i(TAG, "Showing the rail on display ${rail.id}")
    }
}
