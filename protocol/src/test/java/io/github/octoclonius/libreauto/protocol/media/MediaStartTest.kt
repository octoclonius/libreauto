package io.github.octoclonius.libreauto.protocol.media

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Test

class MediaStartTest {

    @Test
    fun theStartHasSession0AsField1AndTheConfigIndexAsField2() {
        assertArrayEquals(bytes(0x08, 0x00, 0x10, 0x00), videoStart(0))
    }

    @Test
    fun theStartCarriesAnotherConfigIndex() {
        assertArrayEquals(bytes(0x08, 0x00, 0x10, 0x01), videoStart(1))
    }

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }
}
