package io.github.octoclonius.libreauto.projection.audio

import io.github.octoclonius.libreauto.protocol.media.PcmConfig
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class AacEncoderTest {

    @Test
    fun theBitRateIsHalfThePcmBitRate() {
        assertEquals(128_000, AacEncoder.bitRate(PcmConfig(0, 16000, 1)))
        assertEquals(352_800, AacEncoder.bitRate(PcmConfig(0, 44100, 1)))
    }

    @Test
    fun theBitRateIsCapped() {
        assertEquals(AacEncoder.MAX_BIT_RATE, AacEncoder.bitRate(PcmConfig(0, 48000, 2)))
    }

    @Test
    fun theAdtsHeaderIsMpeg2AacLcWithoutCrcAndHasTheFrameLength() {
        assertArrayEquals(
            bytes(0xFF, 0xF9, 0x4C, 0x80, 0x2F, 0x5F, 0xFC),
            AacEncoder.adtsHeader(PcmConfig(0, 48000, 2), 371),
        )
        assertArrayEquals(
            bytes(0xFF, 0xF9, 0x60, 0x40, 0x0D, 0x7F, 0xFC),
            AacEncoder.adtsHeader(PcmConfig(0, 16000, 1), 100),
        )
    }

    @Test
    fun theAdtsHeaderCarriesLengthsAboveElevenBits() {
        assertArrayEquals(
            bytes(0xFF, 0xF9, 0x50, 0x81, 0x00, 0x1F, 0xFC),
            AacEncoder.adtsHeader(PcmConfig(0, 44100, 2), 2048 - 7),
        )
    }

    @Test
    fun aSampleRateWithoutAnAdtsIndexIsNotAdtsEncodable() {
        assertFalse(AacEncoder.isAdtsEncodable(PcmConfig(0, 47999, 2)))
    }

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }
}
