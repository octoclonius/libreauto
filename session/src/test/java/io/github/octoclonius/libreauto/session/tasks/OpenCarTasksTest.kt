package io.github.octoclonius.libreauto.session.tasks

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class OpenCarTasksTest {

    @Test
    fun taskIdsKeepTheirOrder() {
        assertEquals(listOf(42, 7, 1300), decodeTaskIds(encodeTaskIds(listOf(42, 7, 1300))))
    }

    @Test
    fun noTasksDecodeToNone() {
        assertEquals(emptyList<Int>(), decodeTaskIds(encodeTaskIds(emptyList())))
    }

    @Test
    fun nothingSavedDecodesToNone() {
        assertEquals(emptyList<Int>(), decodeTaskIds(null))
    }

    @Test
    fun damagedEntriesAndRepeatsAreSkipped() {
        assertEquals(listOf(42, 7), decodeTaskIds("42,x,,7,42"))
    }
}
