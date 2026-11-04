package io.github.octoclonius.libreauto.projection.display

import android.graphics.Insets
import android.view.MotionEvent
import io.github.octoclonius.libreauto.protocol.media.CarInsets
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal const val TOUCH_BUFFER = 64

data class RailDisplayState(val id: Int, val side: RailSide, val contentInsets: Insets)

class RailDisplay internal constructor() {
    private val _state = MutableStateFlow<RailDisplayState?>(null)
    val state: StateFlow<RailDisplayState?> = _state.asStateFlow()

    private val _touches = MutableSharedFlow<MotionEvent>(extraBufferCapacity = TOUCH_BUFFER)
    val touches: SharedFlow<MotionEvent> = _touches.asSharedFlow()

    val outsideTouches = TouchCapture()

    internal fun onCreated(id: Int, side: RailSide, contentInsets: CarInsets) {
        _state.value = RailDisplayState(id, side, contentInsets.toInsets())
    }

    internal fun onReleased(id: Int) {
        _state.update { if (it?.id == id) null else it }
    }

    internal fun onTouch(event: MotionEvent): Boolean = _touches.tryEmit(event)
}
