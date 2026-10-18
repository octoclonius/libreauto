package io.github.octoclonius.libreauto.protocol

import java.io.IOException
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class MediaSetupTest {

    @Test
    fun theRequestHasH264BaselineAsField1() {
        assertArrayEquals(bytes(0x08, 0x03), videoSetupRequest())
    }

    @Test
    fun theResponseHasTheStatusTheMaxUnackedFramesAndTheConfigIndexes() {
        val response =
            parseMediaSetupResponse(bytes(0x08, 0x02, 0x10, 0x04, 0x18, 0x00, 0x18, 0x01))
        assertEquals(2, response.status)
        assertEquals(4, response.max_unacked)
        assertEquals(listOf(0, 1), response.config_indexes)
    }

    @Test
    fun aTruncatedResponseIsRefused() {
        assertThrows<IOException> { parseMediaSetupResponse(bytes(0x10, 0x80)) }
    }

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }
}
