package io.github.octoclonius.libreauto.protocol

import java.io.IOException
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ByeByeTest {

    @Test
    fun theRequestReasonIsField1() {
        assertEquals(1, parseByeByeRequest(byteArrayOf(0x08, 1)))
        assertEquals(3, parseByeByeRequest(byteArrayOf(0x08, 3)))
    }

    @Test
    fun aRequestWithoutAReasonHasNone() {
        assertNull(parseByeByeRequest(byteArrayOf()))
    }

    @Test
    fun aTruncatedRequestIsRefused() {
        assertThrows<IOException> { parseByeByeRequest(byteArrayOf(0x08)) }
    }

    @Test
    fun theResponseIsEmpty() {
        assertArrayEquals(byteArrayOf(), byeByeResponse())
    }
}
