package io.github.octoclonius.libreauto.session.tasks

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class OpenCarTasksTest {

    private val none = OpenTasks(emptyList(), null)

    @Test
    fun openTasksSurviveSaving() {
        val tasks = OpenTasks(listOf(42, 7, 1300), 7)
        assertEquals(tasks, decodeOpenTasks(encodeOpenTasks(tasks)))
    }

    @Test
    fun theHomeInFrontSurvivesSaving() {
        val tasks = OpenTasks(listOf(42, 7), null)
        assertEquals(tasks, decodeOpenTasks(encodeOpenTasks(tasks)))
    }

    @Test
    fun nothingSavedMeansNoTasks() {
        assertEquals(none, decodeOpenTasks(null))
    }

    @Test
    fun somethingUnreadableMeansNoTasks() {
        assertEquals(none, decodeOpenTasks("42,7"))
    }

    @Test
    fun repeatedTasksAreKeptOnce() {
        assertEquals(
            OpenTasks(listOf(42, 7), 42),
            decodeOpenTasks(encodeOpenTasks(OpenTasks(listOf(42, 7, 42), 42))),
        )
    }

    @Test
    fun aFrontTaskOutsideTheTasksIsDropped() {
        assertEquals(
            OpenTasks(listOf(42), null),
            decodeOpenTasks(encodeOpenTasks(OpenTasks(listOf(42), 9))),
        )
    }
}
