package io.github.octoclonius.libreauto.protocol

import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.net.ProtocolException
import java.nio.ByteBuffer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class FrameReaderTest {

    @Test
    fun writtenMessagesReadBack() {
        val bodies =
            listOf(
                byteArrayOf(),
                byteArrayOf(7),
                ByteArray(MAX_FRAME_DATA_SIZE + 1) { (it % 251).toByte() },
            )
        for (encrypt in listOf(null, TestCipher::encrypt)) {
            val output = ByteArrayOutputStream()
            val writer = FrameWriter(output)
            for (body in bodies) writer.write(5, isControl = false, 0x8001, body, encrypt)
            val messages = readAll(output.toByteArray())
            assertEquals(bodies.map { it.toList() }, messages.map { it.body.toList() })
            assertTrue(
                messages.all {
                    it.channel == 5 && it.type == 0x8001 && it.isEncrypted == (encrypt != null)
                }
            )
        }
    }

    @Test
    fun theFirstTwoBytesAreTheType() {
        val flags = FrameFlags.FIRST or FrameFlags.LAST
        val message = readAll(frame(1, flags, bytes(0x80, 0x01, 9))).single()
        assertEquals(0x8001, message.type)
        assertEquals(listOf<Byte>(9), message.body.toList())
    }

    @Test
    fun framesOfDifferentChannelsInterleave() {
        val bytes =
            frame(1, FrameFlags.FIRST, bytes(0, 1, 1), totalSize = 4) +
                frame(2, FrameFlags.FIRST, bytes(0, 2, 2), totalSize = 4) +
                frame(1, FrameFlags.LAST, bytes(1)) +
                frame(2, FrameFlags.LAST, bytes(2))
        val messages = readAll(bytes)
        assertEquals(listOf(1, 2), messages.map { it.channel })
        assertEquals(listOf(1, 2), messages.map { it.type })
        assertEquals(listOf<Byte>(2, 2), messages[1].body.toList())
    }

    @Test
    fun aMessageWithAPlaintextFrameIsNotEncrypted() {
        val encrypted = TestCipher.encrypt(bytes(0, 1, 1))
        val bytes =
            frame(4, FrameFlags.FIRST or FrameFlags.ENCRYPTED, encrypted, totalSize = 4) +
                frame(4, FrameFlags.LAST, bytes(2))
        assertFalse(readAll(bytes).single().isEncrypted)
    }

    @Test
    fun aMessageShorterThanItsTypeBreaksTheFraming() {
        assertThrows<ProtocolException> {
            readAll(frame(1, FrameFlags.FIRST or FrameFlags.LAST, bytes(1)))
        }
        assertThrows<ProtocolException> {
            readAll(
                frame(1, FrameFlags.FIRST, bytes(1), totalSize = 3) +
                    frame(1, FrameFlags.LAST, bytes(1, 2))
            )
        }
    }

    @Test
    fun aSecondFirstFrameOnAChannelBreaksTheFraming() {
        val first = frame(1, FrameFlags.FIRST, ByteArray(2), totalSize = 3)
        assertThrows<ProtocolException> { readAll(first + first) }
    }

    @Test
    fun aContinuationWithoutAFirstFrameBreaksTheFraming() {
        assertThrows<ProtocolException> { readAll(frame(1, FrameFlags.LAST, ByteArray(1))) }
    }

    @Test
    fun moreDataThanTheTotalBreaksTheFraming() {
        val bytes =
            frame(1, FrameFlags.FIRST, ByteArray(2), totalSize = 3) +
                frame(1, FrameFlags.LAST, ByteArray(2))
        assertThrows<ProtocolException> { readAll(bytes) }
    }

    @Test
    fun lessDataThanTheTotalBreaksTheFraming() {
        val bytes =
            frame(1, FrameFlags.FIRST, ByteArray(2), totalSize = 4) +
                frame(1, FrameFlags.LAST, ByteArray(1))
        assertThrows<ProtocolException> { readAll(bytes) }
    }

    @Test
    fun totalsOutsideTheLimitBreakTheFraming() {
        for (totalSize in listOf(-1, MESSAGE_TYPE_SIZE - 1, FrameReader.MAX_MESSAGE_SIZE + 1)) {
            assertThrows<ProtocolException> {
                readAll(frame(1, FrameFlags.FIRST, ByteArray(2), totalSize = totalSize))
            }
        }
    }

    @Test
    fun aRecordThatDoesNotDecryptBreaksTheFraming() {
        val flags = FrameFlags.FIRST or FrameFlags.LAST or FrameFlags.ENCRYPTED
        assertThrows<ProtocolException> { readAll(frame(1, flags, byteArrayOf(1, 2))) }
    }

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    private fun frame(
        channel: Int,
        flags: Int,
        data: ByteArray,
        totalSize: Int? = null,
    ): ByteArray {
        val buffer =
            ByteBuffer.allocate(
                FRAME_HEADER_SIZE + (if (totalSize != null) TOTAL_SIZE_FIELD_SIZE else 0) + data.size
            )
        buffer.put(channel.toByte()).put(flags.toByte()).putShort(data.size.toShort())
        if (totalSize != null) buffer.putInt(totalSize)
        return buffer.put(data).array()
    }

    private fun readAll(bytes: ByteArray): List<ChannelMessage> {
        val reader = FrameReader(bytes.inputStream(), TestCipher::decrypt)
        val channelMessages = mutableListOf<ChannelMessage>()
        try {
            while (true) channelMessages += reader.readMessage()
        } catch (_: EOFException) {}
        return channelMessages
    }
}
