package io.github.octoclonius.libreauto.protocol

import java.io.OutputStream
import java.nio.ByteBuffer
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FrameWriterTest {

    @Test
    fun aShortMessageIsOneFrame() {
        val frames = write(3, isControl = true, type = 0x8001, body = byteArrayOf(1, 2, 3))
        val flags = FIRST or LAST or CONTROL
        assertArrayEquals(
            byteArrayOf(3, flags.toByte(), 0, 5, 0x80.toByte(), 0x01, 1, 2, 3),
            frames.single(),
        )
    }

    @Test
    fun aMessageThatFillsAFrameIsOneFrame() {
        val body = ByteArray(MAX_FRAME_SIZE - HEADER_BYTES - TYPE_BYTES)
        val frame = write(1, isControl = false, type = 1, body = body).single()
        assertEquals(MAX_FRAME_SIZE, frame.size)
        assertEquals(FIRST or LAST, frame[1].toInt())
    }

    @Test
    fun aLongerMessageSplitsIntoFramesOfTheFrameSizeAndTheFirstCarriesTheTotalSize() {
        val body = ByteArray(MAX_FRAME_SIZE - HEADER_BYTES - TYPE_BYTES + 1)
        val frames = write(1, isControl = false, type = 0x8001, body = body)
        assertEquals(
            listOf(MAX_FRAME_SIZE, HEADER_BYTES + TOTAL_SIZE_BYTES + 1),
            frames.map { it.size },
        )
        val first = ByteBuffer.wrap(frames[0])
        assertEquals(FIRST, first.get(1).toInt())
        assertEquals(TYPE_BYTES + body.size, first.getInt(HEADER_BYTES))
        assertEquals(0x8001, first.getShort(HEADER_BYTES + TOTAL_SIZE_BYTES).toInt() and 0xFFFF)
        assertEquals(LAST, frames[1][1].toInt())
    }

    @Test
    fun encryptedFramesAreSplitBeforeEncryptionAndCountPlaintextInTheTotal() {
        val body = ByteArray(MAX_FRAME_SIZE * 2)
        val frames =
            write(2, isControl = false, type = 1, body = body, encrypt = TestCipher::encrypt)
        assertEquals(3, frames.size)
        assertEquals(TYPE_BYTES + body.size, ByteBuffer.wrap(frames[0]).getInt(HEADER_BYTES))
        assertTrue(frames.all { it.size <= MAX_FRAME_SIZE + CIPHER_OVERHEAD_BYTES })
        assertTrue(frames.all { it[1].toInt() and ENCRYPTED != 0 })
    }

    private fun write(
        channel: Int,
        isControl: Boolean,
        type: Int,
        body: ByteArray,
        encrypt: ((ByteArray) -> ByteArray)? = null,
    ): List<ByteArray> {
        val writes = mutableListOf<ByteArray>()
        FrameWriter(recording(writes), encrypt)
            .write(OutgoingMessage(channel, isControl, type, body))
        return writes
    }

    private fun recording(writes: MutableList<ByteArray>) =
        object : OutputStream() {
            override fun write(b: Int) = error("Frames are written whole")

            override fun write(b: ByteArray, off: Int, len: Int) {
                writes += b.copyOfRange(off, off + len)
            }
        }

    private companion object {
        const val FIRST = 0x01
        const val LAST = 0x02
        const val CONTROL = 0x04
        const val ENCRYPTED = 0x08
        const val HEADER_BYTES = 4
        const val TOTAL_SIZE_BYTES = 4
        const val TYPE_BYTES = 2
        const val CIPHER_OVERHEAD_BYTES = 1
    }
}
