package io.github.octoclonius.libreauto.projection.display

class CarDisplays internal constructor(home: CarHome) {

    val car = CarDisplay(home)
    val rail = RailDisplay()
    val popUp = OverlayDisplay("pop-up")
    val panel = OverlayDisplay("panel")

    internal val overlays = listOf(popUp, panel)
}
