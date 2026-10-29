package io.github.octoclonius.libreauto.projection.tasks

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class OpenCarTasksTest {

    @Test
    fun openTasksAreKeptAsSaved() {
        val tasks = OpenTasks(listOf(42, 7, 1300), 7)
        assertEquals(tasks, tasks.sanitized())
    }

    @Test
    fun theHomeInFrontIsKept() {
        val tasks = OpenTasks(listOf(42, 7), null)
        assertEquals(tasks, tasks.sanitized())
    }

    @Test
    fun repeatedTasksAreKeptOnce() {
        assertEquals(
            OpenTasks(listOf(42, 7), 42),
            OpenTasks(listOf(42, 7, 42), 42).sanitized(),
        )
    }

    @Test
    fun aFrontTaskOutsideTheTasksIsDropped() {
        assertEquals(OpenTasks(listOf(42), null), OpenTasks(listOf(42), 9).sanitized())
    }
}
