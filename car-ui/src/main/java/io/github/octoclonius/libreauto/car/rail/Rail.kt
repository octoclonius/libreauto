package io.github.octoclonius.libreauto.car.rail

import android.telephony.CellSignalStrength
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.octoclonius.libreauto.car.R
import io.github.octoclonius.libreauto.car.apps.AppIcon
import io.github.octoclonius.libreauto.car.ui.component.BatteryBadge
import io.github.octoclonius.libreauto.car.ui.component.BatteryIndicator
import io.github.octoclonius.libreauto.car.ui.component.GlyphText
import io.github.octoclonius.libreauto.car.ui.component.SquircleButton
import io.github.octoclonius.libreauto.car.ui.theme.CarShapes
import io.github.octoclonius.libreauto.car.ui.theme.CarSpacing
import io.github.octoclonius.libreauto.session.CarStatusBar
import io.github.octoclonius.libreauto.session.display.RAIL_THICKNESS_DP
import io.github.octoclonius.libreauto.session.display.RailSide

internal val RAIL_ICON_SIZE = 48.dp
private val RAIL_THICKNESS = RAIL_THICKNESS_DP.dp
private val RAIL_PADDING = CarSpacing.medium
private val SIDE_SLOT = DpSize(RAIL_THICKNESS, RAIL_ICON_SIZE)
private val BOTTOM_SLOT = DpSize(RAIL_ICON_SIZE, RAIL_THICKNESS)
private val SIDE_SLOT_GAP = CarSpacing.large
private val BOTTOM_SLOT_GAP = CarSpacing.extraLarge
private val CLOSE_ICON_SIZE = 28.dp
private const val PRESSED_SCALE = 1.1f
private val STATUS_SPACING = CarSpacing.extraSmall
private val STATUS_ROW_SPACING = CarSpacing.small
private val STATUS_ICON_SIZE = 24.dp
private val BUTTON_ICON_SIZE = 40.dp
private val BATTERY_HEIGHT = 16.dp
private val CLOCK_HEIGHT = 16.dp
private val ICON_PADDING = 2.dp
private val FEEDBACK_INSET = CarSpacing.extraSmall
private val SIGNAL_LEVELS =
    CellSignalStrength.SIGNAL_STRENGTH_NONE_OR_UNKNOWN..CellSignalStrength.SIGNAL_STRENGTH_GREAT

private val WIFI_ICONS =
    listOf(
        R.drawable.ic_wifi_0,
        R.drawable.ic_wifi_1,
        R.drawable.ic_wifi_2,
        R.drawable.ic_wifi_3,
        R.drawable.ic_wifi_4,
    )

private val CELLULAR_ICONS =
    listOf(
        R.drawable.ic_cellular_0,
        R.drawable.ic_cellular_1,
        R.drawable.ic_cellular_2,
        R.drawable.ic_cellular_3,
        R.drawable.ic_cellular_4,
    )

@Composable
fun Rail(
    side: RailSide,
    isDriverOnRight: Boolean,
    carStatusBar: CarStatusBar,
    contentPadding: PaddingValues,
    apps: List<RailApp>,
    closingTaskId: Int?,
    isAppInFront: Boolean,
    onButtonClick: () -> Unit,
    onAppClick: (RailApp) -> Unit,
    onAppLongClick: (RailApp) -> Unit,
    onAppClose: (RailApp) -> Unit,
    onCancelClose: () -> Unit,
    onStatusClick: () -> Unit,
    onShownCount: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val clock by remember(context) { phoneClock(context) }.collectAsStateWithLifecycle(null)
    val battery by remember(context) { phoneBattery(context) }.collectAsStateWithLifecycle(null)
    val connectivity by
        remember(context) { phoneConnectivity(context) }
            .collectAsStateWithLifecycle(Connectivity(wifiLevel = null, cellular = null))
    val status =
        RailStatus(
            clock = clock.takeUnless { carStatusBar.isClockShown },
            wifiLevel = connectivity.wifiLevel,
            cellular = connectivity.cellular.takeUnless { carStatusBar.isPhoneSignalShown },
            battery = battery.takeUnless { carStatusBar.isBatteryShown },
        )
    var closeBounds by remember { mutableStateOf<Rect?>(null) }
    val isClosing by rememberUpdatedState(closingTaskId != null)
    val slots: @Composable (Int, DpSize) -> Unit = { count, size ->
        apps.take(count).forEach { app ->
            AppSlot(
                app,
                isClosing = app.task.taskId == closingTaskId,
                size = size,
                onClick = { onAppClick(app) },
                onLongClick = { onAppLongClick(app) },
                onClose = { onAppClose(app) },
                onClosePlaced = { closeBounds = it },
            )
        }
    }
    val button: @Composable (Modifier) -> Unit = {
        RailButton(isAppInFront, onButtonClick, it)
    }
    Surface(modifier.cancelClosingOnTouch({ isClosing }, { closeBounds }, onCancelClose)) {
        Box(Modifier.padding(contentPadding)) {
            if (side == RailSide.Bottom) {
                BottomRail(
                    status,
                    isDriverOnRight,
                    apps.size,
                    onStatusClick,
                    onShownCount,
                    slots,
                    button,
                )
            } else {
                SideRail(status, apps.size, onStatusClick, onShownCount, slots, button)
            }
        }
    }
}

