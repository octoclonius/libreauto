package io.github.octoclonius.libreauto.car.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import io.github.octoclonius.libreauto.car.designsystem.glyph.GLYPH_HEIGHT
import io.github.octoclonius.libreauto.car.designsystem.glyph.drawGlyphs
import io.github.octoclonius.libreauto.car.designsystem.glyph.glyphsOf
import io.github.octoclonius.libreauto.car.designsystem.glyph.width

@Composable
fun GlyphText(
    text: String,
    height: Dp,
    modifier: Modifier = Modifier,
    color: Color = LocalContentColor.current,
) {
    val glyphs = remember(text) { glyphsOf(text) }
    Canvas(
        modifier.size(height * (glyphs.width() / GLYPH_HEIGHT), height).semantics {
            contentDescription = text
        }
    ) {
        scale(size.height / GLYPH_HEIGHT, Offset.Zero) { drawGlyphs(glyphs, color) }
    }
}
