package io.github.octoclonius.libreauto.privileged.bluetooth

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class BluetoothRequestTest {

    private val address = "00:1A:2B:3C:4D:5E"

    @Test
    fun anUppercaseAddressIsAccepted() {
        assertNull(bluetoothRequestError(address))
    }

    @Test
    fun anythingElseIsRefused() {
        assertNotNull(bluetoothRequestError("00:1a:2b:3c:4d:5e"))
        assertNotNull(bluetoothRequestError("00:1A:2B:3C:4D"))
        assertNotNull(bluetoothRequestError("00-1A-2B-3C-4D-5E"))
        assertNotNull(bluetoothRequestError(""))
    }

    @Test
    fun aPinOf1To16BytesIsAccepted() {
        assertNull(pinRequestError(address, "1"))
        assertNull(pinRequestError(address, "1".repeat(MAX_PIN_BYTES)))
    }

    @Test
    fun thePinsLengthIsCountedInUtf8Bytes() {
        assertNotNull(pinRequestError(address, "\u00e9".repeat(MAX_PIN_BYTES / 2 + 1)))
    }

    @Test
    fun anEmptyOrLongerPinIsRefused() {
        assertNotNull(pinRequestError(address, ""))
        assertNotNull(pinRequestError(address, "1".repeat(MAX_PIN_BYTES + 1)))
    }

    @Test
    fun aPinForABadAddressIsRefused() {
        assertNotNull(pinRequestError("nope", "1234"))
    }
}
