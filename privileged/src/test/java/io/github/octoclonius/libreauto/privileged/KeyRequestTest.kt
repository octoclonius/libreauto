package io.github.octoclonius.libreauto.privileged

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class KeyRequestTest {

    private val back = 4

    @Test
    fun backToTheCarDisplayIsAccepted() {
        assertNull(keyRequestError(7, back, carDisplayId = 7))
    }

    @Test
    fun aKeyWithoutACarDisplayIsRefused() {
        assertNotNull(keyRequestError(7, back, carDisplayId = null))
    }

    @Test
    fun aKeyToAnotherDisplayIsRefused() {
        assertNotNull(keyRequestError(0, back, carDisplayId = 7))
    }

    @Test
    fun otherKeysAreRefused() {
        for (keyCode in listOf(3, 24, 26, 66, 85)) {
            assertNotNull(keyRequestError(7, keyCode, carDisplayId = 7))
        }
    }
}
