package io.github.octoclonius.libreauto

import android.content.ComponentName
import android.content.Context
import io.github.octoclonius.libreauto.car.CarContainer
import io.github.octoclonius.libreauto.car.CarHomeActivity
import io.github.octoclonius.libreauto.privileged.PrivilegedService
import io.github.octoclonius.libreauto.session.SessionContainer

class AppContainer(context: Context) {

    val sessionContainer =
        SessionContainer(
            context,
            privilegedService = ComponentName(context, PrivilegedService::class.java),
            carHome = ComponentName(context, CarHomeActivity::class.java),
        )
    val carContainer =
        CarContainer(
            context,
            sessionContainer.carSessionHolder,
            sessionContainer.phoneSettingsRepository,
        )
}
