package io.github.octoclonius.libreauto.protocol.media

fun mediaStart(session: Int, configIndex: Int): ByteArray =
    MediaStart(session = session, config_index = configIndex).encode()
