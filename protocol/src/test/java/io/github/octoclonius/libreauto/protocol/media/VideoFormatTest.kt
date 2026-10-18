package io.github.octoclonius.libreauto.protocol.media

import io.github.octoclonius.libreauto.protocol.control.parseServiceDiscoveryResponse
import java.net.ProtocolException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class VideoFormatTest {

    @Test
    fun theDhusConfigIs800x480At30FpsAnd160Dpi() {
        assertEquals(
            VideoFormat(800, 480, 30, 160),
            format(varint(1, 1), varint(2, 2), varint(5, 160)),
        )
    }

    @Test
    fun theResolutionIsField1() {
        val sizes =
            listOf(
                800 to 480,
                1280 to 720,
                1920 to 1080,
                2560 to 1440,
                3840 to 2160,
                720 to 1280,
                1080 to 1920,
                1440 to 2560,
                2160 to 3840,
            )
        sizes.forEachIndexed { index, (width, height) ->
            assertEquals(
                VideoFormat(width, height, 30, 160),
                format(varint(1, index + 1), varint(2, 2), varint(5, 160)),
            )
        }
    }

    @Test
    fun frameRate1Is60Fps() {
        assertEquals(
            VideoFormat(1280, 720, 60, 160),
            format(varint(1, 2), varint(2, 1), varint(5, 160)),
        )
    }

    @Test
    fun aMissingResolutionIs800x480() {
        assertEquals(VideoFormat(800, 480, 30, 160), format(varint(2, 2), varint(5, 160)))
    }

    @Test
    fun anUnknownResolutionIs800x480() {
        assertEquals(
            VideoFormat(800, 480, 30, 160),
            format(varint(1, 0), varint(2, 2), varint(5, 160)),
        )
        assertEquals(
            VideoFormat(800, 480, 30, 160),
            format(varint(1, 10), varint(2, 2), varint(5, 160)),
        )
    }

    @Test
    fun aMissingFrameRateIs60Fps() {
        assertEquals(VideoFormat(1920, 1080, 60, 160), format(varint(1, 3), varint(5, 160)))
    }

    @Test
    fun anUnknownFrameRateIs60Fps() {
        assertEquals(
            VideoFormat(1920, 1080, 60, 160),
            format(varint(1, 3), varint(2, 0), varint(5, 160)),
        )
        assertEquals(
            VideoFormat(1920, 1080, 60, 160),
            format(varint(1, 3), varint(2, 3), varint(5, 160)),
        )
    }

    @Test
    fun theDensityIsField5() {
        assertEquals(
            VideoFormat(1920, 1080, 60, 320),
            format(varint(1, 3), varint(2, 1), varint(5, 320)),
        )
    }

    @Test
    fun aMissingDensityIsAProtocolError() {
        assertThrows(ProtocolException::class.java) { format(varint(1, 1), varint(2, 2)) }
    }

    @Test
    fun aZeroDensityIsAProtocolError() {
        assertThrows(ProtocolException::class.java) {
            format(varint(1, 1), varint(2, 2), varint(5, 0))
        }
    }

    @Test
    fun aNegativeDensityIsAProtocolError() {
        assertThrows(ProtocolException::class.java) {
            format(varint(1, 1), varint(2, 2), varint(5, -160))
        }
    }

    private fun format(vararg fields: ByteArray): VideoFormat? =
        videoSink(
                parseServiceDiscoveryResponse(
                    message(1, varint(1, 3), message(3, message(4, *fields)))
                )
            )
            ?.configs
            ?.single()
            ?.let(::videoFormat)

    private fun varint(number: Int, value: Int): ByteArray {
        val bytes = mutableListOf((number shl 3).toByte())
        var rest = value.toLong()
        while (rest and 0x7fL.inv() != 0L) {
            bytes += (rest and 0x7f or 0x80).toByte()
            rest = rest ushr 7
        }
        bytes += rest.toByte()
        return bytes.toByteArray()
    }

    private fun message(number: Int, vararg fields: ByteArray): ByteArray {
        val body = fields.fold(ByteArray(0), ByteArray::plus)
        return byteArrayOf((number shl 3 or 2).toByte(), body.size.toByte()) + body
    }
}
