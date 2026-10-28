package io.github.octoclonius.libreauto.car

import android.content.Context
import io.github.octoclonius.libreauto.session.CarSessionHolder
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CarUiHolder(context: Context, private val carSessionHolder: CarSessionHolder) {

    private val context = context.applicationContext
    private val scope = MainScope()

    private val _carUi = MutableStateFlow<CarUi?>(null)
    val carUi: StateFlow<CarUi?> = _carUi.asStateFlow()

    fun start() {
        scope.launch {
            carSessionHolder.session.collect { session ->
                _carUi.value?.close()
                _carUi.value =
                    session?.let { CarUi(context, it, carSessionHolder.state).also(CarUi::start) }
            }
        }
    }
}
