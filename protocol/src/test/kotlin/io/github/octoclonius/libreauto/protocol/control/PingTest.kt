package io.github.octoclonius.libreauto.protocol.control

import io.github.octoclonius.libreauto.protocol.bytes
import java.io.IOException
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PingTest {

    @Test
    fun theRequestTimestampIsField1() {
        assertEquals(1L, parsePingRequest(bytes(0x08, 0x01)))
    }

    @Test
    fun theRequestTimestampTakes64Bits() {
        assertEquals(1L shl 32, parsePingRequest(bytes(0x08, 0x80, 0x80, 0x80, 0x80, 0x10)))
    }

    @Test
    fun aRequestWithoutATimestampHasNone() {
        assertNull(parsePingRequest(bytes()))
    }

    @Test
    fun aTruncatedRequestIsRefused() {
        assertThrows<IOException> { parsePingRequest(bytes(0x08, 0x80)) }
    }

    @Test
    fun theResponseRepeatsTheTimestampAsField1() {
        assertArrayEquals(bytes(0x08, 0x80, 0x80, 0x80, 0x80, 0x10), pingResponse(1L shl 32))
    }

    @Test
    fun aResponseWithoutATimestampIsEmpty() {
        assertArrayEquals(bytes(), pingResponse(null))
    }
}
