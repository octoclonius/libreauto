package io.github.octoclonius.libreauto.projection.audio

import android.media.AudioAttributes
import android.media.AudioManager
import io.github.octoclonius.libreauto.privileged.api.AudioFocusHolder
import io.github.octoclonius.libreauto.privileged.api.AudioPlayer
import io.github.octoclonius.libreauto.protocol.control.AudioFocusRequestType
import io.github.octoclonius.libreauto.protocol.media.AudioType

internal fun divertedUsages(audioType: Int): IntArray? =
    when (audioType) {
        AudioType.MEDIA -> intArrayOf(AudioAttributes.USAGE_MEDIA, AudioAttributes.USAGE_GAME)
        AudioType.GUIDANCE ->
            intArrayOf(
                AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE,
                AudioAttributes.USAGE_ASSISTANT,
            )
        else -> null
    }

internal fun isCountedFocus(
    holder: AudioFocusHolder,
    ownUid: Int,
    isDiverted: (usage: Int) -> Boolean,
): Boolean =
    holder.uid != ownUid &&
        isDiverted(
            if (holder.usage == AudioAttributes.USAGE_UNKNOWN) AudioAttributes.USAGE_MEDIA
            else holder.usage
        )

internal fun audioFrameSamples(audioType: Int): Int? =
    when (audioType) {
        AudioType.MEDIA -> 2048
        AudioType.GUIDANCE -> 1024
        else -> null
    }

internal fun isMediaPlaying(players: List<AudioPlayer>): Boolean = players.any {
    it.usage == AudioAttributes.USAGE_MEDIA
}

internal fun carFocusRequest(
    players: List<AudioPlayer>,
    focusHolders: List<AudioFocusHolder>,
): Int? {
    val holderUids = focusHolders.mapTo(HashSet()) { it.uid }
    val requests =
        focusHolders.mapNotNull { requestForGain(it.gain) } +
            players.filter { it.uid !in holderUids }.map { requestForUsage(it.usage) }
    return requests.maxByOrNull(::requestRank)
}

internal fun requestRank(request: Int): Int =
    when (request) {
        AudioFocusRequestType.GAIN -> 3
        AudioFocusRequestType.GAIN_TRANSIENT -> 2
        AudioFocusRequestType.GAIN_TRANSIENT_MAY_DUCK -> 1
        else -> 0
    }

private fun requestForGain(gain: Int): Int? =
    when (gain) {
        AudioManager.AUDIOFOCUS_GAIN -> AudioFocusRequestType.GAIN
        AudioManager.AUDIOFOCUS_GAIN_TRANSIENT,
        AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE -> AudioFocusRequestType.GAIN_TRANSIENT
        AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK ->
            AudioFocusRequestType.GAIN_TRANSIENT_MAY_DUCK
        else -> null
    }

private fun requestForUsage(usage: Int): Int =
    when (usage) {
        AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE ->
            AudioFocusRequestType.GAIN_TRANSIENT_MAY_DUCK
        AudioAttributes.USAGE_ASSISTANT -> AudioFocusRequestType.GAIN_TRANSIENT
        else -> AudioFocusRequestType.GAIN
    }
