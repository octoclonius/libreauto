package io.github.octoclonius.libreauto.session.audio

import io.github.octoclonius.libreauto.privileged.api.AudioFocusHolder
import io.github.octoclonius.libreauto.privileged.api.AudioPlayer
import io.github.octoclonius.libreauto.session.audio.FocusNegotiation.Action
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FocusNegotiationTest {

    private val requestGain = 1
    private val requestTransient = 2
    private val requestMayDuck = 3
    private val release = 4
    private val gain = 1
    private val gainTransient = 2
    private val loss = 3
    private val lossTransientCanDuck = 4
    private val lossTransient = 5
    private val gainMediaOnly = 6
    private val gainGuidanceOnly = 7
    private val guidanceType = 1
    private val mediaType = 3

    private val media = 1
    private val navigation = 12
    private val androidGain = 1
    private val androidGainTransient = 2
    private val androidGainTransientMayDuck = 3

    private val negotiation = FocusNegotiation()

    @Test
    fun aPlayerAsksTheCarForFocus() {
        assertEquals(listOf(Action.RequestCarFocus(requestGain)), playing(player(1, media)))
    }

    @Test
    fun nothingMoreIsAskedWhileTheCarHasntAnswered() {
        playing(player(1, media))
        assertEquals(emptyList<Action>(), playing(player(1, media), player(2, navigation)))
    }

    @Test
    fun theCarsGainAbandonsAndroidFocusHeldForIt() {
        playing(player(1, media))
        assertEquals(listOf(Action.AbandonAndroidFocus), negotiation.onCarFocus(gain, false))
    }

    @Test
    fun heldFocusIsUpgradedButNeverDowngraded() {
        playing(player(1, navigation))
        negotiation.onCarFocus(gainTransient, false)
        assertEquals(
            listOf(Action.RequestCarFocus(requestGain)),
            playing(player(1, navigation), player(2, media)),
        )
        negotiation.onCarFocus(gain, false)
        assertEquals(emptyList<Action>(), playing(player(1, navigation)))
    }

    @Test
    fun focusIsReleasedOnceNothingPlaysAndTheStreamsAreIdle() {
        playing(player(1, media))
        negotiation.onCarFocus(gain, false)
        negotiation.onIdle(false)
        assertEquals(emptyList<Action>(), playing())
        assertEquals(listOf(Action.RequestCarFocus(release)), negotiation.onIdle(true))
    }

    @Test
    fun focusTheCarGrantsWhileNothingPlaysIsKeptUntilAPlayerStops() {
        assertEquals(listOf(Action.AbandonAndroidFocus), negotiation.onCarFocus(gain, true))
        assertEquals(emptyList<Action>(), negotiation.onIdle(true))
        playing(player(1, media))
        assertEquals(listOf(Action.RequestCarFocus(release)), playing())
    }

    @Test
    fun aPausedAppHoldingFocusKeepsCarFocus() {
        playing(player(1, media))
        negotiation.onCarFocus(gain, false)
        negotiation.onIdle(true)
        assertEquals(emptyList<Action>(), holding(holder("music", androidGain)))
    }

    @Test
    fun focusIsReleasedOnceTheAppAbandonsIt() {
        holding(holder("music", androidGain))
        negotiation.onCarFocus(gain, false)
        negotiation.onIdle(true)
        assertEquals(listOf(Action.RequestCarFocus(release)), holding())
    }

    @Test
    fun aHandoffBetweenAppsKeepsCarFocus() {
        holding(holder("music", androidGain))
        negotiation.onCarFocus(gain, false)
        negotiation.onIdle(true)
        assertEquals(emptyList<Action>(), holding(holder("video", androidGain)))
    }

    @Test
    fun theCarsLossAnsweringAReleaseIsntACarChange() {
        playing(player(1, media))
        negotiation.onCarFocus(gain, false)
        playing()
        assertEquals(emptyList<Action>(), negotiation.onCarFocus(loss, false))
    }

    @Test
    fun aPlayerStartingWhileAReleaseIsUnansweredAsksForFocusAgain() {
        playing(player(1, media))
        negotiation.onCarFocus(gain, false)
        playing()
        assertEquals(emptyList<Action>(), playing(player(2, media)))
        assertEquals(
            listOf(Action.RequestCarFocus(requestGain)),
            negotiation.onCarFocus(loss, false),
        )
    }

    @Test
    fun whenTheCarTakesFocusPhoneAppsLoseIt() {
        playing(player(1, media))
        negotiation.onCarFocus(gain, false)
        assertEquals(
            listOf(Action.RequestAndroidFocus(androidGain)),
            negotiation.onCarFocus(loss, true),
        )
        assertEquals(
            listOf(Action.RequestAndroidFocus(androidGainTransient)),
            negotiation.onCarFocus(lossTransient, true),
        )
        assertEquals(
            listOf(Action.RequestAndroidFocus(androidGainTransientMayDuck)),
            negotiation.onCarFocus(lossTransientCanDuck, true),
        )
    }

    @Test
    fun playersAlreadyPlayingDontTakeFocusBackFromTheCar() {
        playing(player(1, media))
        negotiation.onCarFocus(gain, false)
        negotiation.onCarFocus(loss, true)
        assertEquals(emptyList<Action>(), playing(player(1, media)))
    }

    @Test
    fun aNewPlayerAsksForFocusAfterTheCarTookIt() {
        playing(player(1, media))
        negotiation.onCarFocus(gain, false)
        negotiation.onCarFocus(loss, true)
        assertEquals(
            listOf(Action.RequestCarFocus(requestGain)),
            playing(player(1, media), player(2, media)),
        )
    }

    @Test
    fun aRefusedRequestYieldsLikeATakenFocus() {
        playing(player(1, media))
        assertEquals(
            listOf(Action.RequestAndroidFocus(androidGain)),
            negotiation.onCarFocus(loss, false),
        )
        assertEquals(emptyList<Action>(), playing(player(1, media)))
    }

    @Test
    fun anUnsolicitedGuidanceOnlyFocusStopsPhoneMedia() {
        assertEquals(
            listOf(Action.RequestAndroidFocus(androidGain)),
            negotiation.onCarFocus(gainGuidanceOnly, true),
        )
        assertEquals(emptyList<Action>(), negotiation.onCarFocus(gainGuidanceOnly, false))
    }

    @Test
    fun mediaOnlyFocusAbandonsAndroidFocus() {
        assertEquals(
            listOf(Action.AbandonAndroidFocus),
            negotiation.onCarFocus(gainMediaOnly, true),
        )
    }

    @Test
    fun inACallAGainRequestIsDropped() {
        negotiation.onCall(true)
        assertEquals(emptyList<Action>(), playing(player(1, media)))
        assertEquals(emptyList<Action>(), negotiation.onCall(false))
        assertEquals(emptyList<Action>(), playing(player(1, media)))
    }

    @Test
    fun aNewPlayerAfterTheCallAsksForFocus() {
        negotiation.onCall(true)
        playing(player(1, media))
        negotiation.onCall(false)
        assertEquals(
            listOf(Action.RequestCarFocus(requestGain)),
            playing(player(1, media), player(2, media)),
        )
    }

    @Test
    fun inACallTransientRequestsStillGoOut() {
        negotiation.onCall(true)
        assertEquals(
            listOf(Action.RequestCarFocus(requestMayDuck)),
            playing(player(1, navigation)),
        )
    }

    @Test
    fun inACallNothingIsAskedWhileTheCarHoldsFocusTransiently() {
        playing(player(1, media))
        negotiation.onCarFocus(gain, false)
        negotiation.onCall(true)
        negotiation.onCarFocus(lossTransient, true)
        assertEquals(
            emptyList<Action>(),
            playing(player(1, media), player(2, navigation)),
        )
        assertEquals(emptyList<Action>(), negotiation.onCall(false))
        assertEquals(
            listOf(Action.AbandonAndroidFocus),
            negotiation.onCarFocus(gain, true),
        )
    }

    @Test
    fun theCallEndingSendsNothingForADroppedRequest() {
        playing(player(1, media))
        negotiation.onCarFocus(gain, false)
        negotiation.onCall(true)
        negotiation.onCarFocus(lossTransient, true)
        playing(player(1, media), player(2, media))
        assertEquals(emptyList<Action>(), negotiation.onCall(false))
        assertEquals(
            emptyList<Action>(),
            playing(player(1, media), player(2, media)),
        )
    }

    @Test
    fun streamsMayPlayWhileTheCarGrantsTheirTypeOrDucks() {
        for (state in listOf(gain, gainTransient, lossTransientCanDuck)) {
            assertTrue(isStreamAllowed(mediaType, state))
            assertTrue(isStreamAllowed(guidanceType, state))
        }
        assertTrue(isStreamAllowed(mediaType, gainMediaOnly))
        assertFalse(isStreamAllowed(guidanceType, gainMediaOnly))
        assertTrue(isStreamAllowed(guidanceType, gainGuidanceOnly))
        assertFalse(isStreamAllowed(mediaType, gainGuidanceOnly))
    }

    @Test
    fun streamsStopWithoutCarFocus() {
        for (state in listOf(null, loss, lossTransient)) {
            assertFalse(isStreamAllowed(mediaType, state))
            assertFalse(isStreamAllowed(guidanceType, state))
        }
    }

    private fun playing(vararg players: AudioPlayer) =
        negotiation.onActivity(players.toList(), emptyList())

    private fun holding(vararg holders: AudioFocusHolder) =
        negotiation.onActivity(emptyList(), holders.toList())

    private fun player(id: Int, usage: Int) = AudioPlayer(id, 10_000 + id, usage)

    private fun holder(clientId: String, gain: Int) =
        AudioFocusHolder(clientId, 10_100, media, gain)
}
