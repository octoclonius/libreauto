package io.github.octoclonius.libreauto.protocol.media

private const val VIDEO_H264_BP = 3
private const val VIDEO_VP9 = 5
private const val VIDEO_AV1 = 6
private const val VIDEO_H265 = 7

enum class VideoCodec(internal val type: Int) {
    H264(VIDEO_H264_BP),
    VP9(VIDEO_VP9),
    H265(VIDEO_H265),
    AV1(VIDEO_AV1),
}

internal fun videoCodec(type: Int?): VideoCodec? =
    when (type) {
        null,
        VIDEO_H264_BP -> VideoCodec.H264
        VIDEO_VP9 -> VideoCodec.VP9
        VIDEO_H265 -> VideoCodec.H265
        VIDEO_AV1 -> VideoCodec.AV1
        else -> null
    }

internal fun efficiency(codec: VideoCodec): Int =
    when (codec) {
        VideoCodec.H264 -> 0
        VideoCodec.VP9 -> 1
        VideoCodec.H265 -> 2
        VideoCodec.AV1 -> 3
    }
