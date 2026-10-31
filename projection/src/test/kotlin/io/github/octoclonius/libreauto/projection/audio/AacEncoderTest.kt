package io.github.octoclonius.libreauto.projection.audio

import io.github.octoclonius.libreauto.protocol.media.PcmConfig
import org.junit.jupiter.api.Assertions.assertEquals
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
}
