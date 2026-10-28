package io.github.octoclonius.libreauto.car

import android.content.Context
import io.github.octoclonius.libreauto.car.apps.PhoneAppsRepository
import io.github.octoclonius.libreauto.car.home.HomeAppsRepository
import io.github.octoclonius.libreauto.session.CarSessionHolder

class CarContainer(context: Context, carSessionHolder: CarSessionHolder) {

    val homeAppsRepository = HomeAppsRepository(context)
    val phoneAppsRepository = PhoneAppsRepository(context, homeAppsRepository, carSessionHolder)
    val carUiHolder = CarUiHolder(context, carSessionHolder)

    interface Provider {
        val carContainer: CarContainer
    }
}

val Context.carContainer: CarContainer
    get() = (applicationContext as CarContainer.Provider).carContainer
