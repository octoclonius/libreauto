package io.github.octoclonius.libreauto.session

import io.github.octoclonius.libreauto.protocol.ChannelMessage
import java.io.Closeable

internal interface CarChannel : Closeable {

    val channel: Int

    fun open()

    fun onMessage(message: ChannelMessage): Boolean

    override fun close() {}
}
