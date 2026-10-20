package io.github.octoclonius.libreauto.car.rail

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.octoclonius.libreauto.car.R
import io.github.octoclonius.libreauto.session.CarStatusBar

val RAIL_THICKNESS = 80.dp
private val RAIL_PADDING = 12.dp
private val STATUS_SPACING = 4.dp
private val STATUS_ICON_SIZE = 24.dp
private val APPS_ICON_SIZE = 40.dp
private const val DISABLED_ALPHA = 0.38f
private const val FULL_PERCENT = 100

private val WIFI_ICONS =
    listOf(
        R.drawable.ic_wifi_0,
        R.drawable.ic_wifi_1,
        R.drawable.ic_wifi_2,
        R.drawable.ic_wifi_3,
        R.drawable.ic_wifi_4,
    )

private class BatteryIcons(val levels: List<Int>, @param:DrawableRes val charging: Int)

private val HORIZONTAL_BATTERY =
    BatteryIcons(
        levels =
            listOf(
                R.drawable.ic_battery_horiz_0,
                R.drawable.ic_battery_horiz_1,
                R.drawable.ic_battery_horiz_2,
                R.drawable.ic_battery_horiz_3,
                R.drawable.ic_battery_horiz_4,
                R.drawable.ic_battery_horiz_5,
                R.drawable.ic_battery_horiz_6,
                R.drawable.ic_battery_horiz_full,
            ),
        charging = R.drawable.ic_battery_horiz_charging,
    )

private val VERTICAL_BATTERY =
    BatteryIcons(
        levels =
            listOf(
                R.drawable.ic_battery_vert_0,
                R.drawable.ic_battery_vert_1,
                R.drawable.ic_battery_vert_2,
                R.drawable.ic_battery_vert_3,
                R.drawable.ic_battery_vert_4,
                R.drawable.ic_battery_vert_5,
                R.drawable.ic_battery_vert_6,
                R.drawable.ic_battery_vert_full,
            ),
        charging = R.drawable.ic_battery_vert_charging,
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
    Surface(modifier) {
        if (side == RailSide.Bottom) BottomRail(status, isDriverOnRight) else SideRail(status)
    }
}

private data class RailStatus(
    val clock: String?,
    val wifiLevel: Int?,
    val cellular: Cellular?,
    val battery: BatteryState?,
)

@Composable
private fun SideRail(status: RailStatus) {
    Column(
        Modifier.fillMaxSize().padding(top = RAIL_PADDING),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(STATUS_SPACING),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            status.clock?.let { Clock(it) }
            Row {
                status.wifiLevel?.let { Wifi(it) }
                status.cellular?.let { CellularIcon(it) }
            }
            status.battery?.let { Battery(it, HORIZONTAL_BATTERY) }
        }
        Spacer(Modifier.weight(1f))
        AppsCell()
    }
}

@Composable
private fun BottomRail(status: RailStatus, isDriverOnRight: Boolean) {
    Row(
        Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.Absolute.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isDriverOnRight) {
            BottomStatus(status, Modifier.padding(horizontal = RAIL_PADDING))
            AppsCell()
        } else {
            AppsCell()
            BottomStatus(status, Modifier.padding(horizontal = RAIL_PADDING))
        }
    }
}

@Composable
private fun BottomStatus(status: RailStatus, modifier: Modifier = Modifier) {
    Column(
        modifier,
        verticalArrangement = Arrangement.spacedBy(STATUS_SPACING),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            status.wifiLevel?.let { Wifi(it) }
            status.cellular?.let { CellularIcon(it) }
            status.battery?.let { Battery(it, VERTICAL_BATTERY) }
        }
        status.clock?.let { Clock(it) }
    }
}

@Composable
private fun Clock(time: String) {
    Text(time, style = MaterialTheme.typography.titleLarge, maxLines = 1)
}

@Composable
private fun Wifi(level: Int) {
    StatusIcon(WIFI_ICONS[level.coerceIn(0, MAX_SIGNAL_LEVEL)])
}

@Composable
private fun CellularIcon(cellular: Cellular) {
    StatusIcon(
        when (cellular) {
            Cellular.AirplaneMode -> R.drawable.ic_airplane_mode
            Cellular.NoService -> R.drawable.ic_cellular_no_service
            is Cellular.Signal -> CELLULAR_ICONS[cellular.level.coerceIn(0, MAX_SIGNAL_LEVEL)]
        }
    )
}

@Composable
private fun Battery(battery: BatteryState, icons: BatteryIcons, modifier: Modifier = Modifier) {
    val level = battery.percent * icons.levels.lastIndex / FULL_PERCENT
    StatusIcon(
        if (battery.isPluggedIn) icons.charging
        else icons.levels[level.coerceIn(0, icons.levels.lastIndex)],
        modifier,
    )
}

@Composable
private fun StatusIcon(@DrawableRes icon: Int, modifier: Modifier = Modifier) {
    Icon(
        painterResource(icon),
        contentDescription = null,
        modifier = modifier.size(STATUS_ICON_SIZE),
    )
}

@Composable
private fun AppsCell() {
    Box(Modifier.size(RAIL_THICKNESS), contentAlignment = Alignment.Center) {
        Icon(
            painterResource(R.drawable.ic_apps),
            contentDescription = stringResource(R.string.all_apps),
            modifier = Modifier.size(APPS_ICON_SIZE),
            tint = LocalContentColor.current.copy(alpha = DISABLED_ALPHA),
        )
    }
}
