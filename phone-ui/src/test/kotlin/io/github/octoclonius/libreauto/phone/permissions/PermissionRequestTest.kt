package io.github.octoclonius.libreauto.phone.permissions

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PermissionRequestTest {

    @Test
    fun aGrantIsNotADenial() {
        assertFalse(
            isDeniedWithoutDialog(true, wasRationaleShown = false, isRationaleShown = false)
        )
    }

    @Test
    fun aFirstDenialWasAnswered() {
        assertFalse(
            isDeniedWithoutDialog(false, wasRationaleShown = false, isRationaleShown = true)
        )
    }

    @Test
    fun aSecondDenialWasAnswered() {
        assertFalse(
            isDeniedWithoutDialog(false, wasRationaleShown = true, isRationaleShown = false)
        )
    }

    @Test
    fun aDenialAfterTwoDenialsHadNoDialog() {
        assertTrue(
            isDeniedWithoutDialog(false, wasRationaleShown = false, isRationaleShown = false)
        )
    }
}
