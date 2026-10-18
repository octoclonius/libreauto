package io.github.octoclonius.libreauto.protocol

import java.io.IOException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class VideoFocusTest {

    @Test
    fun theNotificationHasTheModeAsField1AndWhetherItIsUnsolicitedAsField2() {
        val notification = parseVideoFocusNotification(bytes(0x08, 0x01, 0x10, 0x01))
        assertEquals(1, notification.mode)
        assertEquals(true, notification.unsolicited)
    }

    @Test
    fun aNativeNotificationFromTheBuickHasField2False() {
        val notification = parseVideoFocusNotification(bytes(0x08, 0x02, 0x10, 0x00))
        assertEquals(2, notification.mode)
        assertEquals(false, notification.unsolicited)
    }

    @Test
    fun aTruncatedNotificationIsRefused() {
        assertThrows<IOException> { parseVideoFocusNotification(bytes(0x08, 0x80)) }
    }

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }
}
