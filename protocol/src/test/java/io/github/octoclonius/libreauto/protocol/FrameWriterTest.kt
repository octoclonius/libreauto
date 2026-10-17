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
        val frames = write(3, isControl = true, payload = byteArrayOf(1, 2, 3))
        val flags = FrameFlags.FIRST or FrameFlags.LAST or FrameFlags.CONTROL
        assertArrayEquals(byteArrayOf(3, flags.toByte(), 0, 3, 1, 2, 3), frames.single())
    }

    @Test
    fun theFirstOfSeveralFramesCarriesTheTotalSize() {
        val payload = ByteArray(MAX_FRAME_DATA_SIZE + 10)
        val frames = write(1, isControl = false, payload = payload)
        assertEquals(2, frames.size)
        val first = ByteBuffer.wrap(frames[0])
        assertEquals(FrameFlags.FIRST, first.get(1).toInt())
        assertEquals(payload.size, first.getInt(FRAME_HEADER_SIZE))
        assertEquals(FrameFlags.LAST, frames[1][1].toInt())
    }

    @Test
    fun encryptedFramesHoldOneRecordEachAndCountPlaintextInTheTotal() {
        val payload = ByteArray(MAX_RECORD_PLAINTEXT_SIZE * 2 + 1)
        val frames = write(2, isControl = false, payload = payload, encrypt = TestCipher::encrypt)
        assertEquals(3, frames.size)
        assertEquals(payload.size, ByteBuffer.wrap(frames[0]).getInt(FRAME_HEADER_SIZE))
        assertTrue(frames.all { it[1].toInt() and FrameFlags.ENCRYPTED != 0 })
    }

    @Test
    fun concurrentWritesKeepMessagesWholeAndRecordsInOrder() {
        val frames = mutableListOf<ByteArray>()
        val writer = FrameWriter(recording(frames))
        var nextRecord = 0
        val encrypt = { plaintext: ByteArray -> byteArrayOf(nextRecord++.toByte()) + plaintext }
        val framesPerMessage = 3
        val payload = ByteArray(MAX_RECORD_PLAINTEXT_SIZE * (framesPerMessage - 1) + 1)
        val threads = 4
        val messagesPerThread = 20
        (0 until threads)
            .map { channel ->
                thread {
                    repeat(messagesPerThread) {
                        writer.write(channel, isControl = false, payload, encrypt)
                    }
                }
            }
            .forEach { it.join() }

        val messages = frames.chunked(framesPerMessage)
        assertTrue(messages.all { message -> message.map { it[0] }.distinct().size == 1 })
        val records =
            messages.flatMap { message ->
                message.mapIndexed { index, frame ->
                    val dataOffset =
                        if (index == 0) FRAME_HEADER_SIZE + TOTAL_SIZE_FIELD_SIZE
                        else FRAME_HEADER_SIZE
                    frame[dataOffset]
                }
            }
        assertEquals(List(frames.size) { it.toByte() }, records)
    }

    private fun write(
        channel: Int,
        isControl: Boolean,
        payload: ByteArray,
        encrypt: ((ByteArray) -> ByteArray)? = null,
    ): List<ByteArray> {
        val writes = mutableListOf<ByteArray>()
        FrameWriter(recording(writes)).write(channel, isControl, payload, encrypt)
        return writes
    }

    private fun recording(writes: MutableList<ByteArray>) =
        object : OutputStream() {
            override fun write(b: Int) = error("Frames are written whole")

            override fun write(b: ByteArray, off: Int, len: Int) {
                writes += b.copyOfRange(off, off + len)
            }
        }
}
