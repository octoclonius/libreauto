package io.github.octoclonius.libreauto.car.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout

@Composable
fun RetainedVisibility(
    isVisible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Layout(content, modifier) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints) }
        layout(
            placeables.maxOfOrNull { it.width } ?: 0,
            placeables.maxOfOrNull { it.height } ?: 0,
        ) {
            if (isVisible) placeables.forEach { it.place(0, 0) }
        }
    }
}
