package io.github.octoclonius.libreauto.car.designsystem.theme

import android.util.DisplayMetrics
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.unit.Density

@Composable
fun ProvideCarDensity(carDensity: Int?, content: @Composable () -> Unit) {
    val displayDensity = LocalDensity.current
    val displayViewConfiguration = LocalViewConfiguration.current
    val density =
        carDensity?.let { it.toFloat() / DisplayMetrics.DENSITY_DEFAULT } ?: displayDensity.density
    val scale = density / displayDensity.density
    val viewConfiguration =
        remember(displayViewConfiguration, scale) {
            ScaledViewConfiguration(displayViewConfiguration, scale)
        }
    CompositionLocalProvider(
        LocalDensity provides Density(density, displayDensity.fontScale),
        LocalViewConfiguration provides viewConfiguration,
        content = content,
    )
}

private class ScaledViewConfiguration(
    private val base: ViewConfiguration,
    private val scale: Float,
) : ViewConfiguration by base {
    override val touchSlop: Float
        get() = base.touchSlop * scale

    override val handwritingSlop: Float
        get() = base.handwritingSlop * scale

    override val maximumFlingVelocity: Float
        get() = base.maximumFlingVelocity * scale

    override val minimumFlingVelocity: Float
        get() = base.minimumFlingVelocity * scale

    override val handwritingGestureLineMargin: Float
        get() = base.handwritingGestureLineMargin * scale
}
