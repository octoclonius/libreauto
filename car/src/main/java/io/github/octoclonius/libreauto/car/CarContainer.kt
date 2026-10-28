package io.github.octoclonius.libreauto.car

import android.content.Context
import io.github.octoclonius.libreauto.car.apps.PhoneAppsRepository
import io.github.octoclonius.libreauto.car.home.HomeAppsRepository
import io.github.octoclonius.libreauto.car.rail.RailOrderRepository
import io.github.octoclonius.libreauto.session.CarSessionHolder
import io.github.octoclonius.libreauto.session.settings.PhoneSettingsRepository

class CarContainer(
    context: Context,
    carSessionHolder: CarSessionHolder,
    phoneSettingsRepository: PhoneSettingsRepository,
) {

    val homeAppsRepository = HomeAppsRepository(context)
    val phoneAppsRepository = PhoneAppsRepository(context, homeAppsRepository, carSessionHolder)
    val railOrderRepository = RailOrderRepository(context)
    val carUiHolder =
        CarUiHolder(context, carSessionHolder, phoneSettingsRepository, railOrderRepository)

    interface Provider {
        val carContainer: CarContainer
    }
}

val Context.carContainer: CarContainer
    get() = (applicationContext as CarContainer.Provider).carContainer
