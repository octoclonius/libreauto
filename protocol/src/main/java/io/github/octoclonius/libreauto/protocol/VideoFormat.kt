package io.github.octoclonius.libreauto.protocol

private const val VIDEO_1280X720 = 2
private const val VIDEO_1920X1080 = 3
private const val VIDEO_2560X1440 = 4
private const val VIDEO_3840X2160 = 5
private const val VIDEO_720X1280 = 6
private const val VIDEO_1080X1920 = 7
private const val VIDEO_1440X2560 = 8
private const val VIDEO_2160X3840 = 9

private const val VIDEO_FPS_30 = 2

data class VideoFormat(val width: Int, val height: Int, val framesPerSecond: Int)

fun videoFormat(config: VideoConfig): VideoFormat {
    val (width, height) =
        when (config.resolution) {
            VIDEO_1280X720 -> 1280 to 720
            VIDEO_1920X1080 -> 1920 to 1080
            VIDEO_2560X1440 -> 2560 to 1440
            VIDEO_3840X2160 -> 3840 to 2160
            VIDEO_720X1280 -> 720 to 1280
            VIDEO_1080X1920 -> 1080 to 1920
            VIDEO_1440X2560 -> 1440 to 2560
            VIDEO_2160X3840 -> 2160 to 3840
            else -> 800 to 480
        }
    val framesPerSecond = if (config.frame_rate == VIDEO_FPS_30) 30 else 60
    return VideoFormat(width, height, framesPerSecond)
}
