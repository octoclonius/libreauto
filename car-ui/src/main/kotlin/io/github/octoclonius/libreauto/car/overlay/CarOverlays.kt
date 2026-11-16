package io.github.octoclonius.libreauto.car.overlay

import io.github.octoclonius.libreauto.car.calls.CallScreenState
import io.github.octoclonius.libreauto.car.calls.CallScreenSurface
import io.github.octoclonius.libreauto.car.calls.CallScreenSurfaces
import io.github.octoclonius.libreauto.car.quicksettings.QuickSettingsState
import io.github.octoclonius.libreauto.projection.display.CarDisplays
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

internal class CarOverlays(
    private val displays: CarDisplays,
    private val quickSettings: QuickSettingsState,
    private val isPopUpShown: StateFlow<Boolean>,
    private val callScreen: CallScreenState,
) {

    private var expected = CallScreenSurfaces()

    fun start(scope: CoroutineScope) {
        scope.launch {
            merge(quickSettings.isOpen, isPopUpShown, callScreen.surfaces).collect { update() }
        }
    }

    private fun update() {
        val surfaces = callScreen.surfaces.value
        surfaces.ongoingCallId
            ?.takeIf { it != expected.ongoingCallId }
            ?.let(displays.ongoingCallScreen::expect)
        surfaces.ringingCallId
            ?.takeIf { it != expected.ringingCallId }
            ?.let(displays.ringingCallScreen::expect)
        expected = surfaces
        displays.showOverlays(
            buildSet {
                if (quickSettings.isOpen.value) add(displays.panel)
                if (isPopUpShown.value) add(displays.popUp)
                when (surfaces.shown) {
                    CallScreenSurface.Ongoing -> add(displays.ongoingCallScreen)
                    CallScreenSurface.Ringing -> add(displays.ringingCallScreen)
                    null -> {}
                }
            }
        )
    }
}
