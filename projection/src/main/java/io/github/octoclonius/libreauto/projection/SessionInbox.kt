package io.github.octoclonius.libreauto.projection

import io.github.octoclonius.libreauto.protocol.ChannelMessage
import java.io.Closeable
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

internal class SessionInbox(
    private val scope: CoroutineScope,
    private val handle: (ChannelMessage) -> Unit,
) : Closeable {

    private val queue = Channel<ChannelMessage>(Channel.UNLIMITED)
    private var job: Job? = null

    @Volatile
    var failure: IOException? = null
        private set

    fun start(first: () -> Unit) {
        job = scope.launch {
            try {
                first()
                for (message in queue) handle(message)
            } catch (e: IOException) {
                failure = e
            }
        }
    }

    fun send(message: ChannelMessage) {
        queue.trySend(message)
    }

    override fun close() {
        queue.close()
        val running = job ?: return
        runBlocking { running.cancelAndJoin() }
    }
}
