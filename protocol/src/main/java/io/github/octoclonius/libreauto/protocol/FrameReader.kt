package io.github.octoclonius.libreauto.protocol

import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.IOException
import java.io.InputStream
import java.net.ProtocolException

class FrameReader(
    input: InputStream,
    private val decrypt: (ByteArray) -> ByteArray?,
    private val clock: () -> Long,
) {
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
        val receivedNanos = clock()
        val flags = input.readUnsignedByte()
        val size = input.readUnsignedShort()
        val isFirst = flags and FrameFlags.FIRST != 0
        val isLast = flags and FrameFlags.LAST != 0
        val totalSize = if (isFirst && !isLast) input.readInt() else 0
        val data = ByteArray(size)
        input.readFully(data)
        return Frame(channel, flags, totalSize, data, receivedNanos)
    }

    private fun onFrame(frame: Frame): ChannelMessage? {
        val bytes = plaintext(frame)
        if (frame.isFirst && frame.isLast) {
            val type = typeOf(frame, bytes)
            return ChannelMessage(
                frame.channel,
                frame.isEncrypted,
                type,
                bytes.copyOfRange(MESSAGE_TYPE_SIZE, bytes.size),
                frame.receivedNanos,
            )
        }
        if (frame.isFirst) {
            startMessage(frame, bytes)
            return null
        }

        val message = continueMessage(frame)
        append(message, frame, bytes, from = 0)
        if (!frame.isLast) return null

        pendingMessages.remove(frame.channel)
        if (message.filled != message.body.size) {
            throw ProtocolException(
                "Message on channel ${frame.channel} is shorter than its total size"
            )
        }
        return ChannelMessage(
            frame.channel,
            message.isEncrypted,
            message.type,
            message.body,
            message.receivedNanos,
        )
    }

    private fun startMessage(frame: Frame, bytes: ByteArray) {
        if (frame.channel in pendingMessages) {
            throw ProtocolException("Second first frame on channel ${frame.channel}")
        }
        if (frame.totalSize !in MESSAGE_TYPE_SIZE..MAX_MESSAGE_SIZE) {
            throw ProtocolException(
                "Message of ${frame.totalSize} bytes on channel ${frame.channel} is outside " +
                    "$MESSAGE_TYPE_SIZE..$MAX_MESSAGE_SIZE"
            )
        }
        val message =
            PendingMessage(
                typeOf(frame, bytes),
                ByteArray(frame.totalSize - MESSAGE_TYPE_SIZE),
                frame.receivedNanos,
            )
        pendingMessages[frame.channel] = message
        append(message, frame, bytes, from = MESSAGE_TYPE_SIZE)
    }

    private fun continueMessage(frame: Frame): PendingMessage =
        pendingMessages[frame.channel]
            ?: throw ProtocolException(
                "Continuation frame on channel ${frame.channel} without a first frame"
            )

    private fun typeOf(frame: Frame, bytes: ByteArray): Int {
        if (bytes.size < MESSAGE_TYPE_SIZE) {
            throw ProtocolException("Message on channel ${frame.channel} starts without a type")
        }
        return (bytes[0].toInt() and 0xFF shl 8) or (bytes[1].toInt() and 0xFF)
    }

    private fun append(message: PendingMessage, frame: Frame, bytes: ByteArray, from: Int) {
        if (!frame.isEncrypted) message.isEncrypted = false
        if (bytes.size - from > message.body.size - message.filled) {
            throw ProtocolException(
                "Message on channel ${frame.channel} is longer than its total size"
            )
        }
        bytes.copyInto(message.body, destinationOffset = message.filled, startIndex = from)
        message.filled += bytes.size - from
    }

    private fun plaintext(frame: Frame): ByteArray {
        if (!frame.isEncrypted) return frame.data
        return decrypt(frame.data)
            ?: throw ProtocolException("Record on channel ${frame.channel} doesn't decrypt")
    }

    private class Frame(
        val channel: Int,
        val flags: Int,
        val totalSize: Int,
        val data: ByteArray,
        val receivedNanos: Long,
    ) {
        val isFirst
            get() = flags and FrameFlags.FIRST != 0

        val isLast
            get() = flags and FrameFlags.LAST != 0

        val isEncrypted
            get() = flags and FrameFlags.ENCRYPTED != 0
    }

    private class PendingMessage(val type: Int, val body: ByteArray, val receivedNanos: Long) {
        var filled = 0
        var isEncrypted = true
    }

    companion object {
        const val MAX_MESSAGE_SIZE = 256 * 1024
    }
}
