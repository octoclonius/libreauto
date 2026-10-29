package io.github.octoclonius.libreauto.car.apps

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.octoclonius.libreauto.car.designsystem.theme.CarAlpha
import io.github.octoclonius.libreauto.car.designsystem.theme.CarSpacing
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private val GRID_PADDING = CarSpacing.large
private val LETTER_COLUMN_WIDTH = 32.dp
private val SCROLLER_WIDTH = 48.dp
private val SCROLLER_MIN_LETTER_HEIGHT = 14.dp
private val SCROLLER_LETTER_SIZE = 12.sp
private val BUBBLE_SIZE = 80.dp
private val BUBBLE_SPACING = CarSpacing.small

@Composable
fun AppDrawer(
    sections: List<AppSection>,
    onAppClick: (PhoneApp) -> Unit,
    onAppLongClick: (PhoneApp) -> Unit,
    modifier: Modifier = Modifier,
    state: LazyGridState = rememberLazyGridState(),
) {
    val grid = remember(sections) { SectionedApps(sections) }
    val scope = rememberCoroutineScope()
    var scrub by remember { mutableStateOf<Scrub?>(null) }
    val currentSection by
        remember(grid, state) {
            derivedStateOf { grid.sectionIndexOfItem(state.firstVisibleItemIndex) }
        }
    Surface(modifier) {
        Box(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxSize()) {
                AppGrid(
                    grid,
                    onAppClick,
                    onAppLongClick,
                    state,
                    Modifier.weight(1f).fillMaxHeight(),
                )
                Box(Modifier.width(SCROLLER_WIDTH).fillMaxHeight()) {
                    if (state.canScrollBackward || state.canScrollForward) {
                        LetterScroller(
                            sections,
                            current = scrub?.section ?: currentSection,
                            onScrub = { section, y ->
                                val target = section ?: scrub?.section
                                if (target != null && target != scrub?.section) {
                                    grid.firstItemOf(target)?.let {
                                        scope.launch { state.scrollToItem(it) }
                                    }
                                }
                                scrub = target?.let { Scrub(it, y) }
                            },
                            onRelease = { scrub = null },
                        )
                    }
                }
            }
            scrub?.let { LetterBubble(sections[it.section].label, it.y) }
        }
    }
}

private class Scrub(val section: Int, val y: Float)

private class SectionedApps(val sections: List<AppSection>) {
    val shownIndices = sections.indices.filter { sections[it].apps.isNotEmpty() }
    val shown = shownIndices.map { sections[it] }
    val starts = shown.runningFold(0) { start, section -> start + section.apps.size }

    fun shownIndexOfItem(item: Int): Int =
        starts.binarySearch(item).let { if (it >= 0) it else -it - 2 }.coerceIn(shown.indices)

    fun sectionIndexOfItem(item: Int): Int? =
        if (shown.isEmpty()) null else shownIndices[shownIndexOfItem(item)]

    fun firstItemOf(section: Int): Int? =
        shownIndices.indexOf(section).takeIf { it >= 0 }?.let { starts[it] }
}

@Composable
private fun AppGrid(
    grid: SectionedApps,
    onAppClick: (PhoneApp) -> Unit,
    onAppLongClick: (PhoneApp) -> Unit,
    state: LazyGridState,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier) {
        val tilesWidth = maxWidth - LETTER_COLUMN_WIDTH - GRID_PADDING
        val columns =
            ((tilesWidth + TILE_GAP) / (DRAWER_TILE.tile + TILE_GAP)).toInt().coerceAtLeast(1)
        val extra =
            ((tilesWidth - DRAWER_TILE.tile * columns - TILE_GAP * (columns - 1)) / 2)
                .coerceAtLeast(0.dp)
        LazyVerticalGrid(
            state = state,
            columns = GridCells.Fixed(columns),
            contentPadding =
                PaddingValues(
                    start = extra + LETTER_COLUMN_WIDTH,
                    end = extra + GRID_PADDING,
                    top = GRID_PADDING,
                    bottom = GRID_PADDING,
                ),
            verticalArrangement = Arrangement.spacedBy(TILE_GAP),
            horizontalArrangement = Arrangement.spacedBy(TILE_GAP),
        ) {
            grid.shown.forEach { section ->
                itemsIndexed(
                    section.apps,
                    key = { _, app -> app.component.flattenToShortString() },
                    span = { index, _ ->
                        GridItemSpan(if (index == section.apps.lastIndex) maxCurrentLineSpan else 1)
                    },
                ) { _, app ->
                    AppTile(
                        app,
                        DRAWER_TILE,
                        onClick = { onAppClick(app) },
                        onLongClick = { onAppLongClick(app) },
                        modifier = Modifier.wrapContentWidth(Alignment.Start),
                    )
                }
            }
        }
        SectionLetters(
            grid,
            state,
            Modifier.padding(start = extra).width(LETTER_COLUMN_WIDTH).fillMaxHeight(),
        )
    }
}

