package io.github.octoclonius.libreauto.car

import android.app.Presentation
import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import android.view.Display
import android.view.InputDevice
import android.view.MotionEvent
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import io.github.octoclonius.libreauto.car.ui.theme.CarTheme
import io.github.octoclonius.libreauto.session.CarConnection
import io.github.octoclonius.libreauto.session.CarConnectionState
import io.github.octoclonius.libreauto.session.input.Touch

class CarPresentation(context: Context, display: Display) :
    Presentation(context, display), SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private var downTime = 0L

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedStateRegistryController.performRestore(savedInstanceState)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        val decorView = checkNotNull(window).decorView
        decorView.setViewTreeLifecycleOwner(this)
        decorView.setViewTreeSavedStateRegistryOwner(this)
        setContentView(
            ComposeView(context).apply {
                setContent {
                    val state by CarConnection.state.collectAsStateWithLifecycle()
                    CarTheme {
                        CarScreen(carName = (state as? CarConnectionState.Connected)?.carName)
                    }
                }
            }
        )
    }

    fun dispatchTouch(touch: Touch) {
        val now = SystemClock.uptimeMillis()
        if (touch.action == MotionEvent.ACTION_DOWN) downTime = now
        val properties =
            Array(touch.pointers.size) {
                MotionEvent.PointerProperties().apply {
                    id = touch.pointers[it].id
                    toolType = MotionEvent.TOOL_TYPE_FINGER
                }
            }
        val coords =
            Array(touch.pointers.size) {
                MotionEvent.PointerCoords().apply {
                    x = touch.pointers[it].x.toFloat()
                    y = touch.pointers[it].y.toFloat()
                }
            }
        val event =
            MotionEvent.obtain(
                downTime,
                now,
                touch.action or (touch.actionIndex shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                touch.pointers.size,
                properties,
                coords,
                0,
                0,
                1f,
                1f,
                0,
                0,
                InputDevice.SOURCE_TOUCHSCREEN,
                0,
            )
        dispatchTouchEvent(event)
        event.recycle()
    }

    override fun onStart() {
        super.onStart()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    override fun onStop() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        super.onStop()
    }
}
