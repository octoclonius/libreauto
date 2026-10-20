package io.github.octoclonius.libreauto.privileged

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TouchscreenEventsTest {

    private val down = 0
    private val up = 1
    private val move = 2
    private val pointerDown = 5
    private val pointerUp = 6

    @Test
    fun aTapPressesAndReleasesItsPointer() {
        assertEquals(listOf(event(0, down, 10, 20)), events(down, 0, pointer(0, 10, 20)))
        assertEquals(listOf(event(0, up, 10, 20)), events(up, 0, pointer(0, 10, 20)))
    }

    @Test
    fun aMoveMovesEveryPointer() {
        assertEquals(
            listOf(event(0, move, 90, 90), event(1, move, 310, 310)),
            events(move, 0, pointer(0, 90, 90), pointer(1, 310, 310)),
        )
    }

    @Test
    fun aPointerDownPressesItsPointerAndMovesTheOthers() {
        assertEquals(
            listOf(event(0, move, 100, 100), event(1, down, 300, 300)),
            events(pointerDown, 1, pointer(0, 100, 100), pointer(1, 300, 300)),
        )
    }

    @Test
    fun aPointerUpReleasesItsPointerAndMovesTheOthers() {
        assertEquals(
            listOf(event(0, up, 100, 100), event(1, move, 300, 300)),
            events(pointerUp, 0, pointer(0, 100, 100), pointer(1, 300, 300)),
        )
    }

    @Test
    fun anUpReleasesItsPointerAndMovesTheOthers() {
        assertEquals(
            listOf(event(0, move, 100, 100), event(1, up, 300, 300)),
            events(up, 1, pointer(0, 100, 100), pointer(1, 300, 300)),
        )
    }

    @Test
    fun theCarsPointerIdsAndOrderAreKept() {
        assertEquals(
            listOf(event(9, move, 1, 2), event(3, down, 3, 4)),
            events(pointerDown, 1, pointer(9, 1, 2), pointer(3, 3, 4)),
        )
    }

    @Test
    fun positionsOutsideTheDisplayPassUnchanged() {
        assertEquals(listOf(event(0, down, -60, 7502)), events(down, 0, pointer(0, -60, 7502)))
    }

    private fun events(action: Int, actionIndex: Int, vararg pointers: TouchPointer) =
        touchscreenEvents(action, actionIndex, pointers.toList())

    private fun pointer(id: Int, x: Int, y: Int) = TouchPointer(id, x, y)

    private fun event(pointerId: Int, action: Int, x: Int, y: Int) =
        TouchscreenEvent(pointerId, action, x, y)
}
