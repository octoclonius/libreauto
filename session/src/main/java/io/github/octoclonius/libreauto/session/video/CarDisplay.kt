package io.github.octoclonius.libreauto.session.video

import android.graphics.Insets
import io.github.octoclonius.libreauto.protocol.media.CarInsets
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class CarDisplayState(val id: Int, val contentInsets: Insets, val carDensity: Int)

object CarDisplay {
    private val _state = MutableStateFlow<CarDisplayState?>(null)
    val state: StateFlow<CarDisplayState?> = _state.asStateFlow()

    internal fun onCreated(id: Int, contentInsets: CarInsets, carDensity: Int) {
        _state.value = CarDisplayState(id, contentInsets.toInsets(), carDensity)
    }

    internal fun onReleased(id: Int) {
        _state.update { if (it?.id == id) null else it }
    }
}

internal fun CarInsets.toInsets(): Insets = Insets.of(left, top, right, bottom)
