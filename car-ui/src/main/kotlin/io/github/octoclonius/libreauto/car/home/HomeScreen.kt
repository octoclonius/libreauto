package io.github.octoclonius.libreauto.car.home

import android.content.ComponentName
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.octoclonius.libreauto.car.R
import io.github.octoclonius.libreauto.car.apps.AppIcon
import io.github.octoclonius.libreauto.car.apps.AppSection
import io.github.octoclonius.libreauto.car.apps.AppTile
import io.github.octoclonius.libreauto.car.apps.AppTileSize
import io.github.octoclonius.libreauto.car.apps.HOME_TILE
import io.github.octoclonius.libreauto.car.apps.PhoneApp
import io.github.octoclonius.libreauto.car.apps.TILE_GAP
import io.github.octoclonius.libreauto.car.designsystem.theme.CarAlpha
import io.github.octoclonius.libreauto.car.designsystem.theme.CarShapes
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

private const val MIN_ROWS = 2
private val INDICATOR_HEIGHT = 36.dp
private val DOT_TARGET = 30.dp
private val DOT_SIZE = 8.dp
private val CURRENT_DOT_SIZE = 10.dp
private val REMOVE_CIRCLE = 64.dp
private val REMOVE_ICON_SIZE = 24.dp
private const val REMOVE_FIELD_SCALE = 1.25f
private const val REMOVED_ICON_FRACTION = 0.85f
private const val REMOVE_SCRIM_ALPHA = 0.7f
private const val REMOVE_SCRIM_FRACTION = 0.5f
private val EDGE_WIDTH = 40.dp
private val TARGET_BORDER = 2.dp
private const val EDGE_FLIP_MILLIS = 550L
private const val LIFTED_ALPHA = 0.3f
private const val INACTIVE_DOT_ALPHA = 0.38f
private const val GHOST_SCALE = 1.1f
private const val LABEL_SHADOW_BLUR = 6f
private const val LABEL_LINES = 2

class HomeTile(val app: PhoneApp, val index: Int)

fun homeTiles(sections: List<AppSection>, components: List<ComponentName>): List<HomeTile> {
    val apps = sections.flatMap { it.apps }.associateBy { it.component }
    return components.mapIndexedNotNull { index, component ->
        apps[component]?.let { HomeTile(it, index) }
    }
}

@Composable
fun HomeScreen(
    tiles: List<HomeTile>,
    drag: HomeDrag,
    firstPageRequests: Flow<Unit>,
    onAppClick: (PhoneApp) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier) {
        val gridHeight = maxHeight - INDICATOR_HEIGHT * 2
        val columns = ((maxWidth - TILE_GAP) / (HOME_TILE.tile + TILE_GAP)).toInt().coerceAtLeast(1)
        val rows =
            ((gridHeight + TILE_GAP) / (HOME_TILE.tile + TILE_GAP)).toInt().coerceAtLeast(MIN_ROWS)
        val fit =
            minOf(
                1f,
                (gridHeight - TILE_GAP * (rows - 1)) / (HOME_TILE.tile * rows),
                (maxWidth - TILE_GAP * (columns + 1)) / (HOME_TILE.tile * columns),
            )
        val size = HOME_TILE * fit
        val perPage = columns * rows
        val hole by
            remember(drag) {
                derivedStateOf {
                    if (drag.isDragging && !drag.isOverRemove()) drag.targetCell() else null
                }
            }
        val isRemoving by remember(drag) { derivedStateOf { drag.isOverRemove() } }
        val entries =
            remember(tiles, hole, isRemoving) { preview(tiles, drag.from, hole, isRemoving) }
        val slots = tiles.size + if (drag.isDragging) 1 else 0
        val pageCount = ceil(slots.toFloat() / perPage).toInt().coerceAtLeast(1)
        val pager = rememberPagerState { pageCount }
        var bounds by remember { mutableStateOf(Rect.Zero) }
        val edge = with(LocalDensity.current) { EDGE_WIDTH.toPx() }
        LaunchedEffect(pager) { firstPageRequests.collect { pager.scrollToPage(0) } }
        LaunchedEffect(drag.isDragging, pageCount) {
            if (!drag.isDragging) return@LaunchedEffect
            snapshotFlow { edgeDirection(drag.position, bounds, edge) }
                .collectLatest { direction ->
                    while (direction != 0) {
                        delay(EDGE_FLIP_MILLIS)
                        val next = pager.currentPage + direction
                        if (next !in 0 until pageCount) break
                        pager.scrollToPage(next)
                    }
                }
        }
        Box(Modifier.fillMaxSize().onGloballyPositioned { bounds = it.boundsInRoot() }) {
            HorizontalPager(
                pager,
                Modifier.fillMaxSize(),
                userScrollEnabled = !drag.isDragging,
            ) { page ->
                HomePage(entries, hole, page * perPage, columns, rows, size, drag, onAppClick)
            }
            when {
                drag.isDragging && drag.from != null -> {
                    Box(
                        Modifier.fillMaxWidth()
                            .fillMaxHeight(REMOVE_SCRIM_FRACTION)
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color.Transparent,
                                        MaterialTheme.colorScheme.scrim.copy(
                                            alpha = REMOVE_SCRIM_ALPHA
                                        ),
                                    )
                                )
                            )
                    )
                    RemoveTarget(drag, Modifier.align(Alignment.BottomCenter))
                }
                !drag.isDragging && pageCount > 1 ->
                    PageDots(pager, pageCount, Modifier.align(Alignment.BottomCenter))
            }
        }
    }
}

