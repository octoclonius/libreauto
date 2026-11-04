package io.github.octoclonius.libreauto.car

import android.content.Context
import io.github.octoclonius.libreauto.car.apps.PhoneAppsRepository
import io.github.octoclonius.libreauto.car.home.HomeAppsRepository
import io.github.octoclonius.libreauto.car.rail.RailOrderRepository
import io.github.octoclonius.libreauto.projection.CarSessionHolder
import io.github.octoclonius.libreauto.projection.notifications.NotificationRepository
import io.github.octoclonius.libreauto.projection.settings.PhoneSettingsRepository
import kotlinx.coroutines.CoroutineScope

class CarContainer(
    context: Context,
    applicationScope: CoroutineScope,
    carSessionHolder: CarSessionHolder,
    phoneSettingsRepository: PhoneSettingsRepository,
    notificationRepository: NotificationRepository,
) {

    val phoneAppsRepository = PhoneAppsRepository(context, carSessionHolder, applicationScope)
    val homeAppsRepository = HomeAppsRepository(context, phoneAppsRepository, applicationScope)
    val railOrderRepository = RailOrderRepository(context, applicationScope)
    val carUiHolder =
        CarUiHolder(
            context,
            carSessionHolder,
            phoneSettingsRepository,
            railOrderRepository,
            notificationRepository,
            applicationScope,
        )

    interface Provider {
        val carContainer: CarContainer
    }
}

val Context.carContainer: CarContainer
    get() = (applicationContext as CarContainer.Provider).carContainer
