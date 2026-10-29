package io.github.octoclonius.libreauto.projection.display

import android.view.MotionEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class OverlayDisplayState(val id: Int)

class OverlayDisplay internal constructor() {
    private val lock = Any()

    private val _state = MutableStateFlow<OverlayDisplayState?>(null)
    val state: StateFlow<OverlayDisplayState?> = _state.asStateFlow()

    private val _touches = MutableSharedFlow<MotionEvent>(extraBufferCapacity = TOUCH_BUFFER)
    val touches: SharedFlow<MotionEvent> = _touches.asSharedFlow()

    @Volatile
    var isShown = false
        private set

    private var onShown: ((Boolean) -> Unit)? = null

    fun show(isShown: Boolean) {
        synchronized(lock) {
            this.isShown = isShown
            onShown?.invoke(isShown)
        }
    }

    internal fun onCreated(id: Int, onShown: (Boolean) -> Unit) {
        synchronized(lock) {
            this.onShown = onShown
            onShown(isShown)
        }
        _state.value = OverlayDisplayState(id)
    }

    internal fun onReleased(id: Int) {
        synchronized(lock) { onShown = null }
        _state.update { if (it?.id == id) null else it }
    }

    internal fun onTouch(event: MotionEvent): Boolean = _touches.tryEmit(event)
}
