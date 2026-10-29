package io.github.octoclonius.libreauto

import android.content.ComponentName
import android.content.Context
import io.github.octoclonius.libreauto.car.CarContainer
import io.github.octoclonius.libreauto.car.CarHomeActivity
import io.github.octoclonius.libreauto.privileged.PrivilegedService
import io.github.octoclonius.libreauto.projection.ProjectionContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AppContainer(context: Context) {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val projectionContainer =
        ProjectionContainer(
            context,
            applicationScope,
            privilegedService = ComponentName(context, PrivilegedService::class.java),
            carHome = ComponentName(context, CarHomeActivity::class.java),
        )
    val carContainer =
        CarContainer(
            context,
            applicationScope,
            projectionContainer.carSessionHolder,
            projectionContainer.phoneSettingsRepository,
        )
}
