package io.github.octoclonius.libreauto.protocol

private const val MAIN_DISPLAY_TYPE = 0

fun videoChannel(response: ServiceDiscoveryResponse): Int? =
    response.services.firstOrNull { it.media_sink_service?.isMainDisplayVideo == true }?.id

private val MediaSinkService.isMainDisplayVideo: Boolean
    get() = video_configs.isNotEmpty() && (display_type ?: MAIN_DISPLAY_TYPE) == MAIN_DISPLAY_TYPE
