package io.github.octoclonius.libreauto.protocol.control

import java.io.IOException

@Throws(IOException::class)
fun parsePingRequest(body: ByteArray): Long? = PingRequest.ADAPTER.decode(body).timestamp

fun pingResponse(timestamp: Long?): ByteArray = PingResponse(timestamp = timestamp).encode()
