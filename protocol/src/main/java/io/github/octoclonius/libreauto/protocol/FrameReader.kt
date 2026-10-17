package io.github.octoclonius.libreauto.protocol

import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.IOException
import java.io.InputStream
import java.net.ProtocolException

class FrameReader(input: InputStream, private val decrypt: (ByteArray) -> ByteArray?) {
    private val input = DataInputStream(BufferedInputStream(input, MAX_RECORD_PLAINTEXT_SIZE))
    private val pendingMessages = HashMap<Int, PendingMessage>()

    @Throws(IOException::class)
    fun readMessage(): ChannelMessage {
        while (true) {
            val message = onFrame(readFrame())
            if (message != null) return message
        }
    }

    private fun readFrame(): Frame {
        val channel = input.readUnsignedByte()
        val flags = input.readUnsignedByte()
        val size = input.readUnsignedShort()
        val isFirst = flags and FrameFlags.FIRST != 0
        val isLast = flags and FrameFlags.LAST != 0
        val totalSize = if (isFirst && !isLast) input.readInt() else 0
        val data = ByteArray(size)
        input.readFully(data)
        return Frame(channel, flags, totalSize, data)
    }

    private fun onFrame(frame: Frame): ChannelMessage? {
        if (frame.isFirst && frame.isLast) {
            return ChannelMessage(frame.channel, frame.isEncrypted, plaintext(frame))
        }

        val message = if (frame.isFirst) startMessage(frame) else continueMessage(frame)
        append(message, frame)
        if (!frame.isLast) return null

        pendingMessages.remove(frame.channel)
        if (message.filled != message.data.size) {
            throw ProtocolException(
                "Message on channel ${frame.channel} is shorter than its total size"
            )
        }
        return ChannelMessage(frame.channel, message.isEncrypted, message.data)
    }

    private fun startMessage(frame: Frame): PendingMessage {
        if (frame.channel in pendingMessages) {
            throw ProtocolException("Second first frame on channel ${frame.channel}")
        }
        if (frame.totalSize !in 0..MAX_MESSAGE_SIZE) {
            throw ProtocolException(
                "Message of ${frame.totalSize} bytes on channel ${frame.channel} is too large"
            )
        }
        val message = PendingMessage(ByteArray(frame.totalSize))
        pendingMessages[frame.channel] = message
        return message
    }

    private fun continueMessage(frame: Frame): PendingMessage =
        pendingMessages[frame.channel]
            ?: throw ProtocolException(
                "Continuation frame on channel ${frame.channel} without a first frame"
            )

    private fun append(message: PendingMessage, frame: Frame) {
        if (!frame.isEncrypted) message.isEncrypted = false
        val bytes = plaintext(frame)
        if (bytes.size > message.data.size - message.filled) {
            throw ProtocolException(
                "Message on channel ${frame.channel} is longer than its total size"
            )
        }
        bytes.copyInto(message.data, destinationOffset = message.filled)
        message.filled += bytes.size
    }

    private fun plaintext(frame: Frame): ByteArray {
        if (!frame.isEncrypted) return frame.data
        return decrypt(frame.data)
            ?: throw ProtocolException("Record on channel ${frame.channel} doesn't decrypt")
    }

    private class Frame(val channel: Int, val flags: Int, val totalSize: Int, val data: ByteArray) {
        val isFirst get() = flags and FrameFlags.FIRST != 0
        val isLast get() = flags and FrameFlags.LAST != 0
        val isEncrypted get() = flags and FrameFlags.ENCRYPTED != 0
    }

    private class PendingMessage(val data: ByteArray) {
        var filled = 0
        var isEncrypted = true
    }

    companion object {
        const val MAX_MESSAGE_SIZE = 256 * 1024
    }
}
