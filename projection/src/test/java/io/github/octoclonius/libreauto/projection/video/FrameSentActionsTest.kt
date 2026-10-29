package io.github.octoclonius.libreauto.projection.video

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FrameSentActionsTest {

    private val actions = FrameSentActions()
    private val ran = mutableListOf<String>()

    @Test
    fun anActionWaitsForItsFrame() {
        actions.afterFrame(2_000) { ran += "reply" }
        actions.onFrameSent(1_000)
        assertEquals(emptyList<String>(), ran)
        actions.onFrameSent(2_000)
        assertEquals(listOf("reply"), ran)
    }

    @Test
    fun aLaterFrameStandsInForADroppedOne() {
        actions.afterFrame(2_000) { ran += "reply" }
        actions.onFrameSent(3_000)
        assertEquals(listOf("reply"), ran)
    }

    @Test
    fun anAlreadySentFrameRunsTheActionAtOnce() {
        actions.onFrameSent(2_000)
        actions.afterFrame(2_000) { ran += "reply" }
        assertEquals(listOf("reply"), ran)
    }

    @Test
    fun actionsRunInOrder() {
        actions.afterFrame(1_000) { ran += "first" }
        actions.afterFrame(2_000) { ran += "second" }
        actions.onFrameSent(2_000)
        assertEquals(listOf("first", "second"), ran)
    }

    @Test
    fun closingRunsWaitingActions() {
        actions.afterFrame(1_000) { ran += "first" }
        actions.afterFrame(2_000) { ran += "second" }
        actions.close()
        assertEquals(listOf("first", "second"), ran)
    }

    @Test
    fun afterClosingActionsRunAtOnce() {
        actions.close()
        actions.afterFrame(1_000) { ran += "reply" }
        assertEquals(listOf("reply"), ran)
    }
}
