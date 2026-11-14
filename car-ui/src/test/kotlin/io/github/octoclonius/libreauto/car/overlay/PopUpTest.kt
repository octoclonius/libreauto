package io.github.octoclonius.libreauto.car.overlay

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PopUpTest {

    @Test
    fun touchesRightAfterAPopUpShowsAreIgnored() {
        assertFalse(isTouchAccepted(shownAtMillis = 1_000, nowMillis = 1_699))
    }

    @Test
    fun touchesAfterTheAcceptanceDelayCount() {
        assertTrue(isTouchAccepted(shownAtMillis = 1_000, nowMillis = 1_700))
    }
}
