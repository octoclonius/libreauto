package io.github.octoclonius.libreauto.privileged

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TouchscreenPointersTest {

    private val down = 0
    private val up = 1
    private val move = 2
    private val pointerDown = 5
    private val pointerUp = 6

    private val pointers = TouchscreenPointers()

    @Test
    fun aTapPressesAndReleasesOnePointer() {
        assertEquals(listOf(event(0, down, 10, 20)), touch(down, 0, pointer(0, 10, 20)))
        assertEquals(listOf(event(0, up, 10, 20)), touch(up, 0, pointer(0, 10, 20)))
    }

    @Test
    fun aDragMovesThePointer() {
        touch(down, 0, pointer(0, 10, 20))
        assertEquals(listOf(event(0, move, 15, 25)), touch(move, 0, pointer(0, 15, 25)))
        assertEquals(listOf(event(0, move, 30, 40)), touch(move, 0, pointer(0, 30, 40)))
    }

    @Test
    fun aMoveToTheSamePositionSendsNothing() {
        touch(down, 0, pointer(0, 10, 20))
        assertEquals(emptyList<TouchscreenEvent>(), touch(move, 0, pointer(0, 10, 20)))
    }

    @Test
    fun anUpAtANewPositionMovesBeforeReleasing() {
        touch(down, 0, pointer(0, 10, 20))
        assertEquals(
            listOf(event(0, move, 12, 22), event(0, up, 12, 22)),
            touch(up, 0, pointer(0, 12, 22)),
        )
    }

    @Test
    fun aPinchPressesMovesAndReleasesBothPointers() {
        assertEquals(listOf(event(0, down, 100, 100)), touch(down, 0, pointer(0, 100, 100)))
        assertEquals(
            listOf(event(1, down, 300, 300)),
            touch(pointerDown, 1, pointer(0, 100, 100), pointer(1, 300, 300)),
        )
        assertEquals(
            listOf(event(0, move, 90, 90), event(1, move, 310, 310)),
            touch(move, 0, pointer(0, 90, 90), pointer(1, 310, 310)),
        )
        assertEquals(
            listOf(event(1, up, 310, 310)),
            touch(pointerUp, 1, pointer(0, 90, 90), pointer(1, 310, 310)),
        )
        assertEquals(listOf(event(0, up, 90, 90)), touch(up, 0, pointer(0, 90, 90)))
    }

    @Test
    fun onlyThePointersThatMovedAreSent() {
        touch(down, 0, pointer(0, 100, 100))
        touch(pointerDown, 1, pointer(0, 100, 100), pointer(1, 300, 300))
        assertEquals(
            listOf(event(1, move, 320, 300)),
            touch(move, 0, pointer(0, 100, 100), pointer(1, 320, 300)),
        )
    }

    @Test
    fun otherPointersMoveBeforeAPointerGoesDownOrUp() {
        touch(down, 0, pointer(0, 100, 100))
        assertEquals(
            listOf(event(0, move, 105, 100), event(1, down, 300, 300)),
            touch(pointerDown, 1, pointer(0, 105, 100), pointer(1, 300, 300)),
        )
        assertEquals(
            listOf(event(1, move, 290, 300), event(0, move, 110, 100), event(0, up, 110, 100)),
            touch(pointerUp, 0, pointer(0, 110, 100), pointer(1, 290, 300)),
        )
    }

    @Test
    fun carPointerIdsTakeTheLowestFreeSlots() {
        assertEquals(listOf(event(0, down, 1, 1)), touch(down, 0, pointer(7, 1, 1)))
        assertEquals(
            listOf(event(1, down, 2, 2)),
            touch(pointerDown, 1, pointer(7, 1, 1), pointer(31, 2, 2)),
        )
        touch(pointerUp, 0, pointer(7, 1, 1), pointer(31, 2, 2))
        assertEquals(
            listOf(event(0, down, 3, 3)),
            touch(pointerDown, 0, pointer(4, 3, 3), pointer(31, 2, 2)),
        )
    }

    @Test
    fun pointersPastTheTouchscreensSixteenAreDropped() {
        val held = holdSixteen()
        val crowded = held + pointer(16, 99, 99)
        assertEquals(emptyList<TouchscreenEvent>(), touch(pointerDown, 16, *crowded.toTypedArray()))
        assertEquals(emptyList<TouchscreenEvent>(), touch(move, 0, *crowded.toTypedArray()))
        assertEquals(emptyList<TouchscreenEvent>(), touch(pointerUp, 16, *crowded.toTypedArray()))
    }

    @Test
    fun aDroppedPointerIsPressedOnceASlotIsFree() {
        val held = holdSixteen()
        val crowded = held + pointer(16, 99, 99)
        touch(pointerDown, 16, *crowded.toTypedArray())
        assertEquals(listOf(event(0, up, 0, 0)), touch(pointerUp, 0, *crowded.toTypedArray()))
        assertEquals(
            listOf(event(0, down, 98, 99)),
            touch(move, 0, *(held.drop(1) + pointer(16, 98, 99)).toTypedArray()),
        )
    }

    @Test
    fun aDownReleasesPointersLeftFromALostUp() {
        touch(down, 0, pointer(0, 10, 20))
        touch(pointerDown, 1, pointer(0, 10, 20), pointer(1, 30, 40))
        assertEquals(
            listOf(event(0, up, 10, 20), event(1, up, 30, 40), event(0, down, 50, 60)),
            touch(down, 0, pointer(0, 50, 60)),
        )
    }

    @Test
    fun aPointerDownForAHeldPointerReleasesItFirst() {
        touch(down, 0, pointer(0, 10, 20))
        touch(pointerDown, 1, pointer(0, 10, 20), pointer(1, 30, 40))
        assertEquals(
            listOf(event(1, up, 30, 40), event(1, down, 35, 45)),
            touch(pointerDown, 1, pointer(0, 10, 20), pointer(1, 35, 45)),
        )
    }

    @Test
    fun aPointerMissingFromATouchIsReleased() {
        touch(down, 0, pointer(0, 10, 20))
        touch(pointerDown, 1, pointer(0, 10, 20), pointer(1, 30, 40))
        assertEquals(
            listOf(event(0, up, 10, 20), event(1, move, 31, 40)),
            touch(move, 0, pointer(1, 31, 40)),
        )
    }

    @Test
    fun anUpReleasesEveryPointer() {
        touch(down, 0, pointer(0, 10, 20))
        touch(pointerDown, 1, pointer(0, 10, 20), pointer(1, 30, 40))
        assertEquals(
            listOf(event(1, up, 30, 40), event(0, up, 10, 20)),
            touch(up, 1, pointer(0, 10, 20), pointer(1, 30, 40)),
        )
        assertEquals(listOf(event(0, down, 1, 2)), touch(down, 0, pointer(0, 1, 2)))
    }

    @Test
    fun aMoveForAPointerThatIsntDownPressesIt() {
        assertEquals(listOf(event(0, down, 10, 20)), touch(move, 0, pointer(3, 10, 20)))
    }

    @Test
    fun anUpOrPointerUpForAPointerThatIsntDownSendsNothing() {
        assertEquals(emptyList<TouchscreenEvent>(), touch(up, 0, pointer(0, 10, 20)))
        touch(down, 0, pointer(0, 10, 20))
        assertEquals(
            emptyList<TouchscreenEvent>(),
            touch(pointerUp, 1, pointer(0, 10, 20), pointer(1, 30, 40)),
        )
    }

    @Test
    fun positionsOutsideTheDisplayPassUnchanged() {
        assertEquals(listOf(event(0, down, -60, -21)), touch(down, 0, pointer(0, -60, -21)))
        assertEquals(
            listOf(event(0, move, 7502, 5000)),
            touch(move, 0, pointer(0, 7502, 5000)),
        )
    }

    private fun holdSixteen(): List<TouchPointer> {
        val held = (0 until 16).map { pointer(it, it, it) }
        touch(down, 0, held[0])
        for (index in 1 until 16) touch(pointerDown, index, *held.take(index + 1).toTypedArray())
        return held
    }

    private fun touch(action: Int, actionIndex: Int, vararg touchPointers: TouchPointer) =
        pointers.onTouch(action, actionIndex, touchPointers.toList())

    private fun pointer(id: Int, x: Int, y: Int) = TouchPointer(id, x, y)

    private fun event(pointerId: Int, action: Int, x: Int, y: Int) =
        TouchscreenEvent(pointerId, action, x, y)
}
