package io.github.octoclonius.libreauto.car.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.octoclonius.libreauto.car.ui.glyph.GLYPH_HEIGHT
import io.github.octoclonius.libreauto.car.ui.glyph.Glyph
import io.github.octoclonius.libreauto.car.ui.glyph.drawGlyphs
import io.github.octoclonius.libreauto.car.ui.glyph.glyphsOf
import io.github.octoclonius.libreauto.car.ui.glyph.scaledGlyph
import io.github.octoclonius.libreauto.car.ui.glyph.width
import io.github.octoclonius.libreauto.car.ui.icon.MaterialSymbols
import kotlin.math.ceil

private const val BODY_WIDTH = 24f
private const val BODY_HEIGHT = 13f
private const val BODY_CORNER_RADIUS = 4f
private const val CAP_GAP = 1f
private const val CAP_WIDTH = 1.5f
private const val CAP_HEIGHT = 6f
private const val CAP_CORNER_RADIUS = 1.2f
private const val BADGE_HEIGHT = 9f
private const val BADGE_OVERLAP = 0.2f
private const val BADGE_OUTLINE = 2f
private const val DIGIT_WEIGHT = 0.2f
private const val FULL_PERCENT = 100
private const val CRITICAL_PERCENT = 20

private val FILL_CHARGING = Color(0xFF18CC47)
private val FILL_POWER_SAVE = Color(0xFFFFC917)
private val FILL_CRITICAL = Color(0xFFFF0E01)
private const val BACKGROUND_ALPHA = 0.55f
private val DIGITS = Color.Black.copy(alpha = 0.75f)

private val BODY =
    Path().apply {
        addRoundRect(RoundRect(0f, 0f, BODY_WIDTH, BODY_HEIGHT, CornerRadius(BODY_CORNER_RADIUS)))
    }

private val CAP =
    Path().apply {
        val top = (BODY_HEIGHT - CAP_HEIGHT) / 2
        addRoundRect(
            RoundRect(
                left = BODY_WIDTH + CAP_GAP,
                top = top,
                right = BODY_WIDTH + CAP_GAP + CAP_WIDTH,
                bottom = top + CAP_HEIGHT,
                topLeftCornerRadius = CornerRadius.Zero,
                topRightCornerRadius = CornerRadius(CAP_CORNER_RADIUS),
                bottomRightCornerRadius = CornerRadius(CAP_CORNER_RADIUS),
                bottomLeftCornerRadius = CornerRadius.Zero,
            )
        )
    }

enum class BatteryBadge(pathData: String) {
    Charging(MaterialSymbols.BOLT_FILLED),
    ChargingPaused(MaterialSymbols.SHIELD_FILLED),
    PowerSave(MaterialSymbols.ADD);

    internal val glyph: Glyph = scaledGlyph(pathData, BADGE_HEIGHT)
}

@Composable
fun BatteryIndicator(
    percent: Int,
    badge: BatteryBadge?,
    height: Dp,
    modifier: Modifier = Modifier,
    isBodyCentered: Boolean = false,
    color: Color = LocalContentColor.current,
) {
    val digits = remember(percent) { glyphsOf(percent.toString()) }
    val end = badge?.let { it.glyph.width * (1 - BADGE_OVERLAP) } ?: (CAP_GAP + CAP_WIDTH)
    val width = BODY_WIDTH + end
    val unit = height / BODY_HEIGHT
    Canvas(
        modifier
            .padding(start = if (isBodyCentered) unit * end else 0.dp)
            .size(unit * width, height)
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    ) {
        val scale = size.height / BODY_HEIGHT
        val canvasWidth = size.width / scale
        val isRtl = layoutDirection == LayoutDirection.Rtl
        fun start(left: Float, itemWidth: Float) =
            if (isRtl) canvasWidth - left - itemWidth else left
        scale(scale, Offset.Zero) {
            scale(if (isRtl) -1f else 1f, 1f, Offset(canvasWidth / 2, 0f)) {
                drawBattery(
                    percent,
                    hasCap = badge == null,
                    fillColor(percent, badge, color),
                    color,
                )
            }
            val digitsWidth = digits.width(DIGIT_WEIGHT)
            translate(
                start((BODY_WIDTH - digitsWidth) / 2, digitsWidth),
                (BODY_HEIGHT - GLYPH_HEIGHT) / 2,
            ) {
                drawGlyphs(digits, DIGITS, DIGIT_WEIGHT)
            }
            badge?.glyph?.let { glyph ->
                translate(
                    start(canvasWidth - glyph.width, glyph.width),
                    (BODY_HEIGHT - glyph.height) / 2,
                ) {
                    drawPath(
                        glyph.path,
                        Color.Black,
                        style = Stroke(width = BADGE_OUTLINE),
                        blendMode = BlendMode.Clear,
                    )
                    drawPath(glyph.path, color)
                }
            }
        }
    }
}

private fun fillColor(percent: Int, badge: BatteryBadge?, color: Color) =
    when (badge) {
        BatteryBadge.Charging,
        BatteryBadge.ChargingPaused -> FILL_CHARGING
        BatteryBadge.PowerSave -> FILL_POWER_SAVE
        null -> if (percent <= CRITICAL_PERCENT) FILL_CRITICAL else color
    }

private fun DrawScope.drawBattery(percent: Int, hasCap: Boolean, fill: Color, color: Color) {
    val frame = if (percent >= FULL_PERCENT) fill else color.copy(alpha = BACKGROUND_ALPHA)
    drawPath(BODY, frame)
    if (hasCap) drawPath(CAP, frame)
    if (percent in 1 until FULL_PERCENT) {
        clipRect(right = ceil(percent * BODY_WIDTH / FULL_PERCENT)) { drawPath(BODY, fill) }
    }
}
