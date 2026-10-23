package io.github.octoclonius.libreauto.car.apps

import android.graphics.drawable.Drawable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val TILE_SIZE = 112.dp
private val TILE_CORNER = 18.dp
private val TILE_PADDING = 4.dp
private val GRID_SPACING = 16.dp
private val GRID_PADDING = 16.dp
internal val APP_ICON_SIZE = 60.dp
private val LABEL_SPACING = 8.dp
private val LABEL_SIZE = 16.sp

@Composable
fun AppDrawer(apps: List<PhoneApp>, onAppClick: (PhoneApp) -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier) {
        LazyVerticalGrid(
            columns = GridCells.FixedSize(TILE_SIZE),
            contentPadding = PaddingValues(GRID_PADDING),
            verticalArrangement = Arrangement.spacedBy(GRID_SPACING),
            horizontalArrangement =
                Arrangement.spacedBy(GRID_SPACING, Alignment.CenterHorizontally),
        ) {
            items(apps, key = { it.component.flattenToShortString() }) { app ->
                AppTile(app, onClick = { onAppClick(app) })
            }
        }
    }
}

@Composable
private fun AppTile(app: PhoneApp, onClick: () -> Unit) {
    Column(
        Modifier.size(TILE_SIZE)
            .clip(RoundedCornerShape(TILE_CORNER))
            .clickable(onClick = onClick)
            .padding(horizontal = TILE_PADDING),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppIcon(app.icon)
        Spacer(Modifier.height(LABEL_SPACING))
        Text(
            app.label,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = LABEL_SIZE),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun AppIcon(icon: Drawable) {
    Spacer(
        Modifier.size(APP_ICON_SIZE).drawBehind {
            drawIntoCanvas { canvas ->
                icon.setBounds(0, 0, size.width.toInt(), size.height.toInt())
                icon.draw(canvas.nativeCanvas)
            }
        }
    )
}
