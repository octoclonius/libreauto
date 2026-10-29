package io.github.octoclonius.libreauto.car.ui.glyph

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser

const val GLYPH_HEIGHT = 9f
private const val GLYPH_SPACING = 0.8f
private const val DOT_DIAMETER = 1.9f
private const val DOT_SIDE_BEARING = 0.4f
private const val COLON_TOP = 2.1f

class Glyph(val path: Path, val width: Float, val height: Float) {
    constructor(
        pathData: String,
        width: Float,
        height: Float,
    ) : this(PathParser().parsePathString(pathData).toPath(), width, height)
}

fun scaledGlyph(pathData: String, height: Float): Glyph {
    val path = PathParser().parsePathString(pathData).toPath()
    val bounds = path.getBounds()
    val scale = height / bounds.height
    path.translate(Offset(-bounds.left, -bounds.top))
    path.transform(Matrix().apply { scale(scale, scale) })
    return Glyph(path, bounds.width * scale, height)
}

private fun dots(vararg tops: Float) =
    Glyph(
        Path().apply {
            for (top in tops) {
                addOval(
                    Rect(DOT_SIDE_BEARING, top, DOT_SIDE_BEARING + DOT_DIAMETER, top + DOT_DIAMETER)
                )
            }
        },
        width = DOT_DIAMETER + 2 * DOT_SIDE_BEARING,
        height = GLYPH_HEIGHT,
    )

private val COLON = dots(COLON_TOP, GLYPH_HEIGHT - DOT_DIAMETER)
private val DOT = dots(GLYPH_HEIGHT - DOT_DIAMETER)

fun glyphsOf(text: String): List<Glyph> = text.mapNotNull { char ->
    val digit = Character.digit(char, 10)
    when {
        digit >= 0 -> DIGIT_GLYPHS[digit]
        char == ':' -> COLON
        char == '.' -> DOT
        else -> null
    }
}

fun List<Glyph>.width(weight: Float = 0f): Float =
    if (isEmpty()) 0f
    else sumOf { it.width.toDouble() }.toFloat() + (GLYPH_SPACING + weight) * (size - 1)

fun DrawScope.drawGlyphs(glyphs: List<Glyph>, color: Color, weight: Float = 0f) {
    val outline = Stroke(width = weight, join = StrokeJoin.Round)
    var left = 0f
    for (glyph in glyphs) {
        translate(left, (GLYPH_HEIGHT - glyph.height) / 2) {
            drawPath(glyph.path, color)
            if (weight > 0f) drawPath(glyph.path, color, style = outline)
        }
        left += glyph.width + GLYPH_SPACING + weight
    }
}
