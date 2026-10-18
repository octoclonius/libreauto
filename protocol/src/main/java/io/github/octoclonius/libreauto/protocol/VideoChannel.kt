package io.github.octoclonius.libreauto.protocol

private const val MAIN_DISPLAY_TYPE = 0

internal const val DEFAULT_DISPLAY_ID = 0

class VideoSink(val channel: Int, val displayId: Int, val configs: List<VideoConfig>)

fun videoSink(response: ServiceDiscoveryResponse): VideoSink? {
    val service =
        response.services.firstOrNull { it.media_sink_service?.isMainDisplayVideo == true }
            ?: return null
    val channel = service.id ?: return null
    val sink = service.media_sink_service ?: return null
    return VideoSink(channel, sink.display_id ?: DEFAULT_DISPLAY_ID, sink.video_configs)
}

private val MediaSinkService.isMainDisplayVideo: Boolean
    get() = video_configs.isNotEmpty() && (display_type ?: MAIN_DISPLAY_TYPE) == MAIN_DISPLAY_TYPE