private data class RailStatus(
    val clock: String?,
    val wifiLevel: Int?,
    val cellular: Cellular?,
    val battery: BatteryState?,
)

private fun Modifier.cancelClosingOnTouch(
    isClosing: () -> Boolean,
    closeBounds: () -> Rect?,
    onCancel: () -> Unit,
): Modifier =
    pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (!isClosing() || closeBounds()?.contains(down.position) == true) {
                return@awaitEachGesture
            }
            onCancel()
            down.consume()
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                event.changes.forEach { it.consume() }
            } while (event.changes.any { it.pressed })
        }
    }

@Composable
private fun SideRail(
    status: RailStatus,
    appCount: Int,
    onStatusClick: () -> Unit,
    onShownCount: (Int) -> Unit,
    slots: @Composable (Int, DpSize) -> Unit,
    button: @Composable (Modifier) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        var statusHeight by remember { mutableStateOf(0.dp) }
        val capacity =
            ((maxHeight - statusHeight - RAIL_THICKNESS + SIDE_SLOT_GAP) /
                    (SIDE_SLOT.height + SIDE_SLOT_GAP))
                .toInt()
                .coerceIn(0, MAX_RAIL_APPS)
        LaunchedEffect(capacity) { onShownCount(capacity) }
        val count = minOf(capacity, appCount)
        val slotsHeight = slotsLength(count, SIDE_SLOT.height, SIDE_SLOT_GAP)
        val top =
            centered(maxHeight, slotsHeight, statusHeight, maxHeight - RAIL_THICKNESS - slotsHeight)
        StatusButton(
            onStatusClick,
            Modifier.align(Alignment.TopCenter).fillMaxWidth().onSizeChanged {
                statusHeight = with(density) { it.height.toDp() }
            },
        ) {
            Column(
                Modifier.padding(vertical = RAIL_PADDING),
                verticalArrangement = Arrangement.spacedBy(STATUS_ROW_SPACING),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                status.clock?.let { Clock(it) }
                Row(horizontalArrangement = Arrangement.spacedBy(STATUS_SPACING)) {
                    status.wifiLevel?.let { Wifi(it) }
                    status.cellular?.let { CellularIcon(it) }
                }
                status.battery?.let { Battery(it, isBodyCentered = true) }
            }
        }
        Column(
            Modifier.align(Alignment.TopCenter).offset(y = top),
            verticalArrangement = Arrangement.spacedBy(SIDE_SLOT_GAP),
        ) {
            slots(count, SIDE_SLOT)
        }
        button(Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun BottomRail(
    status: RailStatus,
    isDriverOnRight: Boolean,
    appCount: Int,
    onStatusClick: () -> Unit,
    onShownCount: (Int) -> Unit,
    slots: @Composable (Int, DpSize) -> Unit,
    button: @Composable (Modifier) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        var statusWidth by remember { mutableStateOf(0.dp) }
        val capacity =
            ((maxWidth - statusWidth - RAIL_THICKNESS + BOTTOM_SLOT_GAP) /
                    (BOTTOM_SLOT.width + BOTTOM_SLOT_GAP))
                .toInt()
                .coerceIn(0, MAX_RAIL_APPS)
        LaunchedEffect(capacity) { onShownCount(capacity) }
        val count = minOf(capacity, appCount)
        val slotsWidth = slotsLength(count, BOTTOM_SLOT.width, BOTTOM_SLOT_GAP)
        val leftEdge = if (isDriverOnRight) statusWidth else RAIL_THICKNESS
        val rightEdge = maxWidth - if (isDriverOnRight) RAIL_THICKNESS else statusWidth
        val left = centered(maxWidth, slotsWidth, leftEdge, rightEdge - slotsWidth)
        StatusButton(
            onStatusClick,
            Modifier.align(if (isDriverOnRight) Alignment.CenterStart else Alignment.CenterEnd)
                .fillMaxHeight()
                .onSizeChanged { statusWidth = with(density) { it.width.toDp() } },
        ) {
            BottomStatus(status, Modifier.padding(horizontal = RAIL_PADDING))
        }
        Row(
            Modifier.align(Alignment.CenterStart).offset(x = left),
            horizontalArrangement = Arrangement.spacedBy(BOTTOM_SLOT_GAP),
        ) {
            slots(count, BOTTOM_SLOT)
        }
        button(Modifier.align(if (isDriverOnRight) Alignment.CenterEnd else Alignment.CenterStart))
    }
}

@Composable
private fun StatusButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val description = stringResource(R.string.quick_settings)
    SquircleButton(
        onClick,
        FEEDBACK_INSET,
        CarShapes.largeIncreased,
        modifier.semantics { contentDescription = description },
    ) {
        content()
    }
}

