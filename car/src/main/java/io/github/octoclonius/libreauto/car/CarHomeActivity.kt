package io.github.octoclonius.libreauto.car

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.octoclonius.libreauto.car.apps.AppDrawer
import io.github.octoclonius.libreauto.car.apps.PhoneApp
import io.github.octoclonius.libreauto.car.apps.phoneApps
import io.github.octoclonius.libreauto.car.home.HomeState
import io.github.octoclonius.libreauto.car.touch.TouchIndicator
import io.github.octoclonius.libreauto.car.touch.TouchIndicatorState
import io.github.octoclonius.libreauto.car.ui.ProvideCarDensity
import io.github.octoclonius.libreauto.car.ui.theme.CarTheme
import io.github.octoclonius.libreauto.session.video.CarDisplay
import kotlinx.coroutines.flow.first

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
            val shownDisplay = carDisplay?.takeIf { it.id == display.displayId }
            ProvideCarDensity(shownDisplay?.carDensity) {
                CarTheme {
                    val drawerAlpha by
                        animateFloatAsState(
                            targetValue = if (isDrawerOpen) 1f else 0f,
                            animationSpec =
                                if (isDrawerOpen || isHomeShown) {
                                    spring(stiffness = Spring.StiffnessMediumLow)
                                } else {
                                    snap()
                                },
                        )
                    val drawerGrid = rememberLazyGridState()
                    LaunchedEffect(isDrawerOpen) {
                        if (!isDrawerOpen) {
                            snapshotFlow { drawerAlpha }.first { it == 0f }
                            drawerGrid.scrollToItem(0)
                        }
                    }
                    AppDrawer(
                        apps,
                        isEnabled = isDrawerOpen,
                        onAppClick = ::launch,
                        modifier = Modifier.fillMaxSize().graphicsLayer { alpha = drawerAlpha },
                        state = drawerGrid,
                    )
                    indicator?.let { TouchIndicator(it) }
                }
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
}
