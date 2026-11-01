package io.github.octoclonius.libreauto.projection.channel

import io.github.octoclonius.libreauto.protocol.OutgoingMessage
import io.github.octoclonius.libreauto.protocol.control.MessageType
import io.github.octoclonius.libreauto.protocol.control.channelOpenRequest

internal class ChannelSender(private val link: CarLink, val channel: Int) {

    fun send(type: Int, body: ByteArray): Boolean =
        link.send(OutgoingMessage(channel, isControl = false, type, body))

    fun sendLast(type: Int, body: ByteArray): Boolean =
        link.sendLast(OutgoingMessage(channel, isControl = false, type, body))

    fun sendOpenRequest(): Boolean =
        link.send(
            OutgoingMessage(
                channel,
                isControl = true,
                MessageType.CHANNEL_OPEN_REQUEST,
                channelOpenRequest(channel),
            )
        )
}
