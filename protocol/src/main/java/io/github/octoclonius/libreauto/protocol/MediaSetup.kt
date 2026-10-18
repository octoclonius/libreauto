package io.github.octoclonius.libreauto.protocol

import java.io.IOException

private const val VIDEO_H264_BP = 3

fun videoSetupRequest(): ByteArray = MediaSetupRequest(codec = VIDEO_H264_BP).encode()

@Throws(IOException::class)
fun parseMediaSetupResponse(body: ByteArray): MediaSetupResponse =
    MediaSetupResponse.ADAPTER.decode(body)
