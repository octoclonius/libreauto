package io.github.octoclonius.libreauto.protocol.media

import io.github.octoclonius.libreauto.protocol.control.parseServiceDiscoveryResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class AudioSinkTest {

    private val pcm = 1
    private val media = 3
    private val guidance = 1

    @Test
    fun theAudioSinkOfATypeIsTheMediaSinkWithThatAudioType() {
        val sinks =
            arrayOf(
                sink(4, varint(1, pcm), varint(2, guidance), config(16000, 16, 1)),
                sink(5, varint(1, pcm), varint(2, media), config(48000, 16, 2)),
            )
        assertEquals(5, pick(media, *sinks))
        assertEquals(4, pick(guidance, *sinks))
    }

    @Test
    fun aTypeTheCarHasNoSinkForIsNull() {
        assertNull(pick(2, sink(5, varint(1, pcm), varint(2, media), config(48000, 16, 2))))
    }

    @Test
    fun aMissingCodecIsPcm() {
        assertEquals(5, pick(media, sink(5, varint(2, media), config(48000, 16, 2))))
    }

    @Test
    fun anotherCodecIsSkipped() {
        assertNull(pick(media, sink(5, varint(1, 2), varint(2, media), config(48000, 16, 2))))
    }

    @Test
    fun theConfigsAreFieldThreeWithTheirPositionAsIndex() {
        val sink =
            audioSink(
                discovery(
                    sink(
                        5,
                        varint(2, media),
                        config(16000, 16, 1),
                        config(48000, 16, 2),
                    )
                ),
                media,
            )
        assertEquals(
            listOf(Triple(0, 16000, 1), Triple(1, 48000, 2)),
            sink?.configs?.map { Triple(it.index, it.sampleRate, it.channelCount) },
        )
    }

    @Test
    fun configsThatArentSixteenBitOrOneOrTwoChannelsAreSkippedButKeepTheirIndexes() {
        val sink =
            audioSink(
                discovery(
                    sink(
                        5,
                        varint(2, media),
                        config(48000, 8, 2),
                        config(48000, 16, 6),
                        config(44100, 16, 2),
                    )
                ),
                media,
            )
        assertEquals(listOf(2), sink?.configs?.map { it.index })
    }

    @Test
    fun aSinkWithoutUsableConfigsIsNull() {
        assertNull(pick(media, sink(5, varint(2, media), config(48000, 8, 2))))
    }

    @Test
    fun aFrameIsTheSampleRateOverTheFrameLengthTimesTheChannelsTimesTwoBytes() {
        assertEquals(7680, pcmFrameBytes(PcmConfig(0, 48000, 2), 40))
        assertEquals(1280, pcmFrameBytes(PcmConfig(0, 16000, 1), 40))
    }

    @Test
    fun theSetupRequestHasPcmAsCodecField1() {
        assertEquals(listOf<Byte>(0x08, 0x01), audioSetupRequest().toList())
    }

    private fun discovery(vararg services: ByteArray) =
        parseServiceDiscoveryResponse(services.map { message(1, it) }.reduce(ByteArray::plus))

    private fun pick(type: Int, vararg services: ByteArray) =
        audioSink(discovery(*services), type)?.channel

    private fun sink(channel: Int, vararg fields: ByteArray) =
        varint(1, channel) + message(3, *fields)

    private fun config(sampleRate: Int, bitDepth: Int, channels: Int) =
        message(3, varint(1, sampleRate), varint(2, bitDepth), varint(3, channels))

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
}
