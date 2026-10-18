package io.github.octoclonius.libreauto.protocol

private const val FIRST_SESSION = 0

fun videoStart(configIndex: Int): ByteArray =
    MediaStart(session = FIRST_SESSION, config_index = configIndex).encode()
