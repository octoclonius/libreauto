package io.github.octoclonius.libreauto.car

import android.content.Context
import android.hardware.display.DisplayManager
import android.util.Log
import android.view.WindowManager
import io.github.octoclonius.libreauto.session.video.CarDisplay
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

class CarScreenPresenter(private val context: Context) {

    companion object {
        private const val TAG = "CarScreenPresenter"
    }

    private val scope = MainScope()
    private var presentation: CarPresentation? = null

    fun start() {
        scope.launch { CarDisplay.id.collect { present(it) } }
    }

    private fun present(displayId: Int?) {
        presentation?.dismiss()
        presentation = null
        if (displayId == null) return
        val display = context.getSystemService(DisplayManager::class.java).getDisplay(displayId)
        if (display == null) {
            Log.w(TAG, "The car display $displayId is gone")
            return
        }
        val shown = CarPresentation(context, display)
        try {
            shown.show()
        } catch (e: WindowManager.InvalidDisplayException) {
            Log.w(TAG, "Showing the car screen on display $displayId failed", e)
            return
        }
        Log.i(TAG, "Showing the car screen on display $displayId")
        presentation = shown
    }
}
