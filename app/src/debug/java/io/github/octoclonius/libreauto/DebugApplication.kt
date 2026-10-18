package io.github.octoclonius.libreauto

import io.github.octoclonius.libreauto.session.DebugCarService

class DebugApplication : LibreAutoApplication() {
    override fun onCreate() {
        super.onCreate()
        DebugCarService.start(this)
    }
}
