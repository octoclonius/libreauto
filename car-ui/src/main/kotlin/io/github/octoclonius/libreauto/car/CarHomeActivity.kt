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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.octoclonius.libreauto.car.apps.AppDrawer
import io.github.octoclonius.libreauto.car.apps.PhoneApp
import io.github.octoclonius.libreauto.car.designsystem.component.RetainedVisibility
import io.github.octoclonius.libreauto.car.designsystem.theme.CarTheme
import io.github.octoclonius.libreauto.car.designsystem.theme.ProvideCarDensity
import io.github.octoclonius.libreauto.car.home.HomeAppsRepository
import io.github.octoclonius.libreauto.car.home.HomeDrag
import io.github.octoclonius.libreauto.car.home.HomeDragGhost
import io.github.octoclonius.libreauto.car.home.HomeScreen
import io.github.octoclonius.libreauto.car.home.HomeState
import io.github.octoclonius.libreauto.car.home.HomeTile
import io.github.octoclonius.libreauto.car.home.homeDrag
import io.github.octoclonius.libreauto.car.home.homeTiles
import io.github.octoclonius.libreauto.car.touch.TouchIndicator
import io.github.octoclonius.libreauto.car.touch.TouchIndicatorState

class CarHomeActivity : ComponentActivity() {

    companion object {
        private const val TAG = "CarHomeActivity"
    }

    private var touchIndicator: TouchIndicatorState? = null

    private val homeApps: HomeAppsRepository
        get() = carContainer.homeAppsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val indicator =
            if (resources.getBoolean(R.bool.is_touch_indicator_enabled)) TouchIndicatorState()
            else null
        touchIndicator = indicator
        setContent {
            val carUi by carContainer.carUiHolder.carUi.collectAsStateWithLifecycle()
            carUi?.let { CarHome(it, indicator) }
        }
    }

    override fun onPause() {
        carContainer.carUiHolder.carUi.value?.home?.onHomeHidden()
        super.onPause()
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        touchIndicator?.onTouch(event)
        return super.dispatchTouchEvent(event)
    }

    @Composable
    private fun CarHome(carUi: CarUi, indicator: TouchIndicatorState?) {
        val home = carUi.home
        val loadedApps by carContainer.phoneAppsRepository.sections.collectAsStateWithLifecycle()
        val apps = loadedApps.orEmpty()
        val isDrawerOpen by home.isDrawerOpen.collectAsState()
        val homeComponents by homeApps.components.collectAsState()
        val carDisplay by carUi.session.displays.car.state.collectAsStateWithLifecycle()
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
                                drop(home, app, from, cell, isRemoved, tiles)
                            },
                        )
                ) {
                    HomeScreen(
                        tiles,
                        drag,
                        home.firstPageRequests,
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

    private fun drop(
        home: HomeState,
        app: PhoneApp,
        from: Int?,
        cell: Int?,
        isRemoved: Boolean,
        tiles: List<HomeTile>,
    ) {
        home.closeDrawer()
        when {
            isRemoved && from != null -> homeApps.remove(from)
            cell == null -> {}
            else -> {
                val to = tiles.getOrNull(cell)?.index ?: homeApps.components.value.size
                if (from != null) homeApps.move(from, to) else homeApps.add(app.component, to)
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
