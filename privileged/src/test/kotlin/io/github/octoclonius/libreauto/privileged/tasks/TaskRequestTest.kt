package io.github.octoclonius.libreauto.privileged.tasks

import io.github.octoclonius.libreauto.privileged.api.NO_TASK
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class TaskRequestTest {

    @Test
    fun aTaskOnTheCarDisplayIsAccepted() {
        assertNull(taskRequestError(42, setOf(41, 42)))
    }

    @Test
    fun aTaskThatIsNotOnTheCarDisplayIsRefused() {
        assertNotNull(taskRequestError(43, setOf(41, 42)))
    }

    @Test
    fun tasksToBringBackAreAccepted() {
        assertNull(restoreRequestError(listOf(41, 42), 41))
    }

    @Test
    fun tasksWithoutAFrontTaskAreAccepted() {
        assertNull(restoreRequestError(listOf(41, 42), NO_TASK))
    }

    @Test
    fun noTasksAreRefused() {
        assertNotNull(restoreRequestError(emptyList(), NO_TASK))
    }

    @Test
    fun repeatedTasksAreRefused() {
        assertNotNull(restoreRequestError(listOf(41, 41), NO_TASK))
    }

    @Test
    fun aFrontTaskOutsideTheTasksIsRefused() {
        assertNotNull(restoreRequestError(listOf(41, 42), 43))
    }
}
