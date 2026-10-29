package io.github.octoclonius.libreauto.privileged

import android.media.AudioAttributes
import io.github.octoclonius.libreauto.privileged.api.AudioInput
import io.github.octoclonius.libreauto.privileged.api.AudioOutput

private const val MAX_CHANNELS = 2

private val USAGES =
    setOf(
        AudioAttributes.USAGE_MEDIA,
        AudioAttributes.USAGE_GAME,
        AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE,
        AudioAttributes.USAGE_ASSISTANT,
    )

internal fun audioRequestError(outputs: List<AudioOutput>, input: AudioInput?): String? {
    if (outputs.isEmpty() && input == null) return "There are no audio outputs and no input"
    if (input != null) {
        if (input.sampleRate <= 0) {
            return "The input sample rate ${input.sampleRate} isn't positive"
        }
        if (input.channelCount !in 1..MAX_CHANNELS) {
            return "${input.channelCount} input channels aren't 1 or $MAX_CHANNELS"
        }
    }
    for (output in outputs) {
        val error =
            when {
                output.usages.isEmpty() -> "An audio output has no usages"
                output.usages.any { it !in USAGES } ->
                    "The usages ${output.usages.toList()} aren't ones that may be diverted"
                output.sampleRate <= 0 -> "The sample rate ${output.sampleRate} isn't positive"
                output.channelCount !in 1..MAX_CHANNELS ->
                    "${output.channelCount} channels aren't 1 or $MAX_CHANNELS"
                else -> null
            }
        if (error != null) return error
    }
    val usages = outputs.flatMap { it.usages.toList() }
    if (usages.size != usages.toSet().size) return "A usage is in more than one audio output"
    return null
}
