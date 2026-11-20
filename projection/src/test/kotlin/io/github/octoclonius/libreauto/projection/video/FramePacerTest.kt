package io.github.octoclonius.libreauto.projection.video

import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FramePacerTest {

    private val second = TimeUnit.SECONDS.toNanos(1)
    private val millisecond = TimeUnit.MILLISECONDS.toNanos(1)

    private fun drawnFrames(pacer: FramePacer, refreshRate: Int, seconds: Int): List<Long> {
        val vsyncInterval = second / refreshRate
        val drawn = mutableListOf<Long>()
        for (index in 0 until refreshRate * seconds) {
            val vsync = index * vsyncInterval
            if (pacer.isDue(vsync)) {
                pacer.onDrawn(vsync)
                drawn += vsync
            }
        }
        return drawn
    }

    @Test
    fun theFirstVsyncIsDue() {
        assertTrue(FramePacer(60).isDue(123 * millisecond))
    }

    @Test
    fun aPhoneAtTheCarsRateDrawsOnEveryVsync() {
        assertEquals(600, drawnFrames(FramePacer(60), refreshRate = 60, seconds = 10).size)
    }

    @Test
    fun aPhoneAtTwiceTheCarsRateDrawsOnEveryOtherVsync() {
        val drawn = drawnFrames(FramePacer(60), refreshRate = 120, seconds = 10)
        assertEquals(600, drawn.size)
        assertTrue(drawn.zipWithNext { a, b -> b - a }.all { it == 2 * (second / 120) })
    }

    @Test
    fun aPhoneAtFourTimesTheCarsRateDrawsOnEveryFourthVsync() {
        assertEquals(300, drawnFrames(FramePacer(30), refreshRate = 120, seconds = 10).size)
    }

    @Test
    fun aPhoneWhoseRateIsNoMultipleOfTheCarsStillKeepsTheCarsRate() {
        assertEquals(600, drawnFrames(FramePacer(60), refreshRate = 90, seconds = 10).size)
    }

    @Test
    fun aPhoneSlowerThanTheCarDrawsOnEveryVsync() {
        assertEquals(300, drawnFrames(FramePacer(60), refreshRate = 30, seconds = 10).size)
    }

    @Test
    fun aVsyncSlightlyBeforeTheDueTimeIsDue() {
        val pacer = FramePacer(60)
        pacer.onDrawn(0)
        assertTrue(pacer.isDue(15 * millisecond))
        assertFalse(pacer.isDue(13 * millisecond))
    }

    @Test
    fun aLateFrameKeepsTheSchedule() {
        val pacer = FramePacer(60)
        pacer.onDrawn(0)
        pacer.onDrawn(20 * millisecond)
        assertTrue(pacer.isDue(30 * millisecond))
    }

    @Test
    fun aFrameAWholeIntervalLateRestartsTheSchedule() {
        val pacer = FramePacer(60)
        pacer.onDrawn(0)
        pacer.onDrawn(100 * millisecond)
        assertFalse(pacer.isDue(105 * millisecond))
        assertTrue(pacer.isDue(114 * millisecond))
    }

    @Test
    fun theDelayEndsWhenTheNextFrameCanBeDue() {
        val pacer = FramePacer(60)
        assertEquals(0, pacer.delayMillis(0))
        pacer.onDrawn(0)
        assertEquals(11, pacer.delayMillis(2 * millisecond))
        assertEquals(0, pacer.delayMillis(20 * millisecond))
    }

    @Test
    fun resettingMakesTheNextVsyncDue() {
        val pacer = FramePacer(60)
        pacer.onDrawn(0)
        pacer.reset()
        assertTrue(pacer.isDue(millisecond))
    }
}
