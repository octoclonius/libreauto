package io.github.octoclonius.libreauto.projection.input

import io.github.octoclonius.libreauto.protocol.input.parseInputReport
import io.github.octoclonius.libreauto.protocol.media.CarInsets
import io.github.octoclonius.libreauto.protocol.media.VideoFormat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class TouchTest {

    @Test
    fun aDownIsATouchWithItsPointer() {
        assertEquals(
            Touch(0, 0, listOf(TouchPointer(0, 400, 300))),
            touchOf(pointer(400, 300, 0), actionIndex(0), action(0)),
        )
    }

    @Test
    fun aPointerDownKeepsItsIndexAndItsPointersInOrder() {
        assertEquals(
            Touch(5, 1, listOf(TouchPointer(0, 100, 200), TouchPointer(1, 300, 400))),
            touchOf(pointer(100, 200, 0), pointer(300, 400, 1), actionIndex(1), action(5)),
        )
    }

    @Test
    fun theCarsActionsAreKept() {
        for (value in listOf(0, 1, 2, 5, 6)) {
            assertEquals(value, touchOf(pointer(1, 2, 0), action(value))?.action)
        }
    }

    @Test
    fun anUnknownActionIsADown() {
        for (value in listOf(3, 4, 7, 9, -1)) {
            assertEquals(0, touchOf(pointer(1, 2, 0), action(value))?.action)
        }
    }

    @Test
    fun aMissingActionIndexIs0() {
        assertEquals(
            Touch(2, 0, listOf(TouchPointer(0, 1, 2), TouchPointer(1, 3, 4))),
            touchOf(pointer(1, 2, 0), pointer(3, 4, 1), action(2)),
        )
    }

    @Test
    fun aPointerWithoutItsXYOrIdIsDropped() {
        assertNull(touchOf(message(1, varint(2, 2), varint(3, 0))))
        assertNull(touchOf(message(1, varint(1, 1), varint(3, 0))))
        assertNull(touchOf(message(1, varint(1, 1), varint(2, 2))))
        assertNull(touchOf(pointer(1, 2, 0), message(1, varint(1, 3), varint(2, 4)), action(2)))
    }

    @Test
    fun aReportWithoutATimestampIsDropped() {
        assertNull(touch(parseInputReport(message(3, pointer(1, 2, 0), action(0)))))
    }

    @Test
    fun aTouchWithoutPointersIsDropped() {
        assertNull(touchOf(actionIndex(0), action(0)))
        assertNull(touchOf())
    }

    @Test
    fun anActionIndexPastThePointersIsDropped() {
        assertNull(touchOf(pointer(1, 2, 0), pointer(3, 4, 1), actionIndex(2), action(6)))
    }

    @Test
    fun aNegativeActionIndexIsDropped() {
        assertNull(touchOf(pointer(1, 2, 0), actionIndex(-1), action(0)))
    }

    @Test
    fun aPointerIdOutsideTheTouchscreensSixteenIsDropped() {
        assertNull(touchOf(pointer(1, 2, 16)))
        assertNull(touchOf(pointer(1, 2, -1)))
        assertEquals(listOf(TouchPointer(15, 1, 2)), touchOf(pointer(1, 2, 15))?.pointers)
    }

    @Test
    fun aPointerIdUsedTwiceIsKept() {
        assertEquals(
            Touch(5, 1, listOf(TouchPointer(3, 1, 2), TouchPointer(3, 4, 5))),
            touchOf(pointer(1, 2, 3), pointer(4, 5, 3), actionIndex(1), action(5)),
        )
    }

    @Test
    fun squarePixelsKeepTheVisibleAreasPixels() {
        val wide =
            VideoFormat(1920, 1080, 60, 200, CarInsets(top = 0, bottom = 400, left = 0, right = 0))
        assertEquals(
            listOf(TouchPointer(0, 1918, 678)),
            touchOf(pointer(1918, 678, 0))?.onDisplay(wide)?.pointers,
        )
    }

    @Test
    fun widePixelsScaleX() {
        val format =
            VideoFormat(
                1280,
                720,
                30,
                160,
                CarInsets(top = 20, bottom = 20, left = 40, right = 40),
                pixelAspectRatioE4 = 15000,
            )
        assertEquals(
            listOf(TouchPointer(0, 150, 50), TouchPointer(1, 1799, 679)),
            touchOf(pointer(100, 50, 0), pointer(1199, 679, 1), actionIndex(1), action(5))
                ?.onDisplay(format)
                ?.pointers,
        )
    }

    @Test
    fun tallPixelsScaleY() {
        val format = VideoFormat(1280, 720, 30, 160, pixelAspectRatioE4 = 7500)
        assertEquals(
            listOf(TouchPointer(0, 100, 400)),
            touchOf(pointer(100, 300, 0))?.onDisplay(format)?.pointers,
        )
    }

    @Test
    fun touchesOutsideTheVisibleAreaAreScaledButNotClamped() {
        val format =
            VideoFormat(
                1280,
                720,
                30,
                160,
                CarInsets(top = 21, bottom = 20, left = 40, right = 41),
                pixelAspectRatioE4 = 15000,
            )
        assertEquals(
            listOf(TouchPointer(0, -60, -21)),
            touchOf(message(1, uint32(1, -40), uint32(2, -21), varint(3, 0)))
                ?.onDisplay(format)
                ?.pointers,
        )
        assertEquals(
            listOf(TouchPointer(0, 7502, 5000)),
            touchOf(pointer(5000, 5000, 0))?.onDisplay(format)?.pointers,
        )
    }

    private fun touchOf(vararg fields: ByteArray): Touch? =
        touch(parseInputReport(varint(1, 1) + message(3, *fields)))

    private fun pointer(x: Int, y: Int, id: Int) =
        message(1, varint(1, x), varint(2, y), varint(3, id))

    private fun actionIndex(value: Int) = varint(2, value)

    private fun action(value: Int) = varint(3, value)

    private fun varint(number: Int, value: Int): ByteArray = varint(number, value.toLong())

    private fun uint32(number: Int, value: Int): ByteArray =
        varint(number, value.toLong() and 0xFFFFFFFFL)

    private fun varint(number: Int, value: Long): ByteArray {
        val bytes = mutableListOf((number shl 3).toByte())
        var rest = value
        while (rest and 0x7FL.inv() != 0L) {
            bytes += (rest and 0x7F or 0x80).toByte()
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
