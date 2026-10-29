package io.github.octoclonius.libreauto.protocol.control

import io.github.octoclonius.libreauto.protocol.MessageStatus
import java.io.IOException

@Throws(IOException::class)
fun parseAuthComplete(body: ByteArray): Int =
    AuthComplete.ADAPTER.decode(body).status ?: MessageStatus.SUCCESS
