package io.github.octoclonius.libreauto.protocol

import java.io.IOException

private const val VIDEO_FOCUS_NATIVE = 2
private const val VIDEO_FOCUS_NATIVE_TRANSIENT = 3

@Throws(IOException::class)
fun parseVideoFocusNotification(body: ByteArray): VideoFocusNotification =
    VideoFocusNotification.ADAPTER.decode(body)

val VideoFocusNotification.isProjected: Boolean
    get() = mode != VIDEO_FOCUS_NATIVE && mode != VIDEO_FOCUS_NATIVE_TRANSIENT
