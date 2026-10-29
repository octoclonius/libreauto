package io.github.octoclonius.libreauto.car.apps

import android.graphics.drawable.Drawable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.octoclonius.libreauto.car.ui.theme.CarShapes
import io.github.octoclonius.libreauto.car.ui.theme.CarSpacing

data class AppTileSize(val tile: Dp, val icon: Dp, val labelSpacing: Dp, val label: TextUnit) {
    operator fun times(scale: Float): AppTileSize =
        AppTileSize(tile * scale, icon * scale, labelSpacing * scale, label * scale)
}

val DRAWER_TILE = AppTileSize(112.dp, 60.dp, 8.dp, 16.sp)
val HOME_TILE = AppTileSize(160.dp, 88.dp, 10.dp, 18.sp)

val TILE_GAP = CarSpacing.large
private val TILE_PADDING = CarSpacing.extraSmall

@Composable
fun AppTile(
    app: PhoneApp,
    size: AppTileSize,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    labelStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    labelLines: Int = 1,
) {
    Column(
        modifier
            .size(size.tile)
            .clip(CarShapes.largeIncreased)
            .combinedClickable(onLongClick = onLongClick, onClick = onClick)
            .padding(horizontal = TILE_PADDING),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppIcon(app.icon, size.icon)
        Spacer(Modifier.height(size.labelSpacing))
        Text(
            app.label,
            style = labelStyle.copy(fontSize = size.label),
            minLines = labelLines,
            maxLines = labelLines,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun AppIcon(icon: Drawable, iconSize: Dp, modifier: Modifier = Modifier) {
    Spacer(
        modifier.size(iconSize).drawBehind {
            drawIntoCanvas { canvas ->
                icon.setBounds(0, 0, size.width.toInt(), size.height.toInt())
                icon.draw(canvas.nativeCanvas)
            }
        }
    )
}
