package io.github.octoclonius.libreauto.car.ui.theme

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.rectangle
import androidx.graphics.shapes.toPath

private const val CORNER_SMOOTHING = 0.6f

class SquircleShape(private val corner: Dp) : Shape {

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val radius = with(density) { corner.toPx() }.coerceAtMost(size.minDimension / 2)
        val polygon =
            RoundedPolygon.rectangle(
                width = size.width,
                height = size.height,
                rounding = CornerRounding(radius, CORNER_SMOOTHING),
                centerX = size.width / 2,
                centerY = size.height / 2,
            )
        return Outline.Generic(polygon.toPath().asComposePath())
    }

    override fun equals(other: Any?): Boolean = other is SquircleShape && other.corner == corner

    override fun hashCode(): Int = corner.hashCode()
}
