package io.github.octoclonius.libreauto.session.audio

import io.github.octoclonius.libreauto.privileged.api.AudioFocusHolder
import io.github.octoclonius.libreauto.privileged.api.AudioPlayer
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
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
    fun anotherAppsFocusWithAnUnknownUsageCountsAsMedia() {
        assertTrue(isCountedFocus(AudioFocusHolder("vlc", otherApp, 0, gain), app) { it == media })
    }

    @Test
    fun focusWithAnUndivertedUsageDoesntCount() {
        assertFalse(
            isCountedFocus(AudioFocusHolder("phone", otherApp, 6, gain), app) { it == media }
        )
    }

    @Test
    fun libreAutosOwnFocusNeverCounts() {
        assertFalse(isCountedFocus(AudioFocusHolder("libreauto", app, 0, gain), app) { true })
    }

    @Test
    fun onlyMediaPlayersCountAsMediaPlaying() {
        assertFalse(isMediaPlaying(emptyList()))
        assertTrue(isMediaPlaying(listOf(player(app, media))))
        assertFalse(isMediaPlaying(listOf(player(app, game), player(otherApp, navigation))))
        assertTrue(isMediaPlaying(listOf(player(app, game), player(otherApp, media))))
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
