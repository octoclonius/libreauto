package io.github.octoclonius.libreauto.session.channel

import io.github.octoclonius.libreauto.protocol.control.MessageType
import io.github.octoclonius.libreauto.protocol.control.channelOpenRequest

internal class ChannelSender(private val link: CarLink, val channel: Int) {

    fun send(type: Int, body: ByteArray): Boolean =
        link.send(channel, isControl = false, type, body)

    fun sendOpenRequest(): Boolean =
        link.send(
            channel,
            isControl = true,
            MessageType.CHANNEL_OPEN_REQUEST,
            channelOpenRequest(channel),
        )
}
