package io.github.octoclonius.libreauto.projection.video

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OverlaySwitchTest {

    private val a = 0
    private val b = 1
    private val now = 1_000L

    @Test
    fun anOverlayWithAPictureShowsAtOnce() {
        val switch = OverlaySwitch(2)
        switch.onPicture(a, 10, now)
        assertTrue(switch.show(setOf(a), now))
        assertEquals(setOf(a), switch.shown)
    }

    @Test
    fun anOverlayWaitsForItsFirstPicture() {
        val switch = OverlaySwitch(2)
        assertFalse(switch.show(setOf(a), now))
        assertEquals(emptySet<Int>(), switch.shown)
        assertTrue(switch.onPicture(a, 10, now))
        assertEquals(setOf(a), switch.shown)
    }

    @Test
    fun theOldOverlayStaysUntilTheNewOneIsReady() {
        val switch = shownSwitch(a)
        switch.expect(b, 7)
        assertFalse(switch.show(setOf(b), now))
        assertEquals(setOf(a), switch.shown)
    }

    @Test
    fun expectedContentNeedsAPictureLatchedAfterItWasDrawn() {
        val switch = shownSwitch(a)
        switch.expect(b, 7)
        switch.onPicture(b, 10, now)
        switch.show(setOf(b), now)
        assertFalse(switch.reportDrawn(b, 7, 20, now))
        assertFalse(switch.onPicture(b, 19, now))
        assertTrue(switch.onPicture(b, 20, now))
        assertEquals(setOf(b), switch.shown)
    }

    @Test
    fun aPictureThatArrivedBeforeTheDrawnReportCounts() {
        val switch = shownSwitch(a)
        switch.expect(b, 7)
        switch.show(setOf(b), now)
        switch.onPicture(b, 30, now)
        assertTrue(switch.reportDrawn(b, 7, 20, now))
        assertEquals(setOf(b), switch.shown)
    }

    @Test
    fun drawingOlderContentDoesNotCount() {
        val switch = shownSwitch(a)
        switch.expect(b, 8)
        switch.show(setOf(b), now)
        switch.reportDrawn(b, 7, 20, now)
        assertFalse(switch.onPicture(b, 30, now))
        assertEquals(setOf(a), switch.shown)
    }

    @Test
    fun drawingBeforeTheContentIsExpectedCounts() {
        val switch = shownSwitch(a)
        switch.reportDrawn(b, 7, 20, now)
        switch.onPicture(b, 30, now)
        switch.expect(b, 7)
        assertTrue(switch.show(setOf(b), now))
    }

    @Test
    fun hidingIsImmediate() {
        val switch = shownSwitch(a)
        assertTrue(switch.show(emptySet(), now))
        assertEquals(emptySet<Int>(), switch.shown)
    }

    @Test
    fun aShownOverlayChangesItsContentInPlace() {
        val switch = shownSwitch(a)
        switch.expect(a, 7)
        switch.onPicture(b, 10, now)
        assertTrue(switch.show(setOf(a, b), now))
        assertEquals(setOf(a, b), switch.shown)
    }

    @Test
    fun aWaitingSwitchGivesUpAfterAndroidsSyncTimeout() {
        val switch = shownSwitch(a)
        switch.expect(b, 7)
        switch.show(setOf(b), now)
        val deadline = now + OverlaySwitch.BLAST_TIMEOUT_DURATION
        assertEquals(deadline, switch.deadlineMillis)
        assertFalse(switch.onDeadline(deadline - 1))
        assertTrue(switch.onDeadline(deadline))
        assertEquals(setOf(b), switch.shown)
        assertNull(switch.deadlineMillis)
    }

    @Test
    fun aNewerRequestReplacesAWaitingOneAndKeepsItsDeadline() {
        val switch = shownSwitch(a)
        switch.expect(b, 7)
        switch.show(setOf(b), now)
        assertTrue(switch.show(emptySet(), now + 100))
        assertEquals(emptySet<Int>(), switch.shown)
        assertNull(switch.deadlineMillis)
        switch.show(setOf(b), now + 200)
        switch.show(setOf(a, b), now + 300)
        assertEquals(now + 200 + OverlaySwitch.BLAST_TIMEOUT_DURATION, switch.deadlineMillis)
    }

    private fun shownSwitch(overlay: Int) =
        OverlaySwitch(2).apply {
            onPicture(overlay, 1, now)
            show(setOf(overlay), now)
        }
}
