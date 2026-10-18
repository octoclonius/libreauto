package io.github.octoclonius.libreauto.protocol

import java.io.IOException
import java.net.ProtocolException
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class MediaDataTest {

    @Test
    fun theDataIsAnEightByteBigEndianTimestampThenTheFrame() {
        assertArrayEquals(
            bytes(0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x00, 0x00, 0x00, 0x01, 0x67),
            mediaData(0x0102030405060708, bytes(0x00, 0x00, 0x00, 0x01, 0x67)),
        )
    }

    @Test
    fun theDhusAckIsOfOneFrame() {
        assertEquals(1, parseMediaAck(bytes(0x08, 0x00, 0x10, 0x01)))
    }

    @Test
    fun anAckOfNoFramesIsRefused() {
        assertThrows<ProtocolException> { parseMediaAck(bytes(0x08, 0x00, 0x10, 0x00)) }
    }

    @Test
    fun anAckWithoutACountIsRefused() {
        assertThrows<ProtocolException> { parseMediaAck(bytes(0x08, 0x00)) }
    }

    @Test
    fun anAckOfANegativeCountIsRefused() {
        assertThrows<ProtocolException> {
            parseMediaAck(
                bytes(0x10, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0x01)
            )
        }
    }

    @Test
    fun aTruncatedAckIsRefused() {
        assertThrows<IOException> { parseMediaAck(bytes(0x10, 0x80)) }
    }

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }
}
