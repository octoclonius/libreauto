package io.github.octoclonius.libreauto.session.video

import android.media.MediaCodecInfo.CodecCapabilities
import android.media.MediaCodecInfo.CodecProfileLevel
import android.media.MediaCodecList
import android.media.MediaFormat

internal object H264Encoder {
    private const val BIT_RATE = 4_000_000
    private const val I_FRAME_INTERVAL_SECONDS = 1

    fun name(width: Int, height: Int, framesPerSecond: Int): String? =
        MediaCodecList(MediaCodecList.REGULAR_CODECS)
            .findEncoderForFormat(format(width, height, framesPerSecond))

    fun supports(width: Int, height: Int, framesPerSecond: Int): Boolean =
        name(width, height, framesPerSecond) != null

    fun format(width: Int, height: Int, framesPerSecond: Int): MediaFormat =
        MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_PROFILE, CodecProfileLevel.AVCProfileBaseline)
            setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
            setInteger(MediaFormat.KEY_FRAME_RATE, framesPerSecond)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL_SECONDS)
        }
}
