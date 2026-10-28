package io.github.octoclonius.libreauto.session.video

import io.github.octoclonius.libreauto.session.privileged.PrivilegedServiceConnection

class CarDisplays internal constructor(privileged: PrivilegedServiceConnection, home: CarHome) {

    val car = CarDisplay(privileged, home)
    val rail = RailDisplay()
    val overlay = OverlayDisplay()
}
