package io.github.octoclonius.libreauto

import io.github.octoclonius.libreauto.car.CarScreenPresenter
import io.github.octoclonius.libreauto.session.DebugCarService

class DebugApplication : LibreAutoApplication() {
    override fun onCreate() {
        super.onCreate()
        CarScreenPresenter(this, isTouchIndicatorEnabled = true).start()
        DebugCarService.start(this)
    }
}
