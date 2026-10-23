package io.github.octoclonius.libreauto.protocol.media

import io.github.octoclonius.libreauto.protocol.control.parseServiceDiscoveryResponse
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class AudioSourceTest {

    private val pcm = 1

    @Test
    fun theAudioSourceIsTheMediaSourceServiceWithItsConfig() {
        val source = audioSource(discovery(source(7, varint(1, pcm), config(16000, 16, 1))))
        assertEquals(7, source?.channel)
        assertEquals(0, source?.config?.index)
        assertEquals(16000, source?.config?.sampleRate)
        assertEquals(1, source?.config?.channelCount)
    }

    @Test
    fun aCarWithoutAMediaSourceHasNoAudioSource() {
        assertNull(audioSource(discovery(varint(1, 5) + message(3, varint(2, 3)))))
    }

    @Test
    fun aMissingCodecIsPcm() {
        assertEquals(7, audioSource(discovery(source(7, config(16000, 16, 1))))?.channel)
    }

    @Test
    fun anotherCodecIsSkipped() {
        assertNull(audioSource(discovery(source(7, varint(1, 2), config(16000, 16, 1)))))
    }

    @Test
    fun aConfigThatIsntSixteenBitIsSkipped() {
        assertNull(audioSource(discovery(source(7, config(16000, 8, 1)))))
    }

    @Test
    fun aSourceWithoutAConfigIsSkipped() {
        assertNull(audioSource(discovery(source(7, varint(1, pcm)))))
    }

    @Test
    fun theOpenRequestHasOpenNoProcessingAndTheMaxUnacked() {
        assertArrayEquals(
            bytes(0x08, 0x01, 0x10, 0x00, 0x18, 0x00, 0x20, 0x02),
            microphoneRequest(isOpen = true, maxUnacked = 2),
        )
    }

    @Test
    fun theCloseRequestHasOnlyOpenFalse() {
        assertArrayEquals(bytes(0x08, 0x00), microphoneRequest(isOpen = false, maxUnacked = 2))
    }

    @Test
    fun theResponseHasTheStatusAsField1AndTheSessionAsField2() {
        val response = parseMicrophoneResponse(bytes(0x08, 0x00, 0x10, 0x03))
        assertEquals(0, response.status)
        assertEquals(3, response.session)
    }

    private fun discovery(vararg services: ByteArray) =
        parseServiceDiscoveryResponse(services.map { message(1, it) }.reduce(ByteArray::plus))

    private fun source(channel: Int, vararg fields: ByteArray) =
        varint(1, channel) + message(5, *fields)

    private fun config(sampleRate: Int, bitDepth: Int, channels: Int) =
        message(2, varint(1, sampleRate), varint(2, bitDepth), varint(3, channels))

    private fun varint(number: Int, value: Int): ByteArray {
        val out = mutableListOf((number shl 3).toByte())
        var rest = value
        while (rest >= 0x80) {
            out += ((rest and 0x7F) or 0x80).toByte()
            rest = rest ushr 7
        }
        out += rest.toByte()
        return out.toByteArray()
    }

    private fun message(number: Int, vararg fields: ByteArray): ByteArray {
        val body = fields.fold(ByteArray(0), ByteArray::plus)
        return byteArrayOf((number shl 3 or 2).toByte(), body.size.toByte()) + body
    }

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }
}
