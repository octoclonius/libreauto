package io.github.octoclonius.libreauto.car.rail

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RailSideTest {

    @Test
    fun aWideScreenHasTheRailOnTheDriversSide() {
        assertEquals(RailSide.Left, railSide(800, 480, 80, isDriverOnRight = false))
        assertEquals(RailSide.Right, railSide(800, 480, 80, isDriverOnRight = true))
    }

    @Test
    fun theRailGoesToTheBottomWhenTheRestWouldBeSquare() {
        assertEquals(RailSide.Bottom, railSide(560, 480, 80, isDriverOnRight = false))
        assertEquals(RailSide.Left, railSide(561, 480, 80, isDriverOnRight = false))
    }

    @Test
    fun aPortraitScreenHasTheRailAtTheBottom() {
        assertEquals(RailSide.Bottom, railSide(480, 800, 80, isDriverOnRight = false))
        assertEquals(RailSide.Bottom, railSide(480, 800, 80, isDriverOnRight = true))
    }
}
