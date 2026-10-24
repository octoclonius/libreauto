package io.github.octoclonius.libreauto.session.video

import android.media.MediaCodecInfo.CodecCapabilities
import android.media.MediaCodecInfo.CodecProfileLevel
import android.media.MediaCodecList
import android.media.MediaFormat

internal object H264Encoder {
    internal const val REFERENCE_FRAMES_PER_SECOND = 30
    internal const val BITS_PER_PIXEL_SECOND = 2.75
    internal const val BASE_BIT_RATE = 1_500_000
    internal const val MIN_BIT_RATE = 2_000_000
    private const val I_FRAME_INTERVAL_SECONDS = 60

    fun bitRate(visiblePixels: Int, framesPerSecond: Int): Int {
        val scaled = (visiblePixels * BITS_PER_PIXEL_SECOND + BASE_BIT_RATE).toInt()
        return maxOf(scaled, MIN_BIT_RATE) * framesPerSecond / REFERENCE_FRAMES_PER_SECOND
    }

    fun name(format: MediaFormat): String? =
        MediaCodecList(MediaCodecList.REGULAR_CODECS).findEncoderForFormat(format)

    fun supports(width: Int, height: Int, framesPerSecond: Int): Boolean {
        val bitRate = bitRate(width * height, framesPerSecond)
        return name(format(width, height, framesPerSecond, bitRate)) != null
    }

    fun format(width: Int, height: Int, framesPerSecond: Int, bitRate: Int): MediaFormat =
        MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_PROFILE, CodecProfileLevel.AVCProfileBaseline)
            setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
            setInteger(MediaFormat.KEY_FRAME_RATE, framesPerSecond)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL_SECONDS)
        }
}
