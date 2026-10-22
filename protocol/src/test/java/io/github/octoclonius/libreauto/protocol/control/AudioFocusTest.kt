package io.github.octoclonius.libreauto.protocol.control

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AudioFocusTest {

    @Test
    fun theRequestHasItsTypeAsField1() {
        assertArrayEquals(bytes(0x08, 0x01), audioFocusRequest(1))
        assertArrayEquals(bytes(0x08, 0x04), audioFocusRequest(4))
    }

    @Test
    fun theNotificationHasTheStateAsField1AndUnsolicitedAsField2() {
        val notification = parseAudioFocusNotification(bytes(0x08, 0x03, 0x10, 0x01))
        assertEquals(3, notification.state)
        assertEquals(true, notification.unsolicited)
    }

    @Test
    fun aNotificationWithoutUnsolicitedHasNone() {
        val notification = parseAudioFocusNotification(bytes(0x08, 0x01))
        assertEquals(1, notification.state)
        assertEquals(null, notification.unsolicited)
    }

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }
}
