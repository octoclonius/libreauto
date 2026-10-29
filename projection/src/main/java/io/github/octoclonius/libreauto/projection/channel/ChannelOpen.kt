package io.github.octoclonius.libreauto.projection.channel

import android.util.Log
import io.github.octoclonius.libreauto.protocol.ChannelMessage
import io.github.octoclonius.libreauto.protocol.MessageStatus
import io.github.octoclonius.libreauto.protocol.control.parseChannelOpenResponse

internal fun isChannelOpened(tag: String, message: ChannelMessage, name: String): Boolean {
    val status = parseChannelOpenResponse(message.body)
    if (status != MessageStatus.SUCCESS) {
        Log.w(tag, "The car refused the $name channel ${message.channel}: status $status")
        return false
    }
    Log.i(tag, "The car opened the $name channel ${message.channel}")
    return true
}
