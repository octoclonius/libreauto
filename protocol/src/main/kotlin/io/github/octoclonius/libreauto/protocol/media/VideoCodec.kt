package io.github.octoclonius.libreauto.protocol.media

private const val VIDEO_H264_BP = 3
private const val VIDEO_H265 = 7

enum class VideoCodec(internal val type: Int) {
    H264(VIDEO_H264_BP),
    H265(VIDEO_H265),
}

internal fun videoCodec(type: Int?): VideoCodec? =
    when (type) {
        null,
        VIDEO_H264_BP -> VideoCodec.H264
        VIDEO_H265 -> VideoCodec.H265
        else -> null
    }
