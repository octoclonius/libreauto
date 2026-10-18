package io.github.octoclonius.libreauto.protocol

import java.io.IOException

@Throws(IOException::class)
fun parseByeByeRequest(body: ByteArray): Int? = ByeByeRequest.ADAPTER.decode(body).reason

fun byeByeResponse(): ByteArray = ByeByeResponse().encode()
