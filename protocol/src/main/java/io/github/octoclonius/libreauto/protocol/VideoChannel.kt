package io.github.octoclonius.libreauto.protocol

private const val MAIN_DISPLAY_TYPE = 0

class VideoSink(val channel: Int, val configs: List<VideoConfig>)

fun videoSink(response: ServiceDiscoveryResponse): VideoSink? {
    val service =
        response.services.firstOrNull { it.media_sink_service?.isMainDisplayVideo == true }
            ?: return null
    val channel = service.id ?: return null
    return VideoSink(channel, service.media_sink_service?.video_configs.orEmpty())
}

private val MediaSinkService.isMainDisplayVideo: Boolean
    get() = video_configs.isNotEmpty() && (display_type ?: MAIN_DISPLAY_TYPE) == MAIN_DISPLAY_TYPE
