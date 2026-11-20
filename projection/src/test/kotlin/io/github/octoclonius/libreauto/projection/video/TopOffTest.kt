package io.github.octoclonius.libreauto.projection.video

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TopOffTest {

    private val share = 85_000L

    @Test
    fun nothingIsNeededBeforeAPicture() {
        assertFalse(TopOff(decoderDepth = 4).isNeeded)
    }

    @Test
    fun aNewPictureIsNeeded() {
        val topOff = TopOff(decoderDepth = 0)
        topOff.onNewPicture()
        assertTrue(topOff.isNeeded)
    }

    @Test
    fun framesFollowAPictureUntilTheCarsDecoderHoldsNoneOfIt() {
        val topOff = TopOff(decoderDepth = 2)
        topOff.onNewPicture()
        topOff.onDrawn()
        topOff.onEncoded(share / 2, share)
        topOff.onDrawn()
        topOff.onEncoded(share / 2, share)
        assertTrue(topOff.isNeeded)
        topOff.onDrawn()
        topOff.onEncoded(share / 2, share)
        assertFalse(topOff.isNeeded)
    }

    @Test
    fun framesFollowUntilTheOverspendIsEarnedBack() {
        val topOff = TopOff(decoderDepth = 0)
        topOff.onNewPicture()
        topOff.onDrawn()
        topOff.onEncoded(5 * share, share)
        repeat(7) {
            topOff.onDrawn()
            topOff.onEncoded(share / 2, share)
            assertTrue(topOff.isNeeded)
        }
        topOff.onDrawn()
        topOff.onEncoded(share / 2, share)
        assertFalse(topOff.isNeeded)
    }

    @Test
    fun framesStopOnceTheOverspendIsEarnedBackEvenIfTheyStillShrink() {
        val topOff = TopOff(decoderDepth = 0)
        topOff.onNewPicture()
        topOff.onDrawn()
        topOff.onEncoded(share / 2, share)
        topOff.onDrawn()
        topOff.onEncoded(share / 4, share)
        assertFalse(topOff.isNeeded)
    }

    @Test
    fun framesThatSpendTheirWholeShareNeverEnd() {
        val topOff = TopOff(decoderDepth = 0)
        topOff.onNewPicture()
        topOff.onDrawn()
        topOff.onEncoded(3 * share, share)
        repeat(1_000) {
            topOff.onDrawn()
            topOff.onEncoded(share, share)
        }
        assertTrue(topOff.isNeeded)
    }

    @Test
    fun anUnderspendDoesntCountAgainstTheNextPicture() {
        val topOff = TopOff(decoderDepth = 0)
        topOff.onNewPicture()
        topOff.onDrawn()
        topOff.onEncoded(share / 4, share)
        topOff.onNewPicture()
        topOff.onDrawn()
        topOff.onEncoded(2 * share, share)
        assertTrue(topOff.isNeeded)
    }

    @Test
    fun aPictureDuringTheTailRestartsIt() {
        val topOff = TopOff(decoderDepth = 2)
        topOff.onNewPicture()
        topOff.onDrawn()
        topOff.onDrawn()
        topOff.onNewPicture()
        topOff.onDrawn()
        topOff.onDrawn()
        assertTrue(topOff.isNeeded)
    }

    @Test
    fun resettingForgetsEverything() {
        val topOff = TopOff(decoderDepth = 4)
        topOff.onNewPicture()
        topOff.onDrawn()
        topOff.onEncoded(5 * share, share)
        topOff.reset()
        assertFalse(topOff.isNeeded)
    }
}
