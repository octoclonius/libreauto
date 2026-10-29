package io.github.octoclonius.libreauto.protocol.media

import java.io.IOException

private const val VIDEO_FOCUS_PROJECTED = 1
private const val VIDEO_FOCUS_NATIVE = 2
private const val VIDEO_FOCUS_NATIVE_TRANSIENT = 3
private const val VIDEO_FOCUS_REASON_UNKNOWN = 0

fun projectedVideoFocusRequest(): ByteArray =
    VideoFocusRequest(mode = VIDEO_FOCUS_PROJECTED, reason = VIDEO_FOCUS_REASON_UNKNOWN).encode()

@Throws(IOException::class)
fun parseVideoFocusNotification(body: ByteArray): VideoFocusNotification =
    VideoFocusNotification.ADAPTER.decode(body)

val VideoFocusNotification.isProjected: Boolean
    get() = mode != VIDEO_FOCUS_NATIVE && mode != VIDEO_FOCUS_NATIVE_TRANSIENT
