package io.github.octoclonius.libreauto.projection.channel

import android.os.Process
import android.util.Log
import io.github.octoclonius.libreauto.protocol.FrameWriter
import io.github.octoclonius.libreauto.protocol.OutgoingMessage
import io.github.octoclonius.libreauto.protocol.SendPriority
import io.github.octoclonius.libreauto.protocol.SendQueue
import java.io.Closeable
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

internal class CarLink(private val writer: FrameWriter) : Closeable {

    private companion object {
        const val TAG = "CarLink"
    }

    private val queue = SendQueue()
    private val lost = AtomicBoolean()
    private val writerThread =
        thread(start = false, isDaemon = true, name = "car-writer") { writeFrames() }

    val isLost: Boolean
        get() = lost.get()

    fun start() {
        writerThread.start()
    }

    fun sender(channel: Int, priority: SendPriority): ChannelSender {
        queue.addChannel(channel, priority)
        return ChannelSender(this, channel)
    }

    fun send(message: OutgoingMessage): Boolean = queue.offer(message)

    fun sendLast(message: OutgoingMessage): Boolean = queue.endWith(message)

    override fun close() {
        queue.end()
        writerThread.join()
    }

    private fun writeFrames() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        while (true) {
            val frame = queue.take() ?: return
            try {
                writer.write(frame)
            } catch (e: IOException) {
                Log.w(TAG, "Sending on channel ${frame.channel} failed", e)
                lost.set(true)
                queue.end()
                return
            }
        }
    }
}
