package io.github.octoclonius.libreauto.projection.usb

import java.util.concurrent.Executor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AccessorySessionsTest {

    private class QueueExecutor : Executor {
        private val tasks = ArrayDeque<Runnable>()

        override fun execute(command: Runnable) {
            tasks.addLast(command)
        }

        fun runNext() = tasks.removeFirst().run()
    }

    private class FakeLink(
        private val isLost: Boolean = false,
        private val during: () -> Unit = {},
    ) : AccessoryLink {
        var runs = 0
        var isClosed = false
        var wasClosedWhileRunning = false

        override fun run(): Boolean {
            runs++
            during()
            wasClosedWhileRunning = isClosed
            return isLost
        }

        override fun close() {
            isClosed = true
        }
    }

    private val executor = QueueExecutor()
    private val stops = mutableListOf<Int>()
    private var linksLost = 0
    private val sessions = AccessorySessions<String>(executor, { stops += it }, { linksLost++ })

    @Test
    fun aSessionRunsItsLinkAndStopsWithItsStartId() {
        val link = FakeLink()
        sessions.start(1, "car") { link }
        executor.runNext()
        assertEquals(1, link.runs)
        assertTrue(link.isClosed)
        assertEquals(listOf(1), stops)
    }

    @Test
    fun aLinkThatDoesNotOpenStopsWithItsStartId() {
        sessions.start(1, "car") { null }
        executor.runNext()
        assertEquals(listOf(1), stops)
    }

    @Test
    fun aNewSessionClosesTheRunningOneAndEachStopsWithItsOwnStartId() {
        val second = FakeLink()
        val first = FakeLink(during = { sessions.start(2, "car") { second } })
        sessions.start(1, "car") { first }
        executor.runNext()
        assertTrue(first.wasClosedWhileRunning)
        assertEquals(listOf(1), stops)
        executor.runNext()
        assertEquals(1, second.runs)
        assertEquals(listOf(1, 2), stops)
    }

    @Test
    fun aSessionReplacedBeforeItRunsNeverOpens() {
        val first = FakeLink()
        sessions.start(1, "car") { first }
        sessions.start(2, "car") { FakeLink() }
        executor.runNext()
        assertEquals(0, first.runs)
        assertFalse(first.isClosed)
        assertEquals(listOf(1), stops)
        executor.runNext()
        assertEquals(listOf(1, 2), stops)
    }

    @Test
    fun detachingClosesTheRunningSession() {
        val link = FakeLink(during = { sessions.detach() })
        sessions.start(1, "car") { link }
        executor.runNext()
        assertTrue(link.wasClosedWhileRunning)
        assertEquals(listOf(1), stops)
    }

    @Test
    fun aDetachAfterTheSessionEndedDoesNotCloseTheNextOne() {
        sessions.start(1, "car") { FakeLink() }
        executor.runNext()
        sessions.detach()
        val link = FakeLink()
        sessions.start(2, "car") { link }
        executor.runNext()
        assertFalse(link.wasClosedWhileRunning)
        assertEquals(listOf(1, 2), stops)
    }

    @Test
    fun closingClosesTheRunningSession() {
        val link = FakeLink(during = { sessions.close() })
        sessions.start(1, "car") { link }
        executor.runNext()
        assertTrue(link.wasClosedWhileRunning)
        assertEquals(listOf(1), stops)
    }

    @Test
    fun aLinkLostOnItsOwnIsReported() {
        sessions.start(1, "car") { FakeLink(isLost = true) }
        executor.runNext()
        assertEquals(1, linksLost)
        assertEquals(listOf(1), stops)
    }

    @Test
    fun aLinkThatEndsWithoutBeingLostIsNotReported() {
        sessions.start(1, "car") { FakeLink(isLost = false) }
        executor.runNext()
        assertEquals(0, linksLost)
    }

    @Test
    fun aLinkClosedByUsIsNotReportedEvenIfItsReadFailed() {
        val detached = FakeLink(isLost = true, during = { sessions.detach() })
        sessions.start(1, "car") { detached }
        executor.runNext()
        val replaced = FakeLink(isLost = true, during = { sessions.start(3, "car") { FakeLink() } })
        sessions.start(2, "car") { replaced }
        executor.runNext()
        assertEquals(0, linksLost)
    }
}