@Composable
private fun SectionLetters(grid: SectionedApps, state: LazyGridState, modifier: Modifier) {
    val visibleSections by
        remember(grid, state) {
            derivedStateOf {
                val items = state.layoutInfo.visibleItemsInfo
                if (items.isEmpty() || grid.shown.isEmpty()) {
                    IntRange.EMPTY
                } else {
                    grid.shownIndexOfItem(items.first().index)..grid.shownIndexOfItem(
                            items.last().index
                        )
                }
            }
        }
    Box(modifier.clipToBounds()) {
        for (section in visibleSections) {
            key(grid.shown[section].label) {
                Text(
                    grid.shown[section].label,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier =
                        Modifier.layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints)
                            layout(placeable.width, placeable.height) {
                                placeable.place(
                                    0,
                                    letterOffset(grid, state, section, placeable.height),
                                )
                            }
                        },
                )
            }
        }
    }
}

private fun letterOffset(
    grid: SectionedApps,
    state: LazyGridState,
    section: Int,
    height: Int,
): Int {
    val info = state.layoutInfo
    val range = grid.starts[section] until grid.starts[section + 1]
    val items = info.visibleItemsInfo.filter { it.index in range }
    val first = items.firstOrNull() ?: return -height
    val last = items.last()
    val lastRowTop = if (last.index == range.last) last.offset.y else Int.MAX_VALUE
    return info.beforeContentPadding + minOf(maxOf(first.offset.y, 0), lastRowTop)
}

@Composable
private fun LetterScroller(
    sections: List<AppSection>,
    current: Int?,
    onScrub: (section: Int?, y: Float) -> Unit,
    onRelease: () -> Unit,
) {
    val currentOnScrub by rememberUpdatedState(onScrub)
    val currentOnRelease by rememberUpdatedState(onRelease)
    val contentColor = LocalContentColor.current
    Layout(
        content = {
            sections.forEachIndexed { index, section ->
                Text(
                    section.label,
                    fontSize = SCROLLER_LETTER_SIZE,
                    color =
                        when {
                            index == current -> MaterialTheme.colorScheme.primary
                            section.apps.isEmpty() -> contentColor.copy(alpha = CarAlpha.DISABLED)
                            else -> contentColor
                        },
                )
            }
        },
        modifier =
            Modifier.fillMaxSize().pointerInput(sections) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    try {
                        var position = down.position
                        while (true) {
                            val padding = GRID_PADDING.toPx()
                            val slot = (size.height - 2 * padding) / sections.size
                            val index =
                                ((position.y - padding) / slot).toInt().coerceIn(sections.indices)
                            val bubble = BUBBLE_SIZE.toPx() / 2
                            val y = position.y.coerceIn(bubble, size.height - bubble)
                            currentOnScrub(index.takeIf { sections[it].apps.isNotEmpty() }, y)
                            val change =
                                awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                            if (change == null || !change.pressed) break
                            change.consume()
                            position = change.position
                        }
                    } finally {
                        currentOnRelease()
                    }
                }
            },
    ) { measurables, constraints ->
        val padding = GRID_PADDING.roundToPx()
        val slot = (constraints.maxHeight - 2 * padding).toFloat() / measurables.size
        val step = ceil(SCROLLER_MIN_LETTER_HEIGHT.toPx() / slot).toInt().coerceAtLeast(1)
        val placeables = measurables.map {
            it.measure(constraints.copy(minWidth = 0, minHeight = 0))
        }
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeables.forEachIndexed { index, placeable ->
                if (index % step == 0) {
                    placeable.place(
                        (constraints.maxWidth - placeable.width) / 2,
                        (padding + (index + 0.5f) * slot - placeable.height / 2f).roundToInt(),
                    )
                }
            }
        }
    }
}

@Composable
private fun LetterBubble(label: String, y: Float) {
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.align(Alignment.TopEnd)
                .offset {
                    IntOffset(
                        -(SCROLLER_WIDTH + BUBBLE_SPACING).roundToPx(),
                        (y - BUBBLE_SIZE.toPx() / 2).roundToInt(),
                    )
                }
                .size(BUBBLE_SIZE)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}
