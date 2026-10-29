package io.github.octoclonius.libreauto.session.channel

import android.util.Log
import io.github.octoclonius.libreauto.protocol.FrameWriter
import io.github.octoclonius.libreauto.protocol.TlsServer
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

internal class CarLink(private val writer: FrameWriter, private val tls: TlsServer) {

    private companion object {
        const val TAG = "CarLink"
    }

    private val lost = AtomicBoolean()

    val isLost: Boolean
        get() = lost.get()

    fun send(channel: Int, isControl: Boolean, type: Int, body: ByteArray): Boolean {
        if (lost.get()) return false
        return try {
            writer.write(channel, isControl, type, body, tls::encrypt)
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
