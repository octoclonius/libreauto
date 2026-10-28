package io.github.octoclonius.libreauto

import android.app.Application
import android.content.ComponentName
import io.github.octoclonius.libreauto.car.CarContainer
import io.github.octoclonius.libreauto.car.CarHomeActivity
import io.github.octoclonius.libreauto.privileged.PrivilegedService
import io.github.octoclonius.libreauto.session.SessionContainer
import io.github.octoclonius.libreauto.session.privileged.PrivilegedServiceConnection

open class LibreAutoApplication : Application(), SessionContainer.Provider, CarContainer.Provider {

    lateinit var container: AppContainer
        private set

    override val sessionContainer: SessionContainer
        get() = container.sessionContainer

    override val carContainer: CarContainer
        get() = container.carContainer

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        PrivilegedServiceConnection.start(
            this,
            service = ComponentName(this, PrivilegedService::class.java),
            carHome = ComponentName(this, CarHomeActivity::class.java),
        )
        container.sessionContainer.callRepository.start()
        container.carContainer.phoneAppsRepository.start()
        container.carContainer.carUiHolder.start()
    }
}
