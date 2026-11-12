package io.github.octoclonius.libreauto.car

import android.content.Context
import io.github.octoclonius.libreauto.car.rail.RailOrderRepository
import io.github.octoclonius.libreauto.projection.CarSessionHolder
import io.github.octoclonius.libreauto.projection.calls.CallRepository
import io.github.octoclonius.libreauto.projection.notifications.NotificationRepository
import io.github.octoclonius.libreauto.projection.settings.PhoneSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CarUiHolder(
    context: Context,
    private val carSessionHolder: CarSessionHolder,
    private val phoneSettings: PhoneSettingsRepository,
    private val railOrder: RailOrderRepository,
    private val notifications: NotificationRepository,
    private val calls: CallRepository,
    private val externalScope: CoroutineScope,
) {

    private val context = context.applicationContext

    private val _carUi = MutableStateFlow<CarUi?>(null)
    val carUi: StateFlow<CarUi?> = _carUi.asStateFlow()

    fun start() {
        externalScope.launch {
            carSessionHolder.session.collect { session ->
                _carUi.value?.close()
                _carUi.value = session?.let {
                    CarUi(context, it, phoneSettings, railOrder, notifications, calls)
                        .also(CarUi::start)
                }
            }
        }
    }
}
