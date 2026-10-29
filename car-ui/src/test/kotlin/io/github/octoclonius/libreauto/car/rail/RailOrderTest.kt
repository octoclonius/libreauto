package io.github.octoclonius.libreauto.car.rail

import io.github.octoclonius.libreauto.projection.tasks.CarTask
import io.github.octoclonius.libreauto.projection.tasks.CarTasksState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RailOrderTest {

    private val maps = CarTask(1, "maps")
    private val music = CarTask(2, "music")
    private val podcasts = CarTask(3, "podcasts")
    private val messages = CarTask(4, "messages")
    private val video = CarTask(5, "video")

    @Test
    fun aNewAppGoesOnTop() {
        val order = railOrder(listOf(maps), CarTasksState(listOf(music, maps), 2), MAX_RAIL_APPS)
        assertEquals(listOf(music, maps), order)
    }

    @Test
    fun switchingToAShownAppKeepsTheOrder() {
        val order =
            railOrder(listOf(music, maps), CarTasksState(listOf(maps, music), 1), MAX_RAIL_APPS)
        assertEquals(listOf(music, maps), order)
    }

    @Test
    fun aClosedAppLeavesAndTheRestMoveUp() {
        val order =
            railOrder(listOf(music, maps, podcasts), CarTasksState(listOf(music, podcasts), 2), 2)
        assertEquals(listOf(music, podcasts), order)
    }

    @Test
    fun switchingToAnAppBeyondTheShownSlotsMovesItOnTop() {
        val order =
            railOrder(
                listOf(video, messages, podcasts, music, maps),
                CarTasksState(listOf(maps, video, messages, podcasts, music), 1),
                MAX_RAIL_APPS,
            )
        assertEquals(listOf(maps, video, messages, podcasts, music), order)
    }

    @Test
    fun theHomeInFrontKeepsTheOrder() {
        val order =
            railOrder(listOf(music, maps), CarTasksState(listOf(maps, music), null), MAX_RAIL_APPS)
        assertEquals(listOf(music, maps), order)
    }

    @Test
    fun aClosedAppLeavesBeforeTheCarReportsIt() {
        val state = withoutClosed(CarTasksState(listOf(maps, music), 1), setOf(1))
        assertEquals(CarTasksState(listOf(music), null), state)
    }

    @Test
    fun theRememberedOrderComesBackAtPlugIn() {
        val order =
            nextRailOrder(
                emptyList(),
                listOf(music.taskId, maps.taskId),
                CarTasksState(listOf(maps, music), 1),
                MAX_RAIL_APPS,
            )
        assertEquals(listOf(music, maps), order)
    }

    @Test
    fun theCurrentOrderWinsOverTheRememberedOne() {
        val order =
            nextRailOrder(
                listOf(music),
                listOf(maps.taskId, music.taskId),
                CarTasksState(listOf(music, maps), 2),
                MAX_RAIL_APPS,
            )
        assertEquals(listOf(maps, music), order)
    }

    @Test
    fun rememberedAppsThatAreGoneAreSkipped() {
        val order =
            nextRailOrder(
                emptyList(),
                listOf(podcasts.taskId, music.taskId, maps.taskId),
                CarTasksState(listOf(maps, music), 1),
                MAX_RAIL_APPS,
            )
        assertEquals(listOf(music, maps), order)
    }
}
