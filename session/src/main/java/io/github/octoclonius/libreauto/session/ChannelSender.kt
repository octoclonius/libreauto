package io.github.octoclonius.libreauto.session

import io.github.octoclonius.libreauto.protocol.FrameWriter
import io.github.octoclonius.libreauto.protocol.TlsServer
import io.github.octoclonius.libreauto.protocol.control.MessageType
import io.github.octoclonius.libreauto.protocol.control.channelOpenRequest

class ChannelSender(
    private val writer: FrameWriter,
    private val tls: TlsServer,
    val channel: Int,
) {
    fun send(type: Int, body: ByteArray) =
        writer.write(channel, isControl = false, type, body, tls::encrypt)

    fun sendOpenRequest() =
        writer.write(
            channel,
            isControl = true,
            MessageType.CHANNEL_OPEN_REQUEST,
            channelOpenRequest(channel),
            tls::encrypt,
        )
}
