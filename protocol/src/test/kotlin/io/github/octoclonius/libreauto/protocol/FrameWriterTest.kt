package io.github.octoclonius.libreauto.protocol

import java.io.OutputStream
import java.nio.ByteBuffer
import kotlin.concurrent.thread
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
    fun theFirstOfSeveralFramesCarriesTheTotalSizeWithTheType() {
        val body = ByteArray(MAX_FRAME_DATA + 10)
        val frames = write(1, isControl = false, type = 1, body = body)
        assertEquals(2, frames.size)
        val first = ByteBuffer.wrap(frames[0])
        assertEquals(FIRST, first.get(1).toInt())
        assertEquals(TYPE_BYTES + body.size, first.getInt(HEADER_BYTES))
        assertEquals(1, first.getShort(HEADER_BYTES + TOTAL_SIZE_BYTES).toInt())
        assertEquals(LAST, frames[1][1].toInt())
    }

    @Test
    fun encryptedFramesHoldOneRecordEachAndCountPlaintextInTheTotal() {
        val body = ByteArray(MAX_RECORD_PLAINTEXT * 2 + 1)
        val frames =
            write(2, isControl = false, type = 1, body = body, encrypt = TestCipher::encrypt)
        assertEquals(3, frames.size)
        assertEquals(
            TYPE_BYTES + body.size,
            ByteBuffer.wrap(frames[0]).getInt(HEADER_BYTES),
        )
        assertTrue(frames.all { it[1].toInt() and ENCRYPTED != 0 })
    }

    @Test
    fun concurrentWritesKeepMessagesWholeAndRecordsInOrder() {
        val frames = mutableListOf<ByteArray>()
        val writer = FrameWriter(recording(frames))
        var nextRecord = 0
        val encrypt = { plaintext: ByteArray -> byteArrayOf(nextRecord++.toByte()) + plaintext }
        val framesPerMessage = 3
        val body = ByteArray(MAX_RECORD_PLAINTEXT * (framesPerMessage - 1) + 1)
        val threads = 4
        val messagesPerThread = 20
        (0 until threads)
            .map { channel ->
                thread {
                    repeat(messagesPerThread) {
                        writer.write(channel, isControl = false, 1, body, encrypt)
                    }
                }
            }
            .forEach { it.join() }

        val messages = frames.chunked(framesPerMessage)
        assertTrue(messages.all { message -> message.map { it[0] }.distinct().size == 1 })
        val records = messages.flatMap { message ->
            message.mapIndexed { index, frame ->
                val dataOffset = if (index == 0) HEADER_BYTES + TOTAL_SIZE_BYTES else HEADER_BYTES
                frame[dataOffset]
            }
        }
        assertEquals(List(frames.size) { it.toByte() }, records)
    }

    private fun write(
        channel: Int,
        isControl: Boolean,
        type: Int,
        body: ByteArray,
        encrypt: ((ByteArray) -> ByteArray)? = null,
    ): List<ByteArray> {
        val writes = mutableListOf<ByteArray>()
        FrameWriter(recording(writes)).write(channel, isControl, type, body, encrypt)
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
        const val MAX_FRAME_DATA = 0xFFFF
        const val MAX_RECORD_PLAINTEXT = 16384
    }
}
