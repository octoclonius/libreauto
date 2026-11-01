package io.github.octoclonius.libreauto.protocol

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

enum class SendPriority {
    DEFAULT,
    AUDIO,
    VIDEO,
}

class SendQueue {

    private val lock = ReentrantLock()
    private val notEmpty = lock.newCondition()
    private val channels = HashMap<Int, ChannelQueue>()
    private val ready = ArrayDeque<ChannelQueue>()
    private var isEnded = false

    fun addChannel(channel: Int, priority: SendPriority) {
        lock.withLock {
            check(channel !in channels) { "Channel $channel was added twice" }
            channels[channel] = ChannelQueue(priority)
        }
    }

    fun offer(message: OutgoingMessage): Boolean = lock.withLock {
        if (isEnded) return false
        add(message)
        true
    }

    fun endWith(message: OutgoingMessage): Boolean = lock.withLock {
        if (isEnded) return false
        isEnded = true
        for ((channel, queue) in channels) {
            if (channel == message.channel) queue.clearUnstarted() else queue.clear()
        }
        ready.retainAll { it.hasFrames }
        add(message)
        true
    }

    fun end() {
        lock.withLock {
            if (isEnded) return
            isEnded = true
            for (queue in channels.values) queue.clear()
            ready.clear()
            notEmpty.signalAll()
        }
    }

    fun take(): OutgoingFrame? = lock.withLock {
        while (ready.isEmpty()) {
            if (isEnded) return null
            notEmpty.awaitUninterruptibly()
        }
        val next = ready.minBy { it.priority }
        ready.remove(next)
        val frame = next.nextFrame()
        if (next.hasFrames) ready.addLast(next)
        frame
    }

    private fun add(message: OutgoingMessage) {
        val queue =
            checkNotNull(channels[message.channel]) { "Channel ${message.channel} wasn't added" }
        queue.add(message)
        if (queue !in ready) ready.addLast(queue)
        notEmpty.signal()
    }

    private class ChannelQueue(val priority: SendPriority) {
        private val messages = ArrayDeque<OutgoingMessage>()
        private var started: Iterator<OutgoingFrame>? = null

        val hasFrames: Boolean
            get() = started != null || messages.isNotEmpty()

        fun add(message: OutgoingMessage) {
            messages.addLast(message)
        }

        fun nextFrame(): OutgoingFrame {
            val frames = started ?: messages.removeFirst().frames().iterator()
            val frame = frames.next()
            started = frames.takeIf { it.hasNext() }
            return frame
        }

        fun clearUnstarted() {
            messages.clear()
        }

        fun clear() {
            messages.clear()
            started = null
        }
    }
}
