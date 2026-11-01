package io.github.octoclonius.libreauto.car.home

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HomeAppsTest {

    private val apps = listOf("maps", "music", "podcasts", "messages")

    @Test
    fun anAddedAppGoesWhereItWasDropped() {
        assertEquals(
            listOf("maps", "phone", "music", "podcasts", "messages"),
            withDropped(apps, "phone", null, 1),
        )
    }

    @Test
    fun anAddedAppPastTheEndIsAppended() {
        assertEquals(apps + "phone", withDropped(apps, "phone", null, 9))
    }

    @Test
    fun theSameAppCanBeAddedTwice() {
        assertEquals(apps + "maps", withDropped(apps, "maps", null, 4))
    }

    @Test
    fun anAppFromTheDrawerDroppedOnRemoveChangesNothing() {
        assertEquals(apps, withDropped(apps, "phone", null, null))
    }

    @Test
    fun anAppMovedForwardLandsOnTheTargetCell() {
        assertEquals(
            listOf("music", "podcasts", "maps", "messages"),
            withDropped(apps, "maps", 0, 2),
        )
    }

    @Test
    fun anAppMovedBackLandsOnTheTargetCell() {
        assertEquals(
            listOf("maps", "messages", "music", "podcasts"),
            withDropped(apps, "messages", 3, 1),
        )
    }

    @Test
    fun anAppMovedPastTheEndGoesLast() {
        assertEquals(
            listOf("music", "podcasts", "messages", "maps"),
            withDropped(apps, "maps", 0, 4),
        )
    }

    @Test
    fun aRemovedAppLeavesNoGap() {
        assertEquals(listOf("maps", "podcasts", "messages"), withDropped(apps, "music", 1, null))
    }

    @Test
    fun movingOrRemovingAMissingIndexChangesNothing() {
        assertEquals(apps, withDropped(apps, "maps", 7, 0))
        assertEquals(apps, withDropped(apps, "maps", 7, null))
    }

    @Test
    fun aDropOnACellGoesBeforeTheAppShownThere() {
        assertEquals(1, dropIndex(listOf(0, 1, 2, 3), null, 1))
        assertEquals(2, dropIndex(listOf(0, 1, 2, 3), 0, 2))
        assertEquals(1, dropIndex(listOf(0, 1, 2, 3), 3, 1))
    }

    @Test
    fun aDropPastTheLastAppGoesRightAfterIt() {
        assertEquals(4, dropIndex(listOf(0, 1, 2, 3), null, 9))
        assertEquals(3, dropIndex(listOf(0, 1, 2, 3), 0, 9))
        assertEquals(0, dropIndex(emptyList(), null, 0))
    }

    @Test
    fun aDropSkipsAppsTheHomeDoesNotShow() {
        assertEquals(2, dropIndex(listOf(0, 2, 3), null, 1))
        assertEquals(2, dropIndex(listOf(0, 2, 3), 0, 1))
        assertEquals(3, dropIndex(listOf(0, 2, 3), 0, 9))
    }
}
