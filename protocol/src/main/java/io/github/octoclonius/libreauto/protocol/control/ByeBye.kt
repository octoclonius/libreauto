package io.github.octoclonius.libreauto.protocol.control

import java.io.IOException

@Throws(IOException::class)
fun parseByeByeRequest(body: ByteArray): Int? = ByeByeRequest.ADAPTER.decode(body).reason

fun byeByeResponse(): ByteArray = ByeByeResponse().encode()
