package io.github.octoclonius.libreauto.protocol

import java.io.IOException

@Throws(IOException::class)
fun parseVideoFocusNotification(body: ByteArray): VideoFocusNotification =
    VideoFocusNotification.ADAPTER.decode(body)
