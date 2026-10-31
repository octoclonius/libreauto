package io.github.octoclonius.libreauto.projection.channel

import android.util.Log
import io.github.octoclonius.libreauto.protocol.FrameWriter
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

internal class CarLink(
    private val writer: FrameWriter,
    private val encrypt: (ByteArray) -> ByteArray,
) {

    private companion object {
        const val TAG = "CarLink"
    }

    private val lock = Any()
    private val lost = AtomicBoolean()
    private var isEnded = false

    val isLost: Boolean
        get() = lost.get()

    fun send(channel: Int, isControl: Boolean, type: Int, body: ByteArray): Boolean =
        synchronized(lock) { !isEnded && write(channel, isControl, type, body) }

    fun sendLast(channel: Int, isControl: Boolean, type: Int, body: ByteArray): Boolean =
        synchronized(lock) {
            val isSent = !isEnded && write(channel, isControl, type, body)
            isEnded = true
            isSent
        }

    private fun write(channel: Int, isControl: Boolean, type: Int, body: ByteArray): Boolean {
        if (lost.get()) return false
        return try {
            writer.write(channel, isControl, type, body, encrypt)
            true
        } catch (e: IOException) {
            if (lost.compareAndSet(false, true)) {
                Log.w(
                    TAG,
                    "Sending 0x%04x on channel %d failed".format(type, channel),
                    e,
                )
            }
            false
        }
    }
}
