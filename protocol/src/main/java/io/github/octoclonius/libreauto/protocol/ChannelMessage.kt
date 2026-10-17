package io.github.octoclonius.libreauto.protocol

class ChannelMessage(
    val channel: Int,
    val isEncrypted: Boolean,
    val type: Int,
    val body: ByteArray,
)

internal const val MESSAGE_TYPE_SIZE = 2
