package io.github.octoclonius.libreauto.projection.video

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PendingFramesTest {

    private val frames = PendingFrames(VideoCompositor.MAX_PENDING_FRAMES)

    private fun drawUntilFull(): List<Long> {
        val drawn = mutableListOf<Long>()
        var presentationMicros = 1_000L
        while (frames.hasRoom) {
            frames.onDrawn(presentationMicros)
            drawn += presentationMicros
            presentationMicros += 1_000
        }
        return drawn
    }

    @Test
    fun drawingStopsAtTheLimit() {
        val drawn = drawUntilFull()
        assertEquals(VideoCompositor.MAX_PENDING_FRAMES, drawn.size)
        assertFalse(frames.hasRoom)
    }

    @Test
    fun aSentFrameFreesItsPlace() {
        val drawn = drawUntilFull()
        frames.onSent(drawn.first())
        assertTrue(frames.hasRoom)
    }

    @Test
    fun aLaterSentFrameAlsoFreesFramesTheEncoderLost() {
        val drawn = drawUntilFull()
        frames.onSent(drawn.last())
        repeat(VideoCompositor.MAX_PENDING_FRAMES) { frames.onDrawn(10_000L + it) }
        assertFalse(frames.hasRoom)
    }

    @Test
    fun anEarlierSentFrameFreesNothing() {
        drawUntilFull()
        frames.onSent(500)
        assertFalse(frames.hasRoom)
    }

    @Test
    fun clearingFreesEverything() {
        drawUntilFull()
        frames.clear()
        assertTrue(frames.hasRoom)
    }
}
