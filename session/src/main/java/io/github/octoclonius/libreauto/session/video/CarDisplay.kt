package io.github.octoclonius.libreauto.session.video

import android.graphics.Insets
import io.github.octoclonius.libreauto.protocol.media.CarInsets
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class CarDisplayState(val id: Int, val contentInsets: Insets)

object CarDisplay {
    private val _state = MutableStateFlow<CarDisplayState?>(null)
    val state: StateFlow<CarDisplayState?> = _state.asStateFlow()

    internal fun onCreated(id: Int, contentInsets: CarInsets) {
        _state.value =
            CarDisplayState(
                id,
                Insets.of(
                    contentInsets.left,
                    contentInsets.top,
                    contentInsets.right,
                    contentInsets.bottom,
                ),
            )
    }

    internal fun onReleased(id: Int) {
        _state.update { if (it?.id == id) null else it }
    }
}
