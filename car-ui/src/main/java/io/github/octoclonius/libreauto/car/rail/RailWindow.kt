package io.github.octoclonius.libreauto.car.rail

import android.content.Context
import android.os.Bundle
import android.view.Display
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.ComponentDialog
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.octoclonius.libreauto.car.CarUi
import io.github.octoclonius.libreauto.car.R
import io.github.octoclonius.libreauto.car.apps.carDensities
import io.github.octoclonius.libreauto.car.ui.theme.CarTheme
import io.github.octoclonius.libreauto.projection.display.RailDisplayState
import io.github.octoclonius.libreauto.projection.tasks.CarTask
import kotlinx.coroutines.flow.StateFlow

internal class RailWindow(
    context: Context,
    display: Display,
    private val rail: RailDisplayState,
    private val carUi: CarUi,
    private val order: StateFlow<List<CarTask>>,
    private val onShownCount: (Int) -> Unit,
) :
    ComponentDialog(
        context
            .createDisplayContext(display)
            .createWindowContext(WindowManager.LayoutParams.TYPE_PRIVATE_PRESENTATION, null),
        R.style.Theme_LibreAuto_Car_Rail,
    ) {

    init {
        window?.setType(WindowManager.LayoutParams.TYPE_PRIVATE_PRESENTATION)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(
            ComposeView(context).apply {
                setContent { CarTheme { RailContent(rail, carUi, order, onShownCount) } }
            }
        )
        window?.run {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }
    }
}

@Composable
private fun RailContent(
    rail: RailDisplayState,
    carUi: CarUi,
    order: StateFlow<List<CarTask>>,
    onShownCount: (Int) -> Unit,
) {
    val context = LocalContext.current
    val apps by
        remember(context, order) {
                railApps(context, order, carDensities(carUi.session.displays.car), RAIL_ICON_SIZE)
            }
            .collectAsStateWithLifecycle(emptyList())
    val closingTaskId by carUi.rail.closingTaskId.collectAsStateWithLifecycle()
    val closedTaskIds by carUi.rail.closedTaskIds.collectAsStateWithLifecycle()
    val tasks by carUi.session.tasks.state.collectAsStateWithLifecycle()
    val navigation = carUi.navigation
    val insets = rail.contentInsets
    val padding =
        with(LocalDensity.current) {
            PaddingValues.Absolute(
                left = insets.left.toDp(),
                top = insets.top.toDp(),
                right = insets.right.toDp(),
                bottom = insets.bottom.toDp(),
            )
        }
    Rail(
        side = rail.side,
        isDriverOnRight = carUi.session.car.isDriverOnRight,
        carStatusBar = carUi.session.car.statusBar,
        contentPadding = padding,
        apps = apps.filter { it.task.taskId !in closedTaskIds },
        closingTaskId = closingTaskId,
        isAppInFront = tasks.frontTaskId != null,
        onButtonClick = navigation::onAppsButton,
        onAppClick = { navigation.onRailAppClick(it.task) },
        onAppLongClick = { navigation.onRailAppLongClick(it.task) },
        onAppClose = { navigation.onRailAppClose(it.task) },
        onCancelClose = { carUi.rail.cancelClosing() },
        onStatusClick = navigation::onRailStatusClick,
        onShownCount = onShownCount,
        modifier = Modifier.fillMaxSize(),
    )
}
