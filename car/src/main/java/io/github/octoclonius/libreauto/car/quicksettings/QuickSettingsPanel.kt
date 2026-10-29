package io.github.octoclonius.libreauto.car.quicksettings

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import io.github.octoclonius.libreauto.car.R
import io.github.octoclonius.libreauto.car.ui.theme.CarAlpha
import io.github.octoclonius.libreauto.car.ui.theme.CarShapes
import io.github.octoclonius.libreauto.car.ui.theme.CarSpacing
import io.github.octoclonius.libreauto.session.settings.PhoneSettingsState
import io.github.octoclonius.libreauto.session.settings.QuickSetting
import io.github.octoclonius.libreauto.session.display.RailSide

private val PANEL_MARGIN = CarSpacing.medium
private val PANEL_PADDING = CarSpacing.large
private val TILE_SIZE = DpSize(176.dp, 72.dp)
private val TILE_SPACING = CarSpacing.medium
private val TILE_PADDING = CarSpacing.large
private val TILE_ICON_SIZE = 28.dp
private const val MAX_COLUMNS = 3

private class SettingTile(
    val setting: QuickSetting,
    @DrawableRes val icon: Int,
    @StringRes val label: Int,
    val isOn: Boolean?,
    val page: String,
)

@Composable
internal fun QuickSettingsPanel(
    settings: PhoneSettingsState?,
    railSide: RailSide?,
    isDriverOnRight: Boolean,
    onToggle: (QuickSetting, Boolean) -> Unit,
    onClose: () -> Unit,
    onShowPage: (PendingIntent) -> Unit,
) {
    val alignment =
        when (railSide) {
            RailSide.Right -> Alignment.TopEnd
            RailSide.Bottom -> if (isDriverOnRight) Alignment.BottomStart else Alignment.BottomEnd
            else -> Alignment.TopStart
        }
    BoxWithConstraints(
        Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = CarAlpha.SCRIM))
            .clickable(
                interactionSource = null,
                indication = null,
                onClick = onClose,
            )
    ) {
        val columns =
            ((maxWidth - PANEL_MARGIN * 2 - PANEL_PADDING * 2 + TILE_SPACING) /
                    (TILE_SIZE.width + TILE_SPACING))
                .toInt()
                .coerceIn(1, MAX_COLUMNS)
        settings?.let {
            Panel(
                tiles(it),
                columns,
                onToggle,
                onShowPage,
                Modifier.align(alignment).padding(PANEL_MARGIN),
            )
        }
    }
}

private fun tiles(settings: PhoneSettingsState): List<SettingTile> =
    listOf(
        SettingTile(
            QuickSetting.Wifi,
            R.drawable.ic_wifi_4,
            R.string.wifi,
            settings.isWifiOn,
            Settings.ACTION_WIFI_SETTINGS,
        ),
        SettingTile(
            QuickSetting.Bluetooth,
            R.drawable.ic_bluetooth,
            R.string.bluetooth,
            settings.isBluetoothOn,
            Settings.ACTION_BLUETOOTH_SETTINGS,
        ),
        SettingTile(
            QuickSetting.MobileData,
            R.drawable.ic_mobile_data,
            R.string.mobile_data,
            settings.isMobileDataOn,
            Settings.ACTION_NETWORK_OPERATOR_SETTINGS,
        ),
        SettingTile(
            QuickSetting.AirplaneMode,
            R.drawable.ic_airplane_mode,
            R.string.airplane_mode,
            settings.isAirplaneModeOn,
            Settings.ACTION_AIRPLANE_MODE_SETTINGS,
        ),
        SettingTile(
            QuickSetting.DoNotDisturb,
            R.drawable.ic_do_not_disturb,
            R.string.do_not_disturb,
            settings.isDoNotDisturbOn,
            Settings.ACTION_ZEN_MODE_PRIORITY_SETTINGS,
        ),
        SettingTile(
            QuickSetting.Location,
            R.drawable.ic_location,
            R.string.location,
            settings.isLocationOn,
            Settings.ACTION_LOCATION_SOURCE_SETTINGS,
        ),
    )

@Composable
private fun Panel(
    tiles: List<SettingTile>,
    columns: Int,
    onToggle: (QuickSetting, Boolean) -> Unit,
    onShowPage: (PendingIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier,
        shape = CarShapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            Modifier.padding(PANEL_PADDING),
            verticalArrangement = Arrangement.spacedBy(TILE_SPACING),
        ) {
            tiles.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(TILE_SPACING)) {
                    row.forEach { Tile(it, onToggle, onShowPage) }
                }
            }
        }
    }
}

@Composable
private fun Tile(
    tile: SettingTile,
    onToggle: (QuickSetting, Boolean) -> Unit,
    onShowPage: (PendingIntent) -> Unit,
) {
    val context = LocalContext.current
    val isOn = tile.isOn == true
    Surface(
        shape = CarShapes.largeIncreased,
        color =
            if (isOn) MaterialTheme.colorScheme.primaryFixed
            else MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor =
            if (isOn) MaterialTheme.colorScheme.onPrimaryFixed
            else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.size(TILE_SIZE).alpha(if (tile.isOn == null) CarAlpha.DISABLED else 1f),
    ) {
        Row(
            Modifier.fillMaxSize()
                .combinedClickable(
                    enabled = tile.isOn != null,
                    role = Role.Switch,
                    onLongClick = { onShowPage(settingsPage(context, tile)) },
                    onClick = { onToggle(tile.setting, !isOn) },
                )
                .padding(horizontal = TILE_PADDING),
            horizontalArrangement = Arrangement.spacedBy(TILE_PADDING),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painterResource(tile.icon),
                contentDescription = null,
                modifier = Modifier.size(TILE_ICON_SIZE),
            )
            Text(
                stringResource(tile.label),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun settingsPage(context: Context, tile: SettingTile): PendingIntent =
    PendingIntent.getActivity(
        context,
        tile.setting.ordinal,
        Intent(tile.page)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION),
        PendingIntent.FLAG_IMMUTABLE,
    )
