package io.github.octoclonius.libreauto.session.input

import io.github.octoclonius.libreauto.protocol.media.CarInsets
import io.github.octoclonius.libreauto.protocol.media.VideoFormat
import io.github.octoclonius.libreauto.session.display.RAIL_THICKNESS_DP
import io.github.octoclonius.libreauto.session.display.carLayout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class TouchRouterTest {

    private val layout = carLayout(VideoFormat(800, 480, 30, 160), isDriverOnRight = false)
    private val router = TouchRouter()

    @Test
    fun aDownOnTheRailGoesToTheRail() {
        assertEquals(
            RoutedTouch(TouchTarget.Rail, Touch(0, 0, listOf(TouchPointer(0, 40, 100)))),
            router.route(Touch(0, 0, listOf(TouchPointer(0, 40, 100))), layout),
        )
    }

    @Test
    fun aDownBesideTheRailGoesToTheAppsInTheirCoordinates() {
        assertEquals(
            RoutedTouch(
                TouchTarget.Apps,
                Touch(0, 0, listOf(TouchPointer(0, 400 - RAIL_THICKNESS_DP, 100))),
            ),
            router.route(Touch(0, 0, listOf(TouchPointer(0, 400, 100))), layout),
        )
    }

    @Test
    fun aGestureStaysWithWhereItStarted() {
        router.route(Touch(0, 0, listOf(TouchPointer(0, 40, 100))), layout)
        assertEquals(
            RoutedTouch(TouchTarget.Rail, Touch(2, 0, listOf(TouchPointer(0, 400, 100)))),
            router.route(Touch(2, 0, listOf(TouchPointer(0, 400, 100))), layout),
        )
        assertEquals(
            TouchTarget.Rail,
            router.route(Touch(1, 0, listOf(TouchPointer(0, 400, 100))), layout)?.target,
        )
    }

    @Test
    fun aSecondFingerGoesWhereTheFirstWent() {
        router.route(Touch(0, 0, listOf(TouchPointer(0, 400, 100))), layout)
        assertEquals(
            RoutedTouch(
                TouchTarget.Apps,
                Touch(
                    5,
                    1,
                    listOf(
                        TouchPointer(0, 400 - RAIL_THICKNESS_DP, 100),
                        TouchPointer(1, 40 - RAIL_THICKNESS_DP, 100),
                    ),
                ),
            ),
            router.route(
                Touch(5, 1, listOf(TouchPointer(0, 400, 100), TouchPointer(1, 40, 100))),
                layout,
            ),
        )
    }

    @Test
    fun theDownAfterAnUpChoosesAgain() {
        router.route(Touch(0, 0, listOf(TouchPointer(0, 40, 100))), layout)
        router.route(Touch(1, 0, listOf(TouchPointer(0, 40, 100))), layout)
        assertEquals(
            TouchTarget.Apps,
            router.route(Touch(0, 0, listOf(TouchPointer(0, 400, 100))), layout)?.target,
        )
    }

    @Test
    fun aDownInTheMiddleOfAGestureChoosesAgain() {
        router.route(Touch(0, 0, listOf(TouchPointer(0, 40, 100))), layout)
        assertEquals(
            TouchTarget.Apps,
            router.route(Touch(0, 0, listOf(TouchPointer(0, 400, 100))), layout)?.target,
        )
    }

    @Test
    fun aMoveWithoutADownGoesWhereItIs() {
        assertEquals(
            TouchTarget.Rail,
            router.route(Touch(2, 0, listOf(TouchPointer(0, 40, 100))), layout)?.target,
        )
    }

    @Test
    fun touchesAreScaledToTheDisplayBeforeTheyAreRouted() {
        val wide = carLayout(VideoFormat(800, 480, 30, 160, pixelAspectRatioE4 = 20000), false)
        assertEquals(
            RoutedTouch(TouchTarget.Rail, Touch(0, 0, listOf(TouchPointer(0, 60, 100)))),
            router.route(Touch(0, 0, listOf(TouchPointer(0, 30, 100))), wide),
        )
    }

    @Test
    fun aGestureStartingOutsideTheRailAndTheAppsIsDropped() {
        val inset =
            carLayout(
                VideoFormat(
                    800,
                    480,
                    30,
                    160,
                    stableInsets = CarInsets(top = 40, bottom = 0, left = 0, right = 0),
                ),
                isDriverOnRight = false,
            )
        assertNull(router.route(Touch(0, 0, listOf(TouchPointer(0, 400, 10))), inset))
        assertNull(router.route(Touch(2, 0, listOf(TouchPointer(0, 400, 100))), inset))
        assertNull(router.route(Touch(1, 0, listOf(TouchPointer(0, 400, 100))), inset))
        assertEquals(
            TouchTarget.Apps,
            router.route(Touch(0, 0, listOf(TouchPointer(0, 400, 100))), inset)?.target,
        )
    }
}
