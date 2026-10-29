package io.github.octoclonius.libreauto.privileged

import java.lang.reflect.InvocationTargetException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TeardownTest {

    @Test
    fun everyStepRunsWhenAnEarlierOneFails() {
        val ran = mutableListOf<String>()
        val failed = mutableListOf<String>()
        closeAll({ action, _ -> failed += action }) {
            step("first") { ran += "first" }
            step("second") { throw IllegalStateException("second") }
            step("third") { ran += "third" }
        }
        assertEquals(listOf("first", "third"), ran)
        assertEquals(listOf("second"), failed)
    }

    @Test
    fun aReflectedCallReportsTheFailureInsideIt() {
        val failure = IllegalStateException("inside")
        var reported: Throwable? = null
        closeAll({ _, cause -> reported = cause }) {
            step("step") { throw InvocationTargetException(failure) }
        }
        assertEquals(failure, reported)
    }
}
