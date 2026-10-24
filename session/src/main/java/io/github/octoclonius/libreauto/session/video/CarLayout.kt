package io.github.octoclonius.libreauto.session.video

import android.util.DisplayMetrics
import io.github.octoclonius.libreauto.protocol.media.CarInsets
import io.github.octoclonius.libreauto.protocol.media.VideoFormat
import kotlin.math.roundToInt

const val RAIL_THICKNESS_DP = 80

private const val TABLET_SMALLEST_WIDTH_DP = 600

enum class RailSide {
    Left,
    Right,
    Bottom,
}

internal data class Area(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int
        get() = right - left

    val height: Int
        get() = bottom - top

    fun contains(x: Int, y: Int): Boolean = x in left until right && y in top until bottom
}

internal data class CarLayout(
    val format: VideoFormat,
    val railSide: RailSide,
    val rail: Area,
    val railContentInsets: CarInsets,
    val apps: Area,
    val appsDensity: Int,
)

internal fun carLayout(
    format: VideoFormat,
    isDriverOnRight: Boolean,
    maxAppsDensity: Int = format.density,
): CarLayout {
    val width = format.displayWidth
    val height = format.displayHeight
    val permanent = format.displayStableInsets.union(format.displayCutoutInsets)
    val covered = permanent.union(format.displayContentInsets)
    val thickness =
        (RAIL_THICKNESS_DP.toDouble() * format.density / DisplayMetrics.DENSITY_DEFAULT)
            .roundToInt()
    val side =
        railSide(
            width - permanent.left - permanent.right,
            height - permanent.top - permanent.bottom,
            thickness,
            isDriverOnRight,
        )
    val (rail, besideRail) =
        when (side) {
            RailSide.Left -> {
                val edge = permanent.left + thickness
                Area(0, 0, edge, height) to Area(edge, 0, width, height)
            }
            RailSide.Right -> {
                val edge = width - permanent.right - thickness
                Area(edge, 0, width, height) to Area(0, 0, edge, height)
            }
            RailSide.Bottom -> {
                val edge = height - permanent.bottom - thickness
                Area(0, edge, width, height) to Area(0, 0, width, edge)
            }
        }
    val apps = besideRail.inset(covered.within(besideRail, width, height))
    return CarLayout(
        format,
        side,
        rail = rail,
        railContentInsets = covered.within(rail, width, height),
        apps = apps,
        appsDensity =
            minOf(
                maxAppsDensity,
                minOf(apps.width, apps.height) * DisplayMetrics.DENSITY_DEFAULT /
                    TABLET_SMALLEST_WIDTH_DP,
            ),
    )
}

private fun CarInsets.union(other: CarInsets): CarInsets =
    CarInsets(
        top = maxOf(top, other.top),
        bottom = maxOf(bottom, other.bottom),
        left = maxOf(left, other.left),
        right = maxOf(right, other.right),
    )

private fun CarInsets.within(area: Area, width: Int, height: Int): CarInsets =
    CarInsets(
        top = (top - area.top).coerceAtLeast(0),
        bottom = (bottom - (height - area.bottom)).coerceAtLeast(0),
        left = (left - area.left).coerceAtLeast(0),
        right = (right - (width - area.right)).coerceAtLeast(0),
    )

private fun Area.inset(insets: CarInsets): Area =
    Area(left + insets.left, top + insets.top, right - insets.right, bottom - insets.bottom)

private fun railSide(width: Int, height: Int, thickness: Int, isDriverOnRight: Boolean): RailSide =
    when {
        width - thickness <= height -> RailSide.Bottom
        isDriverOnRight -> RailSide.Right
        else -> RailSide.Left
    }
