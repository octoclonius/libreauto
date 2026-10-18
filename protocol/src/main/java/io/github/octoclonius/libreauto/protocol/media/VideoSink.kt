package io.github.octoclonius.libreauto.protocol.media

import io.github.octoclonius.libreauto.protocol.control.DEFAULT_DISPLAY_ID
import io.github.octoclonius.libreauto.protocol.control.MediaSinkService
import io.github.octoclonius.libreauto.protocol.control.ServiceDiscoveryResponse
import io.github.octoclonius.libreauto.protocol.control.VideoConfig

private const val MAIN_DISPLAY_TYPE = 0

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
