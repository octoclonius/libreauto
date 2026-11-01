package io.github.octoclonius.libreauto.protocol

import java.io.IOException
import java.io.OutputStream
import java.nio.ByteBuffer

class FrameWriter(
    private val output: OutputStream,
    private val encrypt: ((ByteArray) -> ByteArray)? = null,
) {
    @Throws(IOException::class)
    fun write(message: OutgoingMessage) {
        for (frame in message.frames()) write(frame)
    }

    @Throws(IOException::class)
    fun write(frame: OutgoingFrame) {
        val data = encrypt?.invoke(frame.plaintext) ?: frame.plaintext
        val flags = if (encrypt == null) frame.flags else frame.flags or FrameFlags.ENCRYPTED
        val totalSize = frame.totalSize
        val headerSize = FRAME_HEADER_SIZE + if (totalSize == null) 0 else TOTAL_SIZE_FIELD_SIZE
        val bytes = ByteBuffer.allocate(headerSize + data.size)
        bytes.put(frame.channel.toByte())
        bytes.put(flags.toByte())
        bytes.putShort(data.size.toShort())
        if (totalSize != null) bytes.putInt(totalSize)
        bytes.put(data)
        output.write(bytes.array())
        output.flush()
    }
}
