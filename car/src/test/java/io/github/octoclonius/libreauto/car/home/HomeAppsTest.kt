package io.github.octoclonius.libreauto.car.home

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HomeAppsTest {

    private val apps = listOf("maps", "music", "podcasts", "messages")

    @Test
    fun anAddedAppGoesWhereItWasDropped() {
        assertEquals(
            listOf("maps", "phone", "music", "podcasts", "messages"),
            withAdded(apps, "phone", 1),
        )
    }

    @Test
    fun anAddedAppPastTheEndIsAppended() {
        assertEquals(apps + "phone", withAdded(apps, "phone", 9))
    }

    @Test
    fun theSameAppCanBeAddedTwice() {
        assertEquals(apps + "maps", withAdded(apps, "maps", 4))
    }

    @Test
    fun anAppMovedForwardLandsOnTheTargetCell() {
        assertEquals(listOf("music", "podcasts", "maps", "messages"), withMoved(apps, 0, 2))
    }

    @Test
    fun anAppMovedBackLandsOnTheTargetCell() {
        assertEquals(listOf("maps", "messages", "music", "podcasts"), withMoved(apps, 3, 1))
    }

    @Test
    fun anAppMovedPastTheEndGoesLast() {
        assertEquals(listOf("music", "podcasts", "messages", "maps"), withMoved(apps, 0, 4))
    }

    @Test
    fun aRemovedAppLeavesNoGap() {
        assertEquals(listOf("maps", "podcasts", "messages"), withRemoved(apps, 1))
    }

    @Test
    fun movingOrRemovingAMissingIndexChangesNothing() {
        assertEquals(apps, withMoved(apps, 7, 0))
        assertEquals(apps, withRemoved(apps, 7))
    }
}
