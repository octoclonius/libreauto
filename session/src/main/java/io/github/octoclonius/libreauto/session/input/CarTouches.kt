package io.github.octoclonius.libreauto.session.input

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object CarTouches {
    private val _touches = MutableSharedFlow<Touch>(extraBufferCapacity = Channel.UNLIMITED)
    val touches: SharedFlow<Touch> = _touches.asSharedFlow()

    internal fun emit(touch: Touch): Boolean = _touches.tryEmit(touch)
}
