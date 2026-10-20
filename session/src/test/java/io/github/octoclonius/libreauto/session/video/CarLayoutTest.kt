package io.github.octoclonius.libreauto.session.video

import io.github.octoclonius.libreauto.protocol.media.CarInsets
import io.github.octoclonius.libreauto.protocol.media.VideoFormat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CarLayoutTest {

    @Test
    fun aWideScreenHasTheRailOnTheLeftForALeftHandDriveCar() {
        val layout = carLayout(VideoFormat(800, 480, 30, 160), isDriverOnRight = false)
        assertEquals(RailSide.Left, layout.railSide)
        assertEquals(Area(0, 0, RAIL_THICKNESS_DP, 480), layout.rail)
        assertEquals(Area(RAIL_THICKNESS_DP, 0, 800, 480), layout.apps)
    }

    @Test
    fun aWideScreenHasTheRailOnTheRightForARightHandDriveCar() {
        val layout = carLayout(VideoFormat(800, 480, 30, 160), isDriverOnRight = true)
        assertEquals(RailSide.Right, layout.railSide)
        assertEquals(Area(800 - RAIL_THICKNESS_DP, 0, 800, 480), layout.rail)
        assertEquals(Area(0, 0, 800 - RAIL_THICKNESS_DP, 480), layout.apps)
    }

    @Test
    fun aPortraitScreenHasTheRailAtTheBottom() {
        val layout = carLayout(VideoFormat(720, 1280, 30, 160), isDriverOnRight = false)
        assertEquals(RailSide.Bottom, layout.railSide)
        assertEquals(Area(0, 1280 - RAIL_THICKNESS_DP, 720, 1280), layout.rail)
        assertEquals(Area(0, 0, 720, 1280 - RAIL_THICKNESS_DP), layout.apps)
    }

    @Test
    fun theRailGoesToTheBottomWhenTheRestWouldBeSquare() {
        val square = VideoFormat(480 + RAIL_THICKNESS_DP, 480, 30, 160)
        val wider = VideoFormat(480 + RAIL_THICKNESS_DP + 1, 480, 30, 160)
        assertEquals(RailSide.Bottom, carLayout(square, isDriverOnRight = false).railSide)
        assertEquals(RailSide.Left, carLayout(wider, isDriverOnRight = false).railSide)
    }

    @Test
    fun theRailIsThickerOnADenserScreen() {
        val layout = carLayout(VideoFormat(1920, 1080, 30, 320), isDriverOnRight = false)
        assertEquals(Area(0, 0, RAIL_THICKNESS_DP * 2, 1080), layout.rail)
    }

    @Test
    fun theRailReachesTheEdgeAndKeepsTheContentInsetsOnItsSide() {
        val insets = CarInsets(top = 10, bottom = 20, left = 30, right = 40)
        val layout =
            carLayout(
                VideoFormat(800, 480, 30, 160, contentInsets = insets),
                isDriverOnRight = false,
            )
        assertEquals(Area(0, 0, 30 + RAIL_THICKNESS_DP, 480), layout.rail)
        assertEquals(
            CarInsets(top = 10, bottom = 20, left = 30, right = 0),
            layout.railContentInsets,
        )
        assertEquals(Area(30 + RAIL_THICKNESS_DP, 0, 800, 480), layout.apps)
        assertEquals(
            CarInsets(top = 10, bottom = 20, left = 0, right = 40),
            layout.appsContentInsets,
        )
    }

    @Test
    fun theRailSideFollowsTheContentArea() {
        val insets = CarInsets(top = 0, bottom = 0, left = 130, right = 130)
        val layout =
            carLayout(
                VideoFormat(800, 480, 30, 160, contentInsets = insets),
                isDriverOnRight = false,
            )
        assertEquals(RailSide.Bottom, layout.railSide)
    }
}
