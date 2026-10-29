package io.github.octoclonius.libreauto.projection.video

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class H264EncoderTest {

    private val reference = H264Encoder.REFERENCE_FRAMES_PER_SECOND

    @Test
    fun theBitRateGrowsPerPixelAboveTheBase() {
        val pixels = 1920 * 1080
        assertEquals(
            (pixels * H264Encoder.BITS_PER_PIXEL_SECOND + H264Encoder.BASE_BIT_RATE).toInt(),
            H264Encoder.bitRate(pixels, reference),
        )
    }

    @Test
    fun aSmallDisplayGetsTheMinimum() {
        assertEquals(H264Encoder.MIN_BIT_RATE, H264Encoder.bitRate(100 * 100, reference))
    }

    @Test
    fun theBitRateIsProportionalToTheFrameRate() {
        val pixels = 800 * 480
        assertEquals(H264Encoder.bitRate(pixels, reference) * 2, H264Encoder.bitRate(pixels, 60))
    }

    @Test
    fun theMinimumIsProportionalToTheFrameRateToo() {
        assertEquals(H264Encoder.MIN_BIT_RATE * 2, H264Encoder.bitRate(100 * 100, 60))
    }
}
