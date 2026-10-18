package io.github.octoclonius.libreauto.car

import android.graphics.Insets
import android.os.Bundle
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.github.octoclonius.libreauto.car.touch.TouchIndicator
import io.github.octoclonius.libreauto.car.touch.TouchIndicatorState
import io.github.octoclonius.libreauto.car.ui.theme.CarTheme
import io.github.octoclonius.libreauto.session.CarConnection
import io.github.octoclonius.libreauto.session.CarConnectionState
import io.github.octoclonius.libreauto.session.input.CarTouches
import io.github.octoclonius.libreauto.session.input.Touch
import io.github.octoclonius.libreauto.session.video.CarDisplay
import kotlinx.coroutines.launch

class CarHomeActivity : ComponentActivity() {

    private var touchIndicator: TouchIndicatorState? = null
    private var downTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val indicator =
            if (resources.getBoolean(R.bool.is_touch_indicator_enabled)) TouchIndicatorState()
            else null
        touchIndicator = indicator
        setContent {
            val connection by CarConnection.state.collectAsStateWithLifecycle()
            val carDisplay by CarDisplay.state.collectAsStateWithLifecycle()
            val contentInsets =
                carDisplay?.takeIf { it.id == display.displayId }?.contentInsets ?: Insets.NONE
            CarTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    CarScreen(
                        carName = (connection as? CarConnectionState.Connected)?.carName,
                        contentPadding = contentPadding(contentInsets),
                    )
                    indicator?.let { TouchIndicator(it) }
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                CarTouches.touches.collect {
                    if (CarDisplay.state.value?.id == display.displayId) dispatchTouch(it)
                }
            }
        }
    }

    private fun dispatchTouch(touch: Touch) {
        touchIndicator?.onTouch(touch)
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

    @Composable
    private fun contentPadding(contentInsets: Insets): PaddingValues =
        with(LocalDensity.current) {
            PaddingValues.Absolute(
                left = contentInsets.left.toDp(),
                top = contentInsets.top.toDp(),
                right = contentInsets.right.toDp(),
                bottom = contentInsets.bottom.toDp(),
            )
        }
}
