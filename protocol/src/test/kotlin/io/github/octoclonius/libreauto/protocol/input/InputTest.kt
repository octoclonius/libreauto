package io.github.octoclonius.libreauto.protocol.input

import io.github.octoclonius.libreauto.protocol.bytes
import io.github.octoclonius.libreauto.protocol.control.parseServiceDiscoveryResponse
import java.io.IOException
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class InputTest {

    @Test
    fun theInputSourceIsTheOneOnTheVideoSinksDisplay() {
        val response = discovery(source(1, varint(5, 1)), source(2, varint(5, 2)))
        assertEquals(2, inputSource(response, 2)?.channel)
        assertNull(inputSource(response, 3))
    }

    @Test
    fun aMissingDisplayIdIs0() {
        assertEquals(4, inputSource(discovery(source(4)), 0)?.channel)
        assertNull(inputSource(discovery(source(4)), 1))
    }

    @Test
    fun otherServicesAreSkipped() {
        assertNull(inputSource(discovery(varint(1, 1) + message(3, message(4))), 0))
    }

    @Test
    fun theInputSourceKeepsItsPackedKeycodesInOrder() {
        val response = discovery(source(3, bytes(0x0A, 0x05, 0x04, 0x03, 0x81, 0x80, 0x04)))
        assertEquals(listOf(4, 3, 65537), inputSource(response, 0)?.keycodes)
    }

    @Test
    fun theBindingRequestHasTheKeycodesPackedAsField1() {
        assertArrayEquals(
            bytes(0x0A, 0x05, 0x03, 0x04, 0x81, 0x80, 0x04),
            inputBindingRequest(listOf(3, 4, 65537)),
        )
    }

    @Test
    fun theBindingResponseStatusIsField1() {
        assertEquals(0, parseInputBindingResponse(bytes(0x08, 0x00)))
        assertEquals(1, parseInputBindingResponse(bytes(0x08, 0x01)))
    }

    @Test
    fun aTruncatedBindingResponseIsRefused() {
        assertThrows<IOException> { parseInputBindingResponse(bytes(0x08, 0x80)) }
    }

    @Test
    fun aReportHasTheTimeAsField1AndTheTouchAsField3() {
        val report =
            parseInputReport(
                bytes(0x08, 0x80, 0xC1, 0xD5, 0xD6, 0xCE, 0x91, 0xDD, 0xED, 0x18) +
                    bytes(0x1A, 0x0E) +
                    bytes(0x0A, 0x08, 0x08, 0x90, 0x03, 0x10, 0xAC, 0x02, 0x18, 0x00) +
                    bytes(0x10, 0x00) +
                    bytes(0x18, 0x00)
            )
        assertEquals(1_791_153_425_394_000_000, report.timestamp)
        val touch = report.touch
        assertEquals(
            listOf(Triple(400, 300, 0)),
            touch?.pointers?.map { Triple(it.x, it.y, it.id) },
        )
        assertEquals(0, touch?.action_index)
        assertEquals(0, touch?.action)
    }

    @Test
    fun aTouchKeepsItsPointersInOrder() {
        val touch =
            parseInputReport(
                    bytes(0x1A, 0x17) +
                        bytes(0x0A, 0x07, 0x08, 0x64, 0x10, 0xC8, 0x01, 0x18, 0x00) +
                        bytes(0x0A, 0x08, 0x08, 0xAC, 0x02, 0x10, 0x90, 0x03, 0x18, 0x01) +
                        bytes(0x10, 0x01) +
                        bytes(0x18, 0x05)
                )
                .touch
        assertEquals(
            listOf(Triple(100, 200, 0), Triple(300, 400, 1)),
            touch?.pointers?.map { Triple(it.x, it.y, it.id) },
        )
        assertEquals(1, touch?.action_index)
        assertEquals(5, touch?.action)
    }

    @Test
    fun aReportsButtonEventsAreField4WithTheCodeAsField1AndPressedAsField2() {
        val presses =
            buttonPresses(parseInputReport(bytes(0x22, 0x06, 0x0A, 0x04, 0x08, 0x03, 0x10, 0x01)))
        assertEquals(listOf(3 to true), presses.map { it.code to it.isPressed })
    }

    @Test
    fun aButtonEventWithoutPressedIsAReleaseAndOneWithoutACodeIsSkipped() {
        val presses =
            buttonPresses(
                parseInputReport(bytes(0x22, 0x08, 0x0A, 0x02, 0x08, 0x04, 0x0A, 0x02, 0x10, 0x01))
            )
        assertEquals(listOf(4 to false), presses.map { it.code to it.isPressed })
    }

    @Test
    fun aReportKeepsItsButtonEventsInOrder() {
        val presses =
            buttonPresses(
                parseInputReport(
                    bytes(0x22, 0x0C) +
                        bytes(0x0A, 0x04, 0x08, 0x55, 0x10, 0x01) +
                        bytes(0x0A, 0x04, 0x08, 0x57, 0x10, 0x00)
                )
            )
        assertEquals(listOf(85 to true, 87 to false), presses.map { it.code to it.isPressed })
    }

    @Test
    fun aTruncatedReportIsRefused() {
        assertThrows<IOException> { parseInputReport(bytes(0x1A, 0x04, 0x0A, 0x02, 0x08)) }
    }

    private fun discovery(vararg services: ByteArray) =
        parseServiceDiscoveryResponse(services.map { message(1, it) }.reduce(ByteArray::plus))

    private fun source(channel: Int, vararg fields: ByteArray) =
        varint(1, channel) + message(4, *fields)

    private fun varint(number: Int, value: Int) =
        byteArrayOf((number shl 3).toByte(), value.toByte())

    private fun message(number: Int, vararg fields: ByteArray): ByteArray {
        val body = fields.fold(ByteArray(0), ByteArray::plus)
        return byteArrayOf((number shl 3 or 2).toByte(), body.size.toByte()) + body
    }
}
