package io.github.octoclonius.libreauto.protocol.control

import io.github.octoclonius.libreauto.protocol.CONTROL_CHANNEL
import io.github.octoclonius.libreauto.protocol.FrameReader
import io.github.octoclonius.libreauto.protocol.FrameWriter
import io.github.octoclonius.libreauto.protocol.MessageStatus
import io.github.octoclonius.libreauto.protocol.TlsServer
import java.io.IOException
import java.net.ProtocolException

class Handshake(
    private val reader: FrameReader,
    private val writer: FrameWriter,
    private val tls: TlsServer,
) {
    @Throws(IOException::class)
    fun run(): ProtocolVersion {
        val car = parseVersionRequest(receive(MessageType.VERSION_REQUEST))
        val response = VersionResponse(ProtocolVersion.answerTo(car), MessageStatus.SUCCESS)
        send(MessageType.VERSION_RESPONSE, response.toBody())
        while (!tls.isEstablished) {
            val toCar = tls.handshake(receive(MessageType.TLS_HANDSHAKE))
            if (toCar.isNotEmpty()) send(MessageType.TLS_HANDSHAKE, toCar)
        }
        val status = parseAuthComplete(receive(MessageType.AUTH_COMPLETE))
        if (status != MessageStatus.SUCCESS) {
            throw ProtocolException("Auth complete with status $status")
        }
        return car
    }

    private fun receive(type: Int): ByteArray {
        val message = reader.readMessage()
        if (message.channel != CONTROL_CHANNEL || message.type != type || message.isEncrypted) {
            val encryption = if (message.isEncrypted) "encrypted" else "plaintext"
            throw ProtocolException(
                "Expected plaintext message ${type.toHex()} on the control channel, got " +
                    "$encryption message ${message.type.toHex()} on channel ${message.channel}"
            )
        }
        return message.body
    }

    private fun send(type: Int, body: ByteArray) =
        writer.write(CONTROL_CHANNEL, isControl = false, type, body)
}

private fun Int.toHex() = "0x%04x".format(this)
