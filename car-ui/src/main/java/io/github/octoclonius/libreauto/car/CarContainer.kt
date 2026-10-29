package io.github.octoclonius.libreauto.car

import android.content.Context
import io.github.octoclonius.libreauto.car.apps.PhoneAppsRepository
import io.github.octoclonius.libreauto.car.home.HomeAppsRepository
import io.github.octoclonius.libreauto.car.rail.RailOrderRepository
import io.github.octoclonius.libreauto.session.CarSessionHolder
import io.github.octoclonius.libreauto.session.settings.PhoneSettingsRepository
import kotlinx.coroutines.CoroutineScope

class CarContainer(
    context: Context,
    applicationScope: CoroutineScope,
    carSessionHolder: CarSessionHolder,
    phoneSettingsRepository: PhoneSettingsRepository,
) {

    val homeAppsRepository = HomeAppsRepository(context, applicationScope)
    val phoneAppsRepository =
        PhoneAppsRepository(context, homeAppsRepository, carSessionHolder, applicationScope)
    val railOrderRepository = RailOrderRepository(context, applicationScope)
    val carUiHolder =
        CarUiHolder(
            context,
            carSessionHolder,
            phoneSettingsRepository,
            railOrderRepository,
            applicationScope,
        )

    interface Provider {
        val carContainer: CarContainer
    }
}

val Context.carContainer: CarContainer
    get() = (applicationContext as CarContainer.Provider).carContainer
