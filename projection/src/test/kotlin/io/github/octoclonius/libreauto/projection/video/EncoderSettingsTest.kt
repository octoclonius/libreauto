package io.github.octoclonius.libreauto.projection.video

import io.github.octoclonius.libreauto.protocol.media.VideoCodec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EncoderSettingsTest {

    private val reference = EncoderSettings.REFERENCE_FRAMES_PER_SECOND

    @Test
    fun theH264BitRateGrowsPerPixelAboveTheBase() {
        val pixels = 1920 * 1080
        assertEquals(
            (pixels * EncoderSettings.H264_BITS_PER_PIXEL_SECOND +
                    EncoderSettings.H264_BASE_BIT_RATE)
                .toInt(),
            EncoderSettings.bitRate(VideoCodec.H264, pixels, reference),
        )
    }

    @Test
    fun theH265BitRateGrowsPerPixelAboveItsOwnBase() {
        val pixels = 1920 * 1080
        assertEquals(
            (pixels * EncoderSettings.H265_BITS_PER_PIXEL_SECOND +
                    EncoderSettings.H265_BASE_BIT_RATE)
                .toInt(),
            EncoderSettings.bitRate(VideoCodec.H265, pixels, reference),
        )
    }

    @Test
    fun aSmallDisplayGetsTheMinimum() {
        assertEquals(
            EncoderSettings.MIN_BIT_RATE,
            EncoderSettings.bitRate(VideoCodec.H264, 100 * 100, reference),
        )
    }

    @Test
    fun theBitRateIsProportionalToTheFrameRate() {
        val pixels = 800 * 480
        for (codec in VideoCodec.entries) {
            assertEquals(
                EncoderSettings.bitRate(codec, pixels, reference) * 2,
                EncoderSettings.bitRate(codec, pixels, 60),
            )
        }
    }

    @Test
    fun theMinimumIsProportionalToTheFrameRateToo() {
        assertEquals(
            EncoderSettings.MIN_BIT_RATE * 2,
            EncoderSettings.bitRate(VideoCodec.H264, 100 * 100, 60),
        )
    }
}
