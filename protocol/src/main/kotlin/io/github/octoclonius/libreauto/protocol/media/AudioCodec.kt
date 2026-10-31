package io.github.octoclonius.libreauto.protocol.media

private const val AUDIO_PCM = 1
private const val AUDIO_AAC_LC = 2

enum class AudioCodec(internal val type: Int) {
    PCM(AUDIO_PCM),
    AAC_LC(AUDIO_AAC_LC),
}

internal fun audioCodec(type: Int?): AudioCodec? =
    when (type) {
        null,
        AUDIO_PCM -> AudioCodec.PCM
        AUDIO_AAC_LC -> AudioCodec.AAC_LC
        else -> null
    }
