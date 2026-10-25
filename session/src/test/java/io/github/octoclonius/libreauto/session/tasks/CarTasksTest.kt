package io.github.octoclonius.libreauto.session.tasks

import io.github.octoclonius.libreauto.privileged.DisplayTask
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CarTasksTest {

    private val home = "io.github.octoclonius.libreauto"

    @Test
    fun theHomeIsLeftOut() {
        val state = carTasks(listOf(DisplayTask(1, "maps"), DisplayTask(2, home)), home)
        assertEquals(listOf(CarTask(1, "maps")), state.tasks)
    }

    @Test
    fun theTopAppIsInFront() {
        val state = carTasks(listOf(DisplayTask(1, "maps"), DisplayTask(2, "music")), home)
        assertEquals(1, state.frontTaskId)
    }

    @Test
    fun nothingIsInFrontWhileTheHomeIsOnTop() {
        val state = carTasks(listOf(DisplayTask(2, home), DisplayTask(1, "maps")), home)
        assertEquals(null, state.frontTaskId)
    }

    @Test
    fun noTasksMeansNothingInFront() {
        assertEquals(CarTasksState.NONE, carTasks(emptyList(), home))
    }
}
