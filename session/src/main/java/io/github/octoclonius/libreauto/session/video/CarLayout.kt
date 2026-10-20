package io.github.octoclonius.libreauto.session.video

import android.util.DisplayMetrics
import io.github.octoclonius.libreauto.protocol.media.CarInsets
import io.github.octoclonius.libreauto.protocol.media.VideoFormat
import kotlin.math.roundToInt

const val RAIL_THICKNESS_DP = 80

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
    val appsContentInsets: CarInsets,
)

internal fun carLayout(format: VideoFormat, isDriverOnRight: Boolean): CarLayout {
    val width = format.displayWidth
    val height = format.displayHeight
    val insets = format.displayContentInsets
    val thickness =
        (RAIL_THICKNESS_DP.toDouble() * format.density / DisplayMetrics.DENSITY_DEFAULT)
            .roundToInt()
    val side =
        railSide(
            width - insets.left - insets.right,
            height - insets.top - insets.bottom,
            thickness,
            isDriverOnRight,
        )
    return when (side) {
        RailSide.Left -> {
            val edge = insets.left + thickness
            CarLayout(
                format,
                side,
                rail = Area(0, 0, edge, height),
                railContentInsets = insets.copy(right = 0),
                apps = Area(edge, 0, width, height),
                appsContentInsets = insets.copy(left = 0),
            )
        }
        RailSide.Right -> {
            val edge = width - insets.right - thickness
            CarLayout(
                format,
                side,
                rail = Area(edge, 0, width, height),
                railContentInsets = insets.copy(left = 0),
                apps = Area(0, 0, edge, height),
                appsContentInsets = insets.copy(right = 0),
            )
        }
        RailSide.Bottom -> {
            val edge = height - insets.bottom - thickness
            CarLayout(
                format,
                side,
                rail = Area(0, edge, width, height),
                railContentInsets = insets.copy(top = 0),
                apps = Area(0, 0, width, edge),
                appsContentInsets = insets.copy(bottom = 0),
            )
        }
    }
}

private fun railSide(width: Int, height: Int, thickness: Int, isDriverOnRight: Boolean): RailSide =
    when {
        width - thickness <= height -> RailSide.Bottom
        isDriverOnRight -> RailSide.Right
        else -> RailSide.Left
    }
