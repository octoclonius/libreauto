package io.github.octoclonius.libreauto.protocol

import java.net.ProtocolException
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class VersionTest {

    @Test
    fun aVersionRequestIsMajorThenMinor() {
        assertEquals(ProtocolVersion(1, 7), parseVersionRequest(byteArrayOf(0, 1, 0, 7)))
    }

    @Test
    fun versionRequestNumbersAreUnsigned() {
        val body = byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0x80.toByte(), 0)
        assertEquals(ProtocolVersion(0xFFFF, 0x8000), parseVersionRequest(body))
    }

    @Test
    fun bytesAfterTheVersionAreIgnored() {
        assertEquals(ProtocolVersion(6, 1), parseVersionRequest(byteArrayOf(0, 6, 0, 1, 9)))
    }

    @Test
    fun aShortVersionRequestIsRefused() {
        assertThrows<ProtocolException> { parseVersionRequest(byteArrayOf(0, 1, 0)) }
    }

    @Test
    fun aVersionResponseIsMajorMinorThenStatus() {
        val response = VersionResponse(ProtocolVersion(6, 1), status = 0)
        assertArrayEquals(byteArrayOf(0, 6, 0, 1, 0, 0), response.toBody())
    }

    @Test
    fun versionResponseNumbersAreUnsigned() {
        val response = VersionResponse(ProtocolVersion(0xFFFF, 0x8000), 0)
        val body = byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0x80.toByte(), 0, 0, 0)
        assertArrayEquals(body, response.toBody())
    }

    @Test
    fun carsUpTo1Point7Get1Point7AndNewerOnesGet6Point1() {
        assertEquals(ProtocolVersion(1, 7), ProtocolVersion.answerTo(ProtocolVersion(1, 0)))
        assertEquals(ProtocolVersion(1, 7), ProtocolVersion.answerTo(ProtocolVersion(1, 7)))
        assertEquals(ProtocolVersion(6, 1), ProtocolVersion.answerTo(ProtocolVersion(1, 8)))
        assertEquals(ProtocolVersion(6, 1), ProtocolVersion.answerTo(ProtocolVersion(7, 0)))
    }
}
