package io.github.octoclonius.libreauto.car.calls

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CallerLookupTest {

    @Test
    fun aSipAddressIsAUriNumber() {
        assertTrue(isUriNumber("alex@example.com"))
        assertTrue(isUriNumber("alex%40example.com"))
    }

    @Test
    fun aPhoneNumberIsNotAUriNumber() {
        assertFalse(isUriNumber("+16502530000"))
    }
}
