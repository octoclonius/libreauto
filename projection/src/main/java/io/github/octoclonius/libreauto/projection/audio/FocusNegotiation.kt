package io.github.octoclonius.libreauto.projection.audio

import android.media.AudioManager
import io.github.octoclonius.libreauto.privileged.api.AudioFocusHolder
import io.github.octoclonius.libreauto.privileged.api.AudioPlayer
import io.github.octoclonius.libreauto.protocol.control.AudioFocusRequestType
import io.github.octoclonius.libreauto.protocol.control.AudioFocusState
import io.github.octoclonius.libreauto.protocol.media.AudioType

internal class FocusNegotiation {

    sealed interface Action {

        data class RequestCarFocus(val type: Int) : Action

        data class RequestAndroidFocus(val gain: Int) : Action

        data object AbandonAndroidFocus : Action
    }

    private companion object {
        val LOSSES =
            setOf(
                AudioFocusState.LOSS,
                AudioFocusState.LOSS_TRANSIENT,
                AudioFocusState.LOSS_TRANSIENT_CAN_DUCK,
            )
    }

    var carState: Int? = null
        private set

    private var pending: Int? = null
    private var demand: Int? = null
    private var sources: Set<String> = emptySet()
    private var yieldedSources: Set<String> = emptySet()
    private var droppedSources: Set<String> = emptySet()
    private var isInCall = false
    private var isIdle = true
    private var isReleaseOwed = false

    fun onActivity(players: List<AudioPlayer>, focusHolders: List<AudioFocusHolder>): List<Action> {
        sources =
            players.mapTo(HashSet()) { "player ${it.id}" } +
                focusHolders.map { "focus ${it.clientId}" }
        val previous = demand
        demand = carFocusRequest(players, focusHolders)
        if (demand == null) {
            yieldedSources = emptySet()
            droppedSources = emptySet()
        }
        isReleaseOwed = demand == null && (previous != null || isReleaseOwed)
        return listOfNotNull(nextRequest())
    }

    fun onCall(isInCall: Boolean): List<Action> {
        this.isInCall = isInCall
        return listOfNotNull(nextRequest())
    }

    fun onIdle(isIdle: Boolean): List<Action> {
        this.isIdle = isIdle
        return listOfNotNull(nextRequest())
    }

    fun onCarFocus(state: Int, isUnsolicited: Boolean): List<Action> {
        val isReleaseAnswer =
            pending == AudioFocusRequestType.RELEASE && state == AudioFocusState.LOSS
        pending = null
        carState = state
        if (state in LOSSES && !isReleaseAnswer) yieldedSources = sources
        val android = if (isReleaseAnswer) null else androidFocusFor(state, isUnsolicited)
        return listOfNotNull(android, nextRequest())
    }

    private fun nextRequest(): Action? {
        if (pending != null) return null
        val wanted = demand
        val request =
            when {
                wanted != null ->
                    wanted.takeIf {
                        requestRank(it) > heldRank() &&
                            !hasYieldedToTheCar() &&
                            !isDroppedForCall(it)
                    }
                isReleaseOwed && heldRank() > 0 && isIdle -> AudioFocusRequestType.RELEASE
                else -> null
            } ?: return null
        pending = request
        if (request == AudioFocusRequestType.RELEASE) isReleaseOwed = false
        return Action.RequestCarFocus(request)
    }

    private fun isDroppedForCall(request: Int): Boolean {
        if (
            isInCall &&
                (request == AudioFocusRequestType.GAIN ||
                    carState == AudioFocusState.LOSS_TRANSIENT)
        ) {
            droppedSources = sources
            return true
        }
        return droppedSources.isNotEmpty() && droppedSources.containsAll(sources)
    }

    private fun hasYieldedToTheCar(): Boolean =
        carState in LOSSES && yieldedSources.containsAll(sources)

    private fun heldRank(): Int =
        when (carState) {
            AudioFocusState.GAIN,
            AudioFocusState.GAIN_MEDIA_ONLY -> requestRank(AudioFocusRequestType.GAIN)
            AudioFocusState.GAIN_TRANSIENT -> requestRank(AudioFocusRequestType.GAIN_TRANSIENT)
            AudioFocusState.GAIN_TRANSIENT_GUIDANCE_ONLY ->
                requestRank(AudioFocusRequestType.GAIN_TRANSIENT_MAY_DUCK)
            else -> 0
        }
}

internal fun isStreamAllowed(audioType: Int, carState: Int?): Boolean =
    when (carState) {
        AudioFocusState.GAIN,
        AudioFocusState.GAIN_TRANSIENT,
        AudioFocusState.LOSS_TRANSIENT_CAN_DUCK -> true
        AudioFocusState.GAIN_MEDIA_ONLY -> audioType == AudioType.MEDIA
        AudioFocusState.GAIN_TRANSIENT_GUIDANCE_ONLY -> audioType == AudioType.GUIDANCE
        else -> false
    }

private fun androidFocusFor(state: Int, isUnsolicited: Boolean): FocusNegotiation.Action? =
    when (state) {
        AudioFocusState.LOSS ->
            FocusNegotiation.Action.RequestAndroidFocus(AudioManager.AUDIOFOCUS_GAIN)
        AudioFocusState.LOSS_TRANSIENT ->
            FocusNegotiation.Action.RequestAndroidFocus(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        AudioFocusState.LOSS_TRANSIENT_CAN_DUCK ->
            FocusNegotiation.Action.RequestAndroidFocus(
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            )
        AudioFocusState.GAIN_TRANSIENT_GUIDANCE_ONLY ->
            if (isUnsolicited) {
                FocusNegotiation.Action.RequestAndroidFocus(AudioManager.AUDIOFOCUS_GAIN)
            } else {
                null
            }
        AudioFocusState.GAIN,
        AudioFocusState.GAIN_TRANSIENT,
        AudioFocusState.GAIN_MEDIA_ONLY -> FocusNegotiation.Action.AbandonAndroidFocus
        else -> null
    }
