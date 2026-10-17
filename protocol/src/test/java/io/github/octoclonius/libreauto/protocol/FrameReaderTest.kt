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
        val payloads = listOf(byteArrayOf(7), ByteArray(MAX_FRAME_DATA_SIZE + 1) { (it % 251).toByte() })
        for (encrypt in listOf(null, TestCipher::encrypt)) {
            val output = ByteArrayOutputStream()
            val writer = FrameWriter(output)
            for (payload in payloads) writer.write(5, isControl = false, payload, encrypt)
            val messages = readAll(output.toByteArray())
            assertEquals(payloads.map { it.toList() }, messages.map { it.payload.toList() })
            assertTrue(messages.all { it.channel == 5 && it.isEncrypted == (encrypt != null) })
        }
    }

    @Test
    fun framesOfDifferentChannelsInterleave() {
        val bytes =
            frame(1, FrameFlags.FIRST, byteArrayOf(1), totalSize = 2) +
                frame(2, FrameFlags.FIRST, byteArrayOf(2), totalSize = 2) +
                frame(1, FrameFlags.LAST, byteArrayOf(1)) +
                frame(2, FrameFlags.LAST, byteArrayOf(2))
        val messages = readAll(bytes)
        assertEquals(listOf(1, 2), messages.map { it.channel })
        assertEquals(listOf<Byte>(2, 2), messages[1].payload.toList())
    }

    @Test
    fun aMessageWithAPlaintextFrameIsNotEncrypted() {
        val encrypted = TestCipher.encrypt(byteArrayOf(1))
        val bytes =
            frame(4, FrameFlags.FIRST or FrameFlags.ENCRYPTED, encrypted, totalSize = 2) +
                frame(4, FrameFlags.LAST, byteArrayOf(2))
        assertFalse(readAll(bytes).single().isEncrypted)
    }

    @Test
    fun aSecondFirstFrameOnAChannelBreaksTheFraming() {
        val first = frame(1, FrameFlags.FIRST, ByteArray(1), totalSize = 2)
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
            frame(1, FrameFlags.FIRST, ByteArray(1), totalSize = 3) +
                frame(1, FrameFlags.LAST, ByteArray(1))
        assertThrows<ProtocolException> { readAll(bytes) }
    }

    @Test
    fun totalsOutsideTheLimitBreakTheFraming() {
        for (totalSize in listOf(-1, FrameReader.MAX_MESSAGE_SIZE + 1)) {
            assertThrows<ProtocolException> {
                readAll(frame(1, FrameFlags.FIRST, ByteArray(1), totalSize = totalSize))
            }
        }
    }

    @Test
    fun aRecordThatDoesNotDecryptBreaksTheFraming() {
        val flags = FrameFlags.FIRST or FrameFlags.LAST or FrameFlags.ENCRYPTED
        assertThrows<ProtocolException> { readAll(frame(1, flags, byteArrayOf(1, 2))) }
    }

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
