package io.github.octoclonius.libreauto.session.audio

import io.github.octoclonius.libreauto.privileged.AudioFocusHolder
import io.github.octoclonius.libreauto.privileged.AudioPlayer
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class DivertedAudioTest {

    private val media = 1
    private val game = 14
    private val navigation = 12
    private val assistant = 16
    private val gain = 1
    private val gainTransient = 2
    private val gainTransientMayDuck = 3
    private val gainTransientExclusive = 4
    private val app = 10_100
    private val otherApp = 10_200

    @Test
    fun mediaTakesMediaAndGames() {
        assertArrayEquals(intArrayOf(media, game), divertedUsages(3))
    }

    @Test
    fun guidanceTakesNavigationAndTheAssistant() {
        assertArrayEquals(intArrayOf(navigation, assistant), divertedUsages(1))
    }

    @Test
    fun systemAudioTakesNothing() {
        assertNull(divertedUsages(2))
    }

    @Test
    fun nothingPlayingOrHoldingFocusNeedsNoFocus() {
        assertNull(carFocusRequest(emptyList(), emptyList()))
    }

    @Test
    fun aPlayerWithoutAndroidFocusAsksByItsUsage() {
        assertEquals(1, carFocusRequest(listOf(player(app, media)), emptyList()))
        assertEquals(1, carFocusRequest(listOf(player(app, game)), emptyList()))
        assertEquals(3, carFocusRequest(listOf(player(app, navigation)), emptyList()))
        assertEquals(2, carFocusRequest(listOf(player(app, assistant)), emptyList()))
    }

    @Test
    fun anAppsAndroidFocusRequestDecidesOverItsPlayersUsage() {
        assertEquals(
            3,
            carFocusRequest(listOf(player(app, media)), listOf(holder(app, gainTransientMayDuck))),
        )
        assertEquals(
            1,
            carFocusRequest(listOf(player(app, navigation)), listOf(holder(app, gain))),
        )
    }

    @Test
    fun heldAndroidFocusNeedsCarFocusWithoutAPlayer() {
        assertEquals(1, carFocusRequest(emptyList(), listOf(holder(app, gain))))
        assertEquals(2, carFocusRequest(emptyList(), listOf(holder(app, gainTransient))))
    }

    @Test
    fun exclusiveTransientAndroidFocusIsTransientCarFocus() {
        assertEquals(2, carFocusRequest(emptyList(), listOf(holder(app, gainTransientExclusive))))
    }

    @Test
    fun theStrongestRequestWins() {
        assertEquals(
            1,
            carFocusRequest(listOf(player(app, navigation), player(otherApp, media)), emptyList()),
        )
        assertEquals(
            1,
            carFocusRequest(
                emptyList(),
                listOf(holder(app, gainTransientMayDuck), holder(otherApp, gain)),
            ),
        )
    }

    private fun player(uid: Int, usage: Int) = AudioPlayer(uid, uid, usage)

    private fun holder(uid: Int, gain: Int) =
        AudioFocusHolder("client $uid $gain", uid, media, gain)
}
