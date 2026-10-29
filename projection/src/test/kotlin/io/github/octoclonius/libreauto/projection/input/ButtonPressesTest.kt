package io.github.octoclonius.libreauto.projection.input

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ButtonPressesTest {

    private val presses = ButtonPresses()

    @Test
    fun aPressActsOnce() {
        assertTrue(presses.isNewPress(3, isPressed = true))
        assertFalse(presses.isNewPress(3, isPressed = true))
    }

    @Test
    fun theReleaseOfAPressDoesNotAct() {
        presses.isNewPress(3, isPressed = true)
        assertFalse(presses.isNewPress(3, isPressed = false))
    }

    @Test
    fun aReleaseWithoutAPressActs() {
        assertTrue(presses.isNewPress(3, isPressed = false))
    }

    @Test
    fun aButtonActsAgainAfterItsRelease() {
        presses.isNewPress(3, isPressed = true)
        presses.isNewPress(3, isPressed = false)
        assertTrue(presses.isNewPress(3, isPressed = true))
    }

    @Test
    fun buttonsAreTrackedSeparately() {
        presses.isNewPress(3, isPressed = true)
        assertTrue(presses.isNewPress(4, isPressed = true))
    }
}
