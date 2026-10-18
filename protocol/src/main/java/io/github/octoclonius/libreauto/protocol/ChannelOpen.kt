package io.github.octoclonius.libreauto.protocol

import java.io.IOException

private const val PRIORITY = 0

fun channelOpenRequest(channel: Int): ByteArray =
    ChannelOpenRequest(priority = PRIORITY, channel_id = channel).encode()

@Throws(IOException::class)
fun parseChannelOpenResponse(body: ByteArray): Int? =
    ChannelOpenResponse.ADAPTER.decode(body).status
