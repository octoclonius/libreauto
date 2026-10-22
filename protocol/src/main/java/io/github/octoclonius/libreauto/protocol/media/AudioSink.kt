package io.github.octoclonius.libreauto.protocol.media

import io.github.octoclonius.libreauto.protocol.control.AudioConfig
import io.github.octoclonius.libreauto.protocol.control.ServiceDiscoveryResponse

private const val CODEC_PCM = 1
private const val BIT_DEPTH_16 = 16
private const val MAX_CHANNELS = 2

object AudioType {
    const val GUIDANCE = 1
    const val SYSTEM = 2
    const val MEDIA = 3
}

class PcmConfig(val index: Int, val sampleRate: Int, val channelCount: Int)

class AudioSink(val channel: Int, val configs: List<PcmConfig>)

fun audioSink(response: ServiceDiscoveryResponse, type: Int): AudioSink? {
    for (service in response.services) {
        val sink = service.media_sink_service ?: continue
        val channel = service.id ?: continue
        if (sink.audio_type != type || (sink.codec ?: CODEC_PCM) != CODEC_PCM) continue
        val configs =
            sink.audio_configs.mapIndexedNotNull { index, config -> pcmConfig(index, config) }
        if (configs.isNotEmpty()) return AudioSink(channel, configs)
    }
    return null
}

private fun pcmConfig(index: Int, config: AudioConfig): PcmConfig? {
    val sampleRate = config.sample_rate ?: return null
    val channelCount = config.channel_count ?: return null
    if (sampleRate <= 0 || config.bit_depth != BIT_DEPTH_16) return null
    if (channelCount !in 1..MAX_CHANNELS) return null
    return PcmConfig(index, sampleRate, channelCount)
}

fun audioSetupRequest(): ByteArray = MediaSetupRequest(codec = CODEC_PCM).encode()

fun pcmFrameBytes(config: PcmConfig, frameSamples: Int): Int =
    frameSamples * config.channelCount * BIT_DEPTH_16 / 8
