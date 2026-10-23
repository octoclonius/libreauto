package io.github.octoclonius.libreauto.protocol.media

import io.github.octoclonius.libreauto.protocol.control.ServiceDiscoveryResponse
import java.io.IOException

class AudioSource(val channel: Int, val config: PcmConfig)

fun audioSource(response: ServiceDiscoveryResponse): AudioSource? {
    for (service in response.services) {
        val source = service.media_source_service ?: continue
        val channel = service.id ?: continue
        if ((source.codec ?: CODEC_PCM) != CODEC_PCM) continue
        val config = source.audio_config?.let { pcmConfig(0, it) } ?: continue
        return AudioSource(channel, config)
    }
    return null
}

fun microphoneRequest(isOpen: Boolean, maxUnacked: Int): ByteArray =
    if (isOpen) {
        MicrophoneRequest(
                is_open = true,
                noise_suppression = false,
                echo_cancellation = false,
                max_unacked = maxUnacked,
            )
            .encode()
    } else {
        MicrophoneRequest(is_open = false).encode()
    }

@Throws(IOException::class)
fun parseMicrophoneResponse(body: ByteArray): MicrophoneResponse =
    MicrophoneResponse.ADAPTER.decode(body)
