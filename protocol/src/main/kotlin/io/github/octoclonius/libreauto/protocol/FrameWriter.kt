package io.github.octoclonius.libreauto.protocol

import java.io.IOException
import java.io.OutputStream
import java.nio.ByteBuffer

class FrameWriter(private val output: OutputStream) {
    private val lock = Any()

    @Throws(IOException::class)
    fun write(
        channel: Int,
        isControl: Boolean,
        type: Int,
        body: ByteArray,
        encrypt: ((ByteArray) -> ByteArray)? = null,
    ) {
        val payloadSize = MESSAGE_TYPE_SIZE + body.size
        val chunkSize = if (encrypt != null) MAX_RECORD_PLAINTEXT_SIZE else MAX_FRAME_DATA_SIZE
        val frameCount = (payloadSize + chunkSize - 1) / chunkSize
        var baseFlags = 0
        if (isControl) baseFlags = baseFlags or FrameFlags.CONTROL
        if (encrypt != null) baseFlags = baseFlags or FrameFlags.ENCRYPTED

        synchronized(lock) {
            for (index in 0 until frameCount) {
                val start = index * chunkSize
                val chunk = ByteArray(minOf(payloadSize, start + chunkSize) - start)
                val bodyOffset = if (index == 0) MESSAGE_TYPE_SIZE else 0
                if (index == 0) ByteBuffer.wrap(chunk).putShort(type.toShort())
                body.copyInto(
                    chunk,
                    destinationOffset = bodyOffset,
                    startIndex = start + bodyOffset - MESSAGE_TYPE_SIZE,
                    endIndex = start + chunk.size - MESSAGE_TYPE_SIZE,
                )
                val data = encrypt?.invoke(chunk) ?: chunk

                var flags = baseFlags
                if (index == 0) flags = flags or FrameFlags.FIRST
                if (index == frameCount - 1) flags = flags or FrameFlags.LAST
                val hasTotalSize = index == 0 && frameCount > 1

                val frame =
                    ByteBuffer.allocate(
                        FRAME_HEADER_SIZE +
                            (if (hasTotalSize) TOTAL_SIZE_FIELD_SIZE else 0) +
                            data.size
                    )
                frame.put(channel.toByte())
                frame.put(flags.toByte())
                frame.putShort(data.size.toShort())
                if (hasTotalSize) frame.putInt(payloadSize)
                frame.put(data)
                output.write(frame.array())
            }
            output.flush()
        }
    }
}
