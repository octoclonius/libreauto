package io.github.octoclonius.libreauto.protocol.media

object MediaMessageType {
    const val DATA = 0x0000
    const val CODEC_CONFIG = 0x0001
    const val SETUP_REQUEST = 0x8000
    const val START = 0x8001
    const val STOP = 0x8002
    const val SETUP_RESPONSE = 0x8003
    const val ACK = 0x8004
    const val MICROPHONE_REQUEST = 0x8005
    const val MICROPHONE_RESPONSE = 0x8006
    const val VIDEO_FOCUS_REQUEST = 0x8007
    const val VIDEO_FOCUS_NOTIFICATION = 0x8008
}
