package io.github.octoclonius.libreauto.car

import android.graphics.Insets
import android.os.Bundle
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.octoclonius.libreauto.car.touch.TouchIndicator
import io.github.octoclonius.libreauto.car.touch.TouchIndicatorState
import io.github.octoclonius.libreauto.car.ui.theme.CarTheme
import io.github.octoclonius.libreauto.session.CarConnection
import io.github.octoclonius.libreauto.session.CarConnectionState
import io.github.octoclonius.libreauto.session.video.CarDisplay

class CarHomeActivity : ComponentActivity() {

    private var touchIndicator: TouchIndicatorState? = null

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
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        touchIndicator?.onTouch(event)
        return super.dispatchTouchEvent(event)
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
