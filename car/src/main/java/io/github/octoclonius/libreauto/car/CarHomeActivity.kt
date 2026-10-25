package io.github.octoclonius.libreauto.car

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.octoclonius.libreauto.car.apps.AppDrawer
import io.github.octoclonius.libreauto.car.apps.PhoneApp
import io.github.octoclonius.libreauto.car.apps.phoneApps
import io.github.octoclonius.libreauto.car.home.HomeApps
import io.github.octoclonius.libreauto.car.home.HomeDrag
import io.github.octoclonius.libreauto.car.home.HomeDragGhost
import io.github.octoclonius.libreauto.car.home.HomeScreen
import io.github.octoclonius.libreauto.car.home.HomeState
import io.github.octoclonius.libreauto.car.home.HomeTile
import io.github.octoclonius.libreauto.car.home.homeDrag
import io.github.octoclonius.libreauto.car.home.homeTiles
import io.github.octoclonius.libreauto.car.touch.TouchIndicator
import io.github.octoclonius.libreauto.car.touch.TouchIndicatorState
import io.github.octoclonius.libreauto.car.ui.ProvideCarDensity
import io.github.octoclonius.libreauto.car.ui.RetainedVisibility
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
        HomeApps.load(applicationContext)
        setContent {
            val apps by
                remember { phoneApps(applicationContext) }.collectAsStateWithLifecycle(emptyList())
            val isDrawerOpen by HomeState.isDrawerOpen.collectAsState()
            val homeComponents by HomeApps.components.collectAsState()
            val carDisplay by CarDisplay.state.collectAsStateWithLifecycle()
            val shownDisplay = carDisplay?.takeIf { it.id == display.displayId }
            ProvideCarDensity(shownDisplay?.carDensity) {
                CarTheme {
                    val drawerGrid = rememberLazyGridState()
                    LaunchedEffect(isDrawerOpen) {
                        if (!isDrawerOpen) drawerGrid.scrollToItem(0)
                    }
                    val drag = remember { HomeDrag() }
                    val tiles = remember(apps, homeComponents) { homeTiles(apps, homeComponents) }
                    Box(
                        Modifier.fillMaxSize()
                            .homeDrag(
                                drag,
                                onDrop = { app, from, cell, isRemoved ->
                                    drop(app, from, cell, isRemoved, tiles)
                                },
                            )
                    ) {
                        HomeScreen(
                            tiles,
                            drag,
                            HomeState.firstPageRequests,
                            onAppClick = ::launch,
                            modifier = Modifier.fillMaxSize(),
                        )
                        RetainedVisibility(isDrawerOpen, Modifier.fillMaxSize()) {
                            AppDrawer(
                                apps,
                                onAppClick = ::launch,
                                onAppLongClick = { drag.lift(it, null) },
                                modifier =
                                    Modifier.fillMaxSize().alpha(if (drag.isDragging) 0f else 1f),
                                state = drawerGrid,
                            )
                        }
                        HomeDragGhost(drag)
                    }
                    indicator?.let { TouchIndicator(it) }
                }
            }
        }
    }

    override fun onPause() {
        HomeState.onHomeHidden()
        super.onPause()
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        touchIndicator?.onTouch(event)
        return super.dispatchTouchEvent(event)
    }

    private fun drop(
        app: PhoneApp,
        from: Int?,
        cell: Int?,
        isRemoved: Boolean,
        tiles: List<HomeTile>,
    ) {
        HomeState.closeDrawer()
        when {
            isRemoved && from != null -> HomeApps.remove(from)
            cell == null -> {}
            else -> {
                val to = tiles.getOrNull(cell)?.index ?: HomeApps.components.value.size
                if (from != null) HomeApps.move(from, to) else HomeApps.add(app.component, to)
            }
        }
    }

    private fun launch(app: PhoneApp) {
        val intent =
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(app.component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "${app.component} is no longer installed", e)
        } catch (e: SecurityException) {
            Log.w(TAG, "${app.component} refused to launch", e)
        }
    }
}