private fun slotsLength(count: Int, slot: Dp, gap: Dp): Dp =
    if (count == 0) 0.dp else slot * count + gap * (count - 1)

private fun centered(length: Dp, content: Dp, min: Dp, max: Dp): Dp =
    ((length - content) / 2).coerceAtMost(max).coerceAtLeast(min)

@Composable
private fun BottomStatus(status: RailStatus, modifier: Modifier = Modifier) {
    Column(
        modifier,
        verticalArrangement = Arrangement.spacedBy(STATUS_ROW_SPACING),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(STATUS_SPACING),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            status.wifiLevel?.let { Wifi(it) }
            status.cellular?.let { CellularIcon(it) }
            status.battery?.let { Battery(it) }
        }
        status.clock?.let { Clock(it) }
    }
}

@Composable
private fun AppSlot(
    app: RailApp,
    isClosing: Boolean,
    size: DpSize,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onClose: () -> Unit,
    onClosePlaced: (Rect) -> Unit,
) {
    val description = if (isClosing) stringResource(R.string.close_app, app.label) else app.label
    val interactions = remember { MutableInteractionSource() }
    val isPressed by interactions.collectIsPressedAsState()
    Box(
        Modifier.size(size)
            .then(
                if (isClosing) Modifier.onGloballyPositioned { onClosePlaced(it.boundsInRoot()) }
                else Modifier
            )
            .combinedClickable(
                interactionSource = interactions,
                indication = null,
                onLongClick = onLongClick,
                onClick = if (isClosing) onClose else onClick,
            )
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(RAIL_ICON_SIZE).scale(if (isPressed) PRESSED_SCALE else 1f),
            contentAlignment = Alignment.Center,
        ) {
            if (isClosing) {
                Box(
                    Modifier.fillMaxSize().background(MaterialTheme.colorScheme.error, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(R.drawable.ic_close),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onError,
                        modifier = Modifier.size(CLOSE_ICON_SIZE),
                    )
                }
            } else {
                AppIcon(app.icon, RAIL_ICON_SIZE)
            }
        }
    }
}

@Composable
private fun RailButton(isAppInFront: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    SquircleButton(
        onClick,
        FEEDBACK_INSET,
        CarShapes.largeIncreased,
        modifier.size(RAIL_THICKNESS),
    ) {
        Icon(
            painterResource(if (isAppInFront) R.drawable.ic_home else R.drawable.ic_apps),
            contentDescription =
                stringResource(if (isAppInFront) R.string.home else R.string.all_apps),
            modifier = Modifier.size(BUTTON_ICON_SIZE),
        )
    }
}

@Composable
private fun Clock(time: String) {
    GlyphText(time, CLOCK_HEIGHT, Modifier.padding(vertical = ICON_PADDING))
}

@Composable
private fun Wifi(level: Int) {
    StatusIcon(WIFI_ICONS[level.coerceIn(SIGNAL_LEVELS)])
}

@Composable
private fun CellularIcon(cellular: Cellular) {
    StatusIcon(
        when (cellular) {
            Cellular.AirplaneMode -> R.drawable.ic_airplane_mode
            Cellular.NoService -> R.drawable.ic_cellular_no_service
            is Cellular.Signal -> CELLULAR_ICONS[cellular.level.coerceIn(SIGNAL_LEVELS)]
        }
    )
}

@Composable
private fun Battery(battery: BatteryState, isBodyCentered: Boolean = false) {
    Box(Modifier.height(STATUS_ICON_SIZE), contentAlignment = Alignment.Center) {
        BatteryIndicator(
            percent = battery.percent,
            badge =
                when {
                    battery.isPowerSaveMode -> BatteryBadge.PowerSave
                    battery.isChargingPaused -> BatteryBadge.ChargingPaused
                    battery.isPluggedIn -> BatteryBadge.Charging
                    else -> null
                },
            height = BATTERY_HEIGHT,
            isBodyCentered = isBodyCentered,
        )
    }
}

@Composable
private fun StatusIcon(@DrawableRes icon: Int, modifier: Modifier = Modifier) {
    Icon(
        painterResource(icon),
        contentDescription = null,
        modifier = modifier.size(STATUS_ICON_SIZE),
    )
}
