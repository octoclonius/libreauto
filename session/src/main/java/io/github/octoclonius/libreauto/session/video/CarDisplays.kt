package io.github.octoclonius.libreauto.session.video

class CarDisplays internal constructor(home: CarHome) {

    val car = CarDisplay(home)
    val rail = RailDisplay()
    val overlay = OverlayDisplay()
}
