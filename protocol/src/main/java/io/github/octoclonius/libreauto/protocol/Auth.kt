package io.github.octoclonius.libreauto.protocol

import java.io.IOException

@Throws(IOException::class)
fun parseAuthComplete(body: ByteArray): Int =
    AuthComplete.ADAPTER.decode(body).status ?: MessageStatus.SUCCESS
