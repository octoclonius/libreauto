package io.github.octoclonius.libreauto.car.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animate
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.octoclonius.libreauto.car.designsystem.theme.CarSpacing
import kotlin.math.roundToInt

private const val TOUCH_ACCEPTANCE_DELAY_MILLIS = 700L
private val MAX_WIDTH = 440.dp
private val MARGIN = CarSpacing.medium
private val BORDER_WIDTH = 1.dp
private val HIDE_DISTANCE = 56.dp
private val HIDE_VELOCITY = 125.dp
private const val SURFACE_ALPHA = 0.75f

internal fun isTouchAccepted(shownAtMillis: Long, nowMillis: Long): Boolean =
    nowMillis - shownAtMillis >= TOUCH_ACCEPTANCE_DELAY_MILLIS

internal object PopUpDefaults {
    val color: Color
        @Composable
        get() = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = SURFACE_ALPHA)

    val border: BorderStroke
        @Composable get() = BorderStroke(BORDER_WIDTH, MaterialTheme.colorScheme.outlineVariant)
}

private class LastShown<T>(var item: T?)

@Composable
internal fun <T : Any> PopUp(
    item: T?,
    key: (T) -> Any,
    onHide: () -> Unit,
    onOnScreen: (Boolean) -> Unit,
    onBounds: (Rect?) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit,
) {
    val visibility = remember { MutableTransitionState(false) }
    visibility.targetState = item != null
    val lastShown = remember { LastShown(item) }
    if (item != null) lastShown.item = item
    val shown = lastShown.item
    val isOnScreen = visibility.currentState || visibility.targetState
    LaunchedEffect(isOnScreen) {
        onOnScreen(isOnScreen)
        if (!isOnScreen) onBounds(null)
    }
    BoxWithConstraints(Modifier.fillMaxSize().padding(MARGIN)) {
        AnimatedVisibility(
            visibility,
            modifier =
                Modifier.align(Alignment.TopCenter)
                    .width(minOf(maxWidth, MAX_WIDTH))
                    .onGloballyPositioned { onBounds(it.boundsInRoot()) },
            enter = slideInVertically { -it },
            exit = slideOutVertically { -it },
        ) {
            if (shown == null) return@AnimatedVisibility
            key(key(shown)) { SwipeUpToHide(onHide, modifier) { content(shown) } }
        }
    }
}

@Composable
private fun SwipeUpToHide(
    onHide: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var offset by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val currentOnHide by rememberUpdatedState(onHide)
    Box(
        modifier
            .offset { IntOffset(0, offset.roundToInt()) }
            .draggable(
                rememberDraggableState { delta -> offset = (offset + delta).coerceAtMost(0f) },
                Orientation.Vertical,
                onDragStopped = { velocity ->
                    val isHidden =
                        with(density) {
                            offset < -HIDE_DISTANCE.toPx() || velocity < -HIDE_VELOCITY.toPx()
                        }
                    if (isHidden) currentOnHide()
                    else animate(offset, 0f) { value, _ -> offset = value }
                },
            )
    ) {
        content()
    }
}
