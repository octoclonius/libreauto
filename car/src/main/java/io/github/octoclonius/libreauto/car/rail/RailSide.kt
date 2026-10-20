package io.github.octoclonius.libreauto.car.rail

enum class RailSide {
    Left,
    Right,
    Bottom,
}

fun railSide(width: Int, height: Int, thickness: Int, isDriverOnRight: Boolean): RailSide =
    when {
        width - thickness <= height -> RailSide.Bottom
        isDriverOnRight -> RailSide.Right
        else -> RailSide.Left
    }
