package io.github.octoclonius.libreauto.projection.display

import android.graphics.Rect
import android.view.MotionEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class OverlayDisplayState(val id: Int)

internal interface OverlayLayer {
    fun expect(contentId: Int)

    fun reportDrawn(contentId: Int, latchNanos: Long)
}

class OverlayDisplay internal constructor(val name: String) {
    private val lock = Any()

    private val _state = MutableStateFlow<OverlayDisplayState?>(null)
    val state: StateFlow<OverlayDisplayState?> = _state.asStateFlow()

    private val _touches = MutableSharedFlow<MotionEvent>(extraBufferCapacity = TOUCH_BUFFER)
    val touches: SharedFlow<MotionEvent> = _touches.asSharedFlow()

    val outsideTouches = TouchCapture()

    @Volatile
    var isShown = false
        internal set

    @Volatile private var touchArea: Rect? = null

    private var layer: OverlayLayer? = null
    private var expectedContent: Int? = null

    fun expect(contentId: Int) {
        synchronized(lock) {
            expectedContent = contentId
            layer?.expect(contentId)
        }
    }

    fun reportDrawn(contentId: Int, latchNanos: Long) {
        synchronized(lock) { layer?.reportDrawn(contentId, latchNanos) }
    }

    fun setTouchArea(area: Rect?) {
        touchArea = area
    }

    internal fun takesTouchAt(x: Int, y: Int): Boolean =
        isShown && touchArea.let { it == null || it.contains(x, y) }

    internal fun onCreated(id: Int, layer: OverlayLayer) {
        synchronized(lock) {
            this.layer = layer
            expectedContent?.let(layer::expect)
        }
        _state.value = OverlayDisplayState(id)
    }

    internal fun onReleased(id: Int) {
        synchronized(lock) { layer = null }
        isShown = false
        _state.update { if (it?.id == id) null else it }
    }

    internal fun onTouch(event: MotionEvent): Boolean = _touches.tryEmit(event)
}
