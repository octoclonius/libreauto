package io.github.octoclonius.libreauto.protocol.media

import io.github.octoclonius.libreauto.protocol.bytes
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
    fun thePayloadIsWhatFollowsTheEightByteTimestamp() {
        assertArrayEquals(
            bytes(0x34, 0x12),
            mediaDataPayload(bytes(0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x34, 0x12)),
        )
    }

    @Test
    fun dataShorterThanTheTimestampIsRefused() {
        assertThrows<ProtocolException> { mediaDataPayload(bytes(0x01, 0x02, 0x03)) }
    }

    @Test
    fun anAckSentHasTheSessionAsField1AndTheCountAsField2() {
        assertArrayEquals(bytes(0x08, 0x03, 0x10, 0x01), mediaAck(3, 1))
    }

    @Test
    fun theDhusAckIsOfOneFrame() {
        assertEquals(1, parseMediaAck(bytes(0x08, 0x00, 0x10, 0x01)).count)
    }

    @Test
    fun anAckHasTheSessionAsField1AndTheCountAsField2() {
        val ack = parseMediaAck(bytes(0x08, 0x03, 0x10, 0x05))
        assertEquals(3, ack.session)
        assertEquals(5, ack.count)
    }

    @Test
    fun anAckWithoutASessionHasNone() {
        assertEquals(null, parseMediaAck(bytes(0x10, 0x01)).session)
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
            parseMediaAck(bytes(0x10, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0x01))
        }
    }

    @Test
    fun aTruncatedAckIsRefused() {
        assertThrows<IOException> { parseMediaAck(bytes(0x10, 0x80)) }
    }
}
