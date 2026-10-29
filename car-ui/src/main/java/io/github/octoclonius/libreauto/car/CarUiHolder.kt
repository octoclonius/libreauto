package io.github.octoclonius.libreauto.car

import android.content.Context
import io.github.octoclonius.libreauto.car.rail.RailOrderRepository
import io.github.octoclonius.libreauto.session.CarSessionHolder
import io.github.octoclonius.libreauto.session.settings.PhoneSettingsRepository
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
                    CarUi(context, it, phoneSettings, railOrder).also(CarUi::start)
                }
            }
        }
    }
}
