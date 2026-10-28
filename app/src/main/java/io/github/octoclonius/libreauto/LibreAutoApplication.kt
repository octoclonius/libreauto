package io.github.octoclonius.libreauto

import android.app.Application
import io.github.octoclonius.libreauto.car.CarContainer
import io.github.octoclonius.libreauto.session.SessionContainer

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
        container.sessionContainer.privilegedServiceConnection.start()
        container.sessionContainer.callRepository.start()
        container.carContainer.phoneAppsRepository.start()
        container.carContainer.carUiHolder.start()
    }
}
