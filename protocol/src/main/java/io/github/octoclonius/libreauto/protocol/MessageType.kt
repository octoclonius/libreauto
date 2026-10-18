package io.github.octoclonius.libreauto.protocol

object MessageType {
    const val VERSION_REQUEST = 0x0001
    const val VERSION_RESPONSE = 0x0002
    const val TLS_HANDSHAKE = 0x0003
    const val AUTH_COMPLETE = 0x0004
    const val SERVICE_DISCOVERY_REQUEST = 0x0005
    const val SERVICE_DISCOVERY_RESPONSE = 0x0006
    const val CHANNEL_OPEN_REQUEST = 0x0007
    const val CHANNEL_OPEN_RESPONSE = 0x0008
    const val PING_REQUEST = 0x000B
    const val PING_RESPONSE = 0x000C
    const val BYE_BYE_REQUEST = 0x000F
    const val BYE_BYE_RESPONSE = 0x0010
    const val MEDIA_SETUP_REQUEST = 0x8000
    const val MEDIA_SETUP_RESPONSE = 0x8003
}
