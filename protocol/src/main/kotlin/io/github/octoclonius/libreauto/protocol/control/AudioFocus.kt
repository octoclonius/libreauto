package io.github.octoclonius.libreauto.protocol.control

import java.io.IOException

object AudioFocusRequestType {
    const val GAIN = 1
    const val GAIN_TRANSIENT = 2
    const val GAIN_TRANSIENT_MAY_DUCK = 3
    const val RELEASE = 4
}

object AudioFocusState {
    const val GAIN = 1
    const val GAIN_TRANSIENT = 2
    const val LOSS = 3
    const val LOSS_TRANSIENT_CAN_DUCK = 4
    const val LOSS_TRANSIENT = 5
    const val GAIN_MEDIA_ONLY = 6
    const val GAIN_TRANSIENT_GUIDANCE_ONLY = 7
}

fun audioFocusRequest(type: Int): ByteArray = AudioFocusRequest(type = type).encode()

@Throws(IOException::class)
fun parseAudioFocusNotification(body: ByteArray): AudioFocusNotification =
    AudioFocusNotification.ADAPTER.decode(body)
