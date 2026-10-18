package io.github.octoclonius.libreauto.privileged

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class TouchRequestTest {

    private val down = 0
    private val pointerDown = 5

    @Test
    fun aTouchWithOnePointerIsAccepted() {
        assertNull(touchRequestError(down, 0, intArrayOf(0), intArrayOf(400), intArrayOf(300)))
    }

    @Test
    fun theCarsActionsAreAccepted() {
        for (action in listOf(0, 1, 2, 5, 6)) {
            assertNull(touchRequestError(action, 0, intArrayOf(0), intArrayOf(1), intArrayOf(2)))
        }
    }

    @Test
    fun otherActionsAreRefused() {
        for (action in listOf(-1, 3, 4, 7, 9, 0x105)) {
            assertNotNull(
                touchRequestError(action, 0, intArrayOf(0), intArrayOf(1), intArrayOf(2))
            )
        }
    }

    @Test
    fun aTouchWithoutPointersIsRefused() {
        assertNotNull(touchRequestError(down, 0, intArrayOf(), intArrayOf(), intArrayOf()))
    }

    @Test
    fun positionsThatDontMatchThePointersAreRefused() {
        assertNotNull(touchRequestError(down, 0, intArrayOf(0), intArrayOf(), intArrayOf(2)))
        assertNotNull(touchRequestError(down, 0, intArrayOf(0), intArrayOf(1), intArrayOf(2, 3)))
    }

    @Test
    fun anActionIndexOutsideThePointersIsRefused() {
        val ids = intArrayOf(0, 1)
        val positions = intArrayOf(1, 2)
        assertNull(touchRequestError(pointerDown, 1, ids, positions, positions))
        assertNotNull(touchRequestError(pointerDown, 2, ids, positions, positions))
        assertNotNull(touchRequestError(pointerDown, -1, ids, positions, positions))
    }

    @Test
    fun pointerIdsOutsideMotionEventsRangeAreRefused() {
        val position = intArrayOf(1)
        assertNull(touchRequestError(down, 0, intArrayOf(31), position, position))
        assertNotNull(touchRequestError(down, 0, intArrayOf(32), position, position))
        assertNotNull(touchRequestError(down, 0, intArrayOf(-1), position, position))
    }

    @Test
    fun aPointerIdUsedTwiceIsRefused() {
        val positions = intArrayOf(1, 2)
        assertNotNull(touchRequestError(pointerDown, 1, intArrayOf(3, 3), positions, positions))
    }

    @Test
    fun positionsOutsideTheDisplayAreAccepted() {
        assertNull(
            touchRequestError(down, 0, intArrayOf(0), intArrayOf(-60), intArrayOf(100_000))
        )
    }
}
