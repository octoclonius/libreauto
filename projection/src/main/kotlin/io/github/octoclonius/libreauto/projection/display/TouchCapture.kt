package io.github.octoclonius.libreauto.projection.display

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class TouchCapture internal constructor() {
    private val _captured = MutableSharedFlow<Unit>(extraBufferCapacity = TOUCH_BUFFER)
    val captured: SharedFlow<Unit> = _captured.asSharedFlow()

    @Volatile private var isCapturing = false

    fun setCapturing(isCapturing: Boolean) {
        this.isCapturing = isCapturing
    }

    internal fun capture(): Boolean {
        if (!isCapturing) return false
        _captured.tryEmit(Unit)
        return true
    }
}