@Composable
fun HomeDragGhost(drag: HomeDrag) {
    val app = drag.app ?: return
    val center = drag.removeCenter?.takeIf { drag.isOverRemove() }
    val iconSize = if (center != null) REMOVE_CIRCLE * REMOVED_ICON_FRACTION else HOME_TILE.icon
    val at = center ?: drag.position
    val half = with(LocalDensity.current) { iconSize.roundToPx() / 2 }
    AppIcon(
        app.icon,
        iconSize,
        Modifier.offset { IntOffset(at.x.roundToInt() - half, at.y.roundToInt() - half) }
            .scale(if (center != null) 1f else GHOST_SCALE),
    )
}

private fun preview(
    tiles: List<HomeTile>,
    from: Int?,
    hole: Int?,
    isRemoving: Boolean,
): List<HomeTile?> {
    if (hole == null && !isRemoving) return tiles
    val rest = tiles.filter { it.index != from }
    if (hole == null) return rest
    return rest.toMutableList<HomeTile?>().apply { add(hole.coerceAtMost(size), null) }
}

private fun edgeDirection(position: Offset, bounds: Rect, edge: Float): Int =
    when {
        !bounds.contains(position) -> 0
        position.x < bounds.left + edge -> -1
        position.x > bounds.right - edge -> 1
        else -> 0
    }

@Composable
private fun HomePage(
    entries: List<HomeTile?>,
    hole: Int?,
    first: Int,
    columns: Int,
    rows: Int,
    size: AppTileSize,
    drag: HomeDrag,
    onAppClick: (PhoneApp) -> Unit,
) {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(TILE_GAP, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        repeat(rows) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(TILE_GAP)) {
                repeat(columns) { column ->
                    val cell = first + row * columns + column
                    HomeCell(cell, entries.getOrNull(cell), size, drag, cell == hole, onAppClick)
                }
            }
        }
    }
}

@Composable
private fun HomeCell(
    cell: Int,
    tile: HomeTile?,
    size: AppTileSize,
    drag: HomeDrag,
    isTarget: Boolean,
    onAppClick: (PhoneApp) -> Unit,
) {
    DisposableEffect(drag, cell) { onDispose { drag.cells.remove(cell) } }
    val shape = CarShapes.largeIncreased
    Box(
        Modifier.size(size.tile)
            .onGloballyPositioned { drag.cells[cell] = it.boundsInRoot() }
            .clip(shape)
            .then(
                if (isTarget) {
                    Modifier.background(
                            MaterialTheme.colorScheme.primary.copy(alpha = CarAlpha.DRAGGED)
                        )
                        .border(TARGET_BORDER, MaterialTheme.colorScheme.primary, shape)
                } else {
                    Modifier
                }
            )
    ) {
        if (tile != null) {
            AppTile(
                tile.app,
                size,
                onClick = { onAppClick(tile.app) },
                onLongClick = { drag.lift(tile.app, tile.index) },
                modifier = Modifier.alpha(if (drag.from == tile.index) LIFTED_ALPHA else 1f),
                labelStyle =
                    MaterialTheme.typography.bodyLarge.copy(
                        color = Color.White,
                        shadow = Shadow(Color.Black, blurRadius = LABEL_SHADOW_BLUR),
                    ),
                labelLines = LABEL_LINES,
            )
        }
    }
}

@Composable
private fun PageDots(pager: PagerState, pageCount: Int, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    Row(modifier.height(INDICATOR_HEIGHT), verticalAlignment = Alignment.CenterVertically) {
        repeat(pageCount) { page ->
            val isCurrent = page == pager.currentPage
            Box(
                Modifier.size(DOT_TARGET, INDICATOR_HEIGHT).clickable {
                    scope.launch { pager.scrollToPage(page) }
                },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.size(if (isCurrent) CURRENT_DOT_SIZE else DOT_SIZE)
                        .background(
                            MaterialTheme.colorScheme.onSurface.copy(
                                alpha = if (isCurrent) 1f else INACTIVE_DOT_ALPHA
                            ),
                            CircleShape,
                        )
                )
            }
        }
    }
}

@Composable
private fun RemoveTarget(drag: HomeDrag, modifier: Modifier = Modifier) {
    DisposableEffect(drag) { onDispose { drag.removeCenter = null } }
    val description = stringResource(R.string.remove)
    val radius = with(LocalDensity.current) { (REMOVE_CIRCLE / 2 * REMOVE_FIELD_SCALE).toPx() }
    Box(
        modifier.size(INDICATOR_HEIGHT * 2).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(REMOVE_CIRCLE)
                .onGloballyPositioned {
                    drag.removeCenter = it.boundsInRoot().center
                    drag.removeRadius = radius
                }
                .background(MaterialTheme.colorScheme.primaryFixed, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.ic_close),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryFixed,
                modifier = Modifier.size(REMOVE_ICON_SIZE),
            )
        }
    }
}
