package io.github.octoclonius.libreauto.protocol

import java.nio.ByteBuffer

class OutgoingMessage(
    val channel: Int,
    val isControl: Boolean,
    val type: Int,
    val body: ByteArray,
) {
    private val size = MESSAGE_TYPE_SIZE + body.size

    internal fun frames(): Sequence<OutgoingFrame> = sequence {
        val isFragmented = size > MAX_FRAME_SIZE - FRAME_HEADER_SIZE
        var start = 0
        while (start < size) {
            val isFirst = start == 0
            val hasTotalSize = isFirst && isFragmented
            val headerSize = FRAME_HEADER_SIZE + if (hasTotalSize) TOTAL_SIZE_FIELD_SIZE else 0
            val end = minOf(size, start + MAX_FRAME_SIZE - headerSize)
            var flags = if (isControl) FrameFlags.CONTROL else 0
            if (isFirst) flags = flags or FrameFlags.FIRST
            if (end == size) flags = flags or FrameFlags.LAST
            yield(
                OutgoingFrame(
                    channel,
                    flags,
                    totalSize = if (hasTotalSize) size else null,
                    plaintext(start, end),
                )
            )
            start = end
        }
    }

    private fun plaintext(start: Int, end: Int): ByteArray {
        val bytes = ByteArray(end - start)
        if (start == 0) ByteBuffer.wrap(bytes).putShort(type.toShort())
        body.copyInto(
            bytes,
            destinationOffset = if (start == 0) MESSAGE_TYPE_SIZE else 0,
            startIndex = maxOf(start - MESSAGE_TYPE_SIZE, 0),
            endIndex = end - MESSAGE_TYPE_SIZE,
        )
        return bytes
    }
}

class OutgoingFrame
internal constructor(
    val channel: Int,
    internal val flags: Int,
    internal val totalSize: Int?,
    internal val plaintext: ByteArray,
)
