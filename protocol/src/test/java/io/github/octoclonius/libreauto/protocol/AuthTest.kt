package io.github.octoclonius.libreauto.protocol

import java.io.IOException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class AuthTest {

    @Test
    fun anEmptyAuthCompleteIsASuccess() {
        assertEquals(MessageStatus.SUCCESS, parseAuthComplete(byteArrayOf()))
    }

    @Test
    fun theStatusIsField1() {
        assertEquals(MessageStatus.SUCCESS, parseAuthComplete(byteArrayOf(0x08, 0)))
        assertEquals(1, parseAuthComplete(byteArrayOf(0x08, 1)))
    }

    @Test
    fun aTruncatedAuthCompleteIsRefused() {
        assertThrows<IOException> { parseAuthComplete(byteArrayOf(0x08)) }
    }
}
