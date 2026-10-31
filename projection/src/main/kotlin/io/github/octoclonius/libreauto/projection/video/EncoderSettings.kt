package io.github.octoclonius.libreauto.projection.video

import android.media.MediaCodecInfo.CodecCapabilities
import android.media.MediaCodecInfo.CodecProfileLevel
import android.media.MediaCodecList
import android.media.MediaFormat
import io.github.octoclonius.libreauto.protocol.media.VideoCodec
import io.github.octoclonius.libreauto.protocol.media.VideoMode

internal object EncoderSettings {
    internal const val REFERENCE_FRAMES_PER_SECOND = 30
    internal const val H264_BITS_PER_PIXEL_SECOND = 2.75
    internal const val H264_BASE_BIT_RATE = 1_500_000
    internal const val H265_BITS_PER_PIXEL_SECOND = 0.46
    internal const val H265_BASE_BIT_RATE = 3_500_000
    internal const val MIN_BIT_RATE = 2_000_000
    private const val I_FRAME_INTERVAL_SECONDS = 60

    fun bitRate(codec: VideoCodec, visiblePixels: Int, framesPerSecond: Int): Int {
        val scaled =
            when (codec) {
                VideoCodec.H264 -> visiblePixels * H264_BITS_PER_PIXEL_SECOND + H264_BASE_BIT_RATE
                VideoCodec.H265 -> visiblePixels * H265_BITS_PER_PIXEL_SECOND + H265_BASE_BIT_RATE
            }.toInt()
        return maxOf(scaled, MIN_BIT_RATE) * framesPerSecond / REFERENCE_FRAMES_PER_SECOND
    }

    fun name(format: MediaFormat): String? =
        MediaCodecList(MediaCodecList.REGULAR_CODECS).findEncoderForFormat(format)

    fun isEncodable(mode: VideoMode): Boolean {
        val bitRate = bitRate(mode.codec, mode.width * mode.height, mode.framesPerSecond)
        return name(format(mode, bitRate)) != null
    }

    fun format(mode: VideoMode, bitRate: Int): MediaFormat =
        when (mode.codec) {
            VideoCodec.H264 ->
                MediaFormat.createVideoFormat(
                        MediaFormat.MIMETYPE_VIDEO_AVC,
                        mode.width,
                        mode.height,
                    )
                    .apply {
                        setInteger(MediaFormat.KEY_PROFILE, CodecProfileLevel.AVCProfileBaseline)
                    }
            VideoCodec.H265 ->
                MediaFormat.createVideoFormat(
                        MediaFormat.MIMETYPE_VIDEO_HEVC,
                        mode.width,
                        mode.height,
                    )
                    .apply {
                        setInteger(MediaFormat.KEY_PROFILE, CodecProfileLevel.HEVCProfileMain)
                        setInteger(MediaFormat.KEY_LEVEL, hevcLevel(mode))
                        setInteger(MediaFormat.KEY_COLOR_STANDARD, MediaFormat.COLOR_STANDARD_BT709)
                    }
        }.apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
            setInteger(MediaFormat.KEY_FRAME_RATE, mode.framesPerSecond)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL_SECONDS)
        }

    private fun hevcLevel(mode: VideoMode): Int {
        val longSide = maxOf(mode.width, mode.height)
        val isFast = mode.framesPerSecond > REFERENCE_FRAMES_PER_SECOND
        return when {
            longSide <= 800 -> CodecProfileLevel.HEVCMainTierLevel31
            longSide <= 1280 -> CodecProfileLevel.HEVCMainTierLevel4
            longSide <= 1920 -> CodecProfileLevel.HEVCMainTierLevel41
            isFast -> CodecProfileLevel.HEVCMainTierLevel52
            longSide <= 2560 -> CodecProfileLevel.HEVCMainTierLevel5
            else -> CodecProfileLevel.HEVCMainTierLevel51
        }
    }
}
