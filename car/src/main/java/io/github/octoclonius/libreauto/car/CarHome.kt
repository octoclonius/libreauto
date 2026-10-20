package io.github.octoclonius.libreauto.car

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import io.github.octoclonius.libreauto.car.rail.RAIL_THICKNESS
import io.github.octoclonius.libreauto.car.rail.Rail
import io.github.octoclonius.libreauto.car.rail.RailSide
import io.github.octoclonius.libreauto.car.rail.railSide
import io.github.octoclonius.libreauto.session.CarStatusBar

@Composable
fun CarHome(
    isDriverOnRight: Boolean,
    carStatusBar: CarStatusBar,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxSize().padding(contentPadding)) {
        val thickness = with(LocalDensity.current) { RAIL_THICKNESS.roundToPx() }
        val side = railSide(constraints.maxWidth, constraints.maxHeight, thickness, isDriverOnRight)
        val railModifier =
            when (side) {
                RailSide.Left ->
                    Modifier.align(AbsoluteAlignment.CenterLeft)
                        .fillMaxHeight()
                        .width(RAIL_THICKNESS)
                RailSide.Right ->
                    Modifier.align(AbsoluteAlignment.CenterRight)
                        .fillMaxHeight()
                        .width(RAIL_THICKNESS)
                RailSide.Bottom ->
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(RAIL_THICKNESS)
            }
        Rail(side, isDriverOnRight, carStatusBar, railModifier)
    }
}
