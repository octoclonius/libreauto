package io.github.octoclonius.libreauto.car

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Insets
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.octoclonius.libreauto.car.apps.AppDrawer
import io.github.octoclonius.libreauto.car.apps.PhoneApp
import io.github.octoclonius.libreauto.car.apps.phoneApps
import io.github.octoclonius.libreauto.car.home.HomeState
import io.github.octoclonius.libreauto.car.touch.TouchIndicator
import io.github.octoclonius.libreauto.car.touch.TouchIndicatorState
import io.github.octoclonius.libreauto.car.ui.theme.CarTheme
import io.github.octoclonius.libreauto.session.video.CarDisplay

class CarHomeActivity : ComponentActivity() {

    companion object {
        private const val TAG = "CarHomeActivity"
    }

    private var touchIndicator: TouchIndicatorState? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val indicator =
            if (resources.getBoolean(R.bool.is_touch_indicator_enabled)) TouchIndicatorState()
            else null
        touchIndicator = indicator
        setContent {
            val apps by
                remember { phoneApps(applicationContext) }.collectAsStateWithLifecycle(emptyList())
            val isDrawerOpen by HomeState.isDrawerOpen.collectAsState()
            val isHomeShown by HomeState.isHomeShown.collectAsState()
            val carDisplay by CarDisplay.state.collectAsStateWithLifecycle()
            val contentInsets =
                carDisplay?.takeIf { it.id == display.displayId }?.contentInsets ?: Insets.NONE
            CarTheme {
                AnimatedVisibility(
                    visible = isDrawerOpen,
                    modifier = Modifier.fillMaxSize().padding(contentPadding(contentInsets)),
                    enter = fadeIn(),
                    exit = if (isHomeShown) fadeOut() else ExitTransition.None,
                ) {
                    AppDrawer(apps, onAppClick = ::launch, modifier = Modifier.fillMaxSize())
                }
                indicator?.let { TouchIndicator(it) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        HomeState.onHomeShown(true)
    }

    override fun onPause() {
        HomeState.onHomeShown(false)
        super.onPause()
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        touchIndicator?.onTouch(event)
        return super.dispatchTouchEvent(event)
    }

    private fun launch(app: PhoneApp) {
        val intent =
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(app.component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "${app.component} is no longer installed", e)
        } catch (e: SecurityException) {
            Log.w(TAG, "${app.component} refused to launch", e)
        }
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
