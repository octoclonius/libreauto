package io.github.octoclonius.libreauto

import android.app.Application
import android.content.ComponentName
import io.github.octoclonius.libreauto.car.CarHomeActivity
import io.github.octoclonius.libreauto.car.apps.PhoneApps
import io.github.octoclonius.libreauto.car.home.CarButtonHost
import io.github.octoclonius.libreauto.car.quicksettings.QuickSettingsHost
import io.github.octoclonius.libreauto.car.rail.RailHost
import io.github.octoclonius.libreauto.privileged.PrivilegedService
import io.github.octoclonius.libreauto.session.calls.PhoneCalls
import io.github.octoclonius.libreauto.session.privileged.PrivilegedServiceConnection

open class LibreAutoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        PrivilegedServiceConnection.start(
            this,
            service = ComponentName(this, PrivilegedService::class.java),
            carHome = ComponentName(this, CarHomeActivity::class.java),
        )
        PhoneCalls.start(this)
        PhoneApps.start(this)
        RailHost(this).start()
        QuickSettingsHost(this).start()
        CarButtonHost().start()
    }
}
