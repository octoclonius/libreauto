package io.github.octoclonius.libreauto.privileged

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class TaskRequestTest {

    @Test
    fun aTaskOnTheCarDisplayIsAccepted() {
        assertNull(taskRequestError(7, 42, 7, setOf(41, 42)))
    }

    @Test
    fun aTaskWithoutACarDisplayIsRefused() {
        assertNotNull(taskRequestError(7, 42, null, emptySet()))
    }

    @Test
    fun aTaskOnAnotherDisplayIsRefused() {
        assertNotNull(taskRequestError(0, 42, 7, setOf(42)))
    }

    @Test
    fun aTaskThatIsNotOnTheCarDisplayIsRefused() {
        assertNotNull(taskRequestError(7, 43, 7, setOf(41, 42)))
    }

    @Test
    fun tasksToBringBackAreAccepted() {
        assertNull(restoreRequestError(7, 7, listOf(41, 42), 41))
    }

    @Test
    fun tasksWithoutAFrontTaskAreAccepted() {
        assertNull(restoreRequestError(7, 7, listOf(41, 42), NO_TASK))
    }

    @Test
    fun tasksWithoutACarDisplayAreRefused() {
        assertNotNull(restoreRequestError(7, null, listOf(41), NO_TASK))
    }

    @Test
    fun tasksForAnotherDisplayAreRefused() {
        assertNotNull(restoreRequestError(0, 7, listOf(41), NO_TASK))
    }

    @Test
    fun noTasksAreRefused() {
        assertNotNull(restoreRequestError(7, 7, emptyList(), NO_TASK))
    }

    @Test
    fun repeatedTasksAreRefused() {
        assertNotNull(restoreRequestError(7, 7, listOf(41, 41), NO_TASK))
    }

    @Test
    fun aFrontTaskOutsideTheTasksIsRefused() {
        assertNotNull(restoreRequestError(7, 7, listOf(41, 42), 43))
    }
}
