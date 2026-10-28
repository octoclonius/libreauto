package io.github.octoclonius.libreauto.privileged

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class KeyRequestTest {

    private val back = 4

    @Test
    fun backIsAccepted() {
        assertNull(keyRequestError(back))
    }

    @Test
    fun otherKeysAreRefused() {
        for (keyCode in listOf(3, 24, 26, 66, 85)) {
            assertNotNull(keyRequestError(keyCode))
        }
    }
}
