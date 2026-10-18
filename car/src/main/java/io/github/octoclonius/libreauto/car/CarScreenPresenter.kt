package io.github.octoclonius.libreauto.car

import android.content.Context
import android.hardware.display.DisplayManager
import android.util.Log
import android.view.WindowManager
import io.github.octoclonius.libreauto.session.input.CarTouches
import io.github.octoclonius.libreauto.session.video.CarDisplay
import io.github.octoclonius.libreauto.session.video.CarDisplayState
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

class CarScreenPresenter(
    private val context: Context,
    private val isTouchIndicatorEnabled: Boolean,
) {

    companion object {
        private const val TAG = "CarScreenPresenter"
    }

    private val scope = MainScope()
    private var presentation: CarPresentation? = null

    fun start() {
        scope.launch { CarDisplay.state.collect { present(it) } }
        scope.launch { CarTouches.touches.collect { presentation?.dispatchTouch(it) } }
    }

    private fun present(state: CarDisplayState?) {
        presentation?.dismiss()
        presentation = null
        if (state == null) return
        val displayId = state.id
        val display = context.getSystemService(DisplayManager::class.java).getDisplay(displayId)
        if (display == null) {
            Log.w(TAG, "The car display $displayId is gone")
            return
        }
        val shown =
            CarPresentation(context, display, state.contentInsets, isTouchIndicatorEnabled)
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
