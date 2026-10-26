package io.github.octoclonius.libreauto.session.video

import android.graphics.Insets
import io.github.octoclonius.libreauto.protocol.media.CarInsets
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class CarDisplayState(val id: Int, val carDensity: Int)

object CarDisplay {
    private val _state = MutableStateFlow<CarDisplayState?>(null)
    val state: StateFlow<CarDisplayState?> = _state.asStateFlow()

    private val _carDensity = MutableStateFlow<Int?>(null)
    val carDensity: StateFlow<Int?> = _carDensity.asStateFlow()

    internal fun onCarDensity(carDensity: Int?) {
        _carDensity.value = carDensity
    }

    internal fun onCreated(id: Int, carDensity: Int) {
        _state.value = CarDisplayState(id, carDensity)
    }

    internal fun onReleased(id: Int) {
        _state.update { if (it?.id == id) null else it }
    }
}

internal fun CarInsets.toInsets(): Insets = Insets.of(left, top, right, bottom)
