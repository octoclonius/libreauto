package io.github.octoclonius.libreauto.projection.calls

enum class AudioOutputType {
    Bluetooth,
    Speaker,
    WiredHeadset,
    Earpiece,
}

data class AudioOutput(val id: String, val type: AudioOutputType, val name: String)

data class CallAudio(
    val isMuted: Boolean,
    val output: AudioOutput?,
    val outputs: List<AudioOutput>,
)
