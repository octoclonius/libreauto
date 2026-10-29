package io.github.octoclonius.libreauto.session.channel

import android.util.Log
import io.github.octoclonius.libreauto.protocol.ChannelMessage
import java.io.Closeable

internal interface CarChannel : Closeable {

    val channel: Int

    fun open()

    fun onMessage(message: ChannelMessage): Boolean

    override fun close() {}
}

internal fun logUnhandled(tag: String, message: ChannelMessage) {
    Log.i(tag, "Message 0x%04x on channel %d".format(message.type, message.channel))
}
