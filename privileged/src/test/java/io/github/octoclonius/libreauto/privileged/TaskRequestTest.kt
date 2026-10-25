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
}
