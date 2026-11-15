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
import io.github.octoclonius.libreauto.car.R
import io.github.octoclonius.libreauto.car.apps.carDensities
import io.github.octoclonius.libreauto.car.designsystem.theme.CarTheme
import io.github.octoclonius.libreauto.projection.CarSession
import io.github.octoclonius.libreauto.projection.calls.PhoneCall
import io.github.octoclonius.libreauto.projection.display.RailDisplayState
import io.github.octoclonius.libreauto.projection.tasks.CarTask
import kotlinx.coroutines.flow.StateFlow

internal class RailWindow(
    context: Context,
    display: Display,
    private val rail: RailDisplayState,
    private val session: CarSession,
    private val state: RailState,
    private val actions: RailActions,
    private val order: StateFlow<List<CarTask>>,
    private val calls: StateFlow<List<PhoneCall>>,
    private val isCallScreenShown: StateFlow<Boolean>,
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
                setContent {
                    CarTheme {
                        RailContent(
                            rail,
                            session,
                            state,
                            actions,
                            order,
                            calls,
                            isCallScreenShown,
                            onShownCount,
                        )
                    }
                }
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
    session: CarSession,
    state: RailState,
    actions: RailActions,
    order: StateFlow<List<CarTask>>,
    calls: StateFlow<List<PhoneCall>>,
    isCallScreenShown: StateFlow<Boolean>,
    onShownCount: (Int) -> Unit,
) {
    val context = LocalContext.current
    val apps by
        remember(context, order) {
                railApps(context, order, carDensities(session.displays.car), RAIL_ICON_SIZE)
            }
            .collectAsStateWithLifecycle(emptyList())
    val closingTaskId by state.closingTaskId.collectAsStateWithLifecycle()
    val closedTaskIds by state.closedTaskIds.collectAsStateWithLifecycle()
    val tasks by session.tasks.state.collectAsStateWithLifecycle()
    val phoneCalls by calls.collectAsStateWithLifecycle()
    val callScreenShown by isCallScreenShown.collectAsStateWithLifecycle()
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
        isDriverOnRight = session.car.isDriverOnRight,
        carStatusBar = session.car.statusBar,
        isCallUnderWay = isCallUnderWay(phoneCalls),
        contentPadding = padding,
        apps = apps.filter { it.task.taskId !in closedTaskIds },
        closingTaskId = closingTaskId,
        isAppInFront = tasks.frontTaskId != null || callScreenShown,
        onButtonClick = actions::onAppsButton,
        onAppClick = { actions.onAppClick(it.task) },
        onAppLongClick = { actions.onAppLongClick(it.task) },
        onAppClose = { actions.onAppClose(it.task) },
        onCancelClose = { state.cancelClosing() },
        onStatusClick = actions::onStatusClick,
        onShownCount = onShownCount,
        modifier = Modifier.fillMaxSize(),
    )
}
