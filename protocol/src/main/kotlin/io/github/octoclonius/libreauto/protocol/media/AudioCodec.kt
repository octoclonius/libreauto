package io.github.octoclonius.libreauto.protocol.media

private const val AUDIO_PCM = 1

enum class AudioCodec(internal val type: Int) {
    PCM(AUDIO_PCM)
}

internal fun audioCodec(type: Int?): AudioCodec? =
    when (type) {
        null,
        AUDIO_PCM -> AudioCodec.PCM
        else -> null
    }
