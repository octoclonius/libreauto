package io.github.octoclonius.libreauto.projection.video

import io.github.octoclonius.libreauto.projection.display.CarLayout

internal interface VideoProfiler {
    fun onStart(session: Int, layout: CarLayout, encoder: String, bitRate: Int) {}

    fun onDrawn(presentationNanos: Long) {}

    fun onEncoded(presentationMicros: Long, size: Int, isKeyFrame: Boolean) {}

    fun onPermitted(presentationMicros: Long) {}

    fun onSent(presentationMicros: Long) {}

    fun onAcknowledged(session: Int, count: Int) {}

    fun onStop(session: Int) {}
}
