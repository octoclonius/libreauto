package io.github.octoclonius.libreauto.projection

import io.github.octoclonius.libreauto.protocol.ChannelMessage
import java.io.IOException
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SessionInboxTest {

    private val dispatcher: ExecutorCoroutineDispatcher =
        Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val scope = CoroutineScope(dispatcher)
    private val handled = Collections.synchronizedList(mutableListOf<String>())

    @AfterEach
    fun tearDown() {
        dispatcher.close()
    }

    @Test
    fun startRunsFirstThenTheMessagesInOrder() {
        val done = CountDownLatch(3)
        val inbox =
            SessionInbox(scope) {
                handled += "message ${it.type}"
                done.countDown()
            }
        inbox.send(message(1))
        inbox.start { handled += "start" }
        inbox.send(message(2))
        inbox.send(message(3))
        assertTrue(done.await(5, TimeUnit.SECONDS))
        inbox.close()
        assertEquals(listOf("start", "message 1", "message 2", "message 3"), handled)
        assertNull(inbox.failure)
    }

    @Test
    fun aFailureIsKeptAndStopsTheHandling() {
        val broken = IOException("broken")
        val failing = CountDownLatch(1)
        val inbox =
            SessionInbox(scope) {
                handled += "message ${it.type}"
                failing.countDown()
                throw broken
            }
        inbox.start {}
        inbox.send(message(1))
        inbox.send(message(2))
        assertTrue(failing.await(5, TimeUnit.SECONDS))
        inbox.close()
        assertSame(broken, inbox.failure)
        assertEquals(listOf("message 1"), handled)
    }

    @Test
    fun closingWithoutStartingReturns() {
        val inbox = SessionInbox(scope) { handled += "message ${it.type}" }
        inbox.send(message(1))
        inbox.close()
        assertEquals(emptyList<String>(), handled)
    }

    private fun message(type: Int) =
        ChannelMessage(
            channel = 1,
            isEncrypted = true,
            type = type,
            body = ByteArray(0),
            receivedNanos = 0,
        )
}
