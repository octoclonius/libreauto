package io.github.octoclonius.libreauto

import android.app.Application
import io.github.octoclonius.libreauto.car.CarContainer
import io.github.octoclonius.libreauto.projection.ProjectionContainer

open class LibreAutoApplication :
    Application(), ProjectionContainer.Provider, CarContainer.Provider {

    lateinit var container: AppContainer
        private set

    override val projectionContainer: ProjectionContainer
        get() = container.projectionContainer

    override val carContainer: CarContainer
        get() = container.carContainer

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.projectionContainer.privilegedServiceConnection.start()
        container.projectionContainer.callRepository.start()
        container.projectionContainer.notificationRepository.start()
        container.carContainer.phoneAppsRepository.start()
        container.carContainer.carUiHolder.start()
    }
}
