package io.github.octoclonius.libreauto.protocol.media

private const val FIRST_SESSION = 0

fun videoStart(configIndex: Int): ByteArray = mediaStart(FIRST_SESSION, configIndex)

fun mediaStart(session: Int, configIndex: Int): ByteArray =
    MediaStart(session = session, config_index = configIndex).encode()
