package io.github.octoclonius.libreauto.protocol.media

import java.io.IOException

fun audioSetupRequest(codec: AudioCodec): ByteArray = MediaSetupRequest(codec = codec.type).encode()

fun videoSetupRequest(codec: VideoCodec): ByteArray = MediaSetupRequest(codec = codec.type).encode()

@Throws(IOException::class)
fun parseMediaSetupResponse(body: ByteArray): MediaSetupResponse =
    MediaSetupResponse.ADAPTER.decode(body)
