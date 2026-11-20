package io.github.octoclonius.libreauto.projection.video

internal class TopOff(private val decoderDepth: Int) {

    private var hasNewPicture = false
    private var framesSincePicture = decoderDepth
    private var debtBits = 0L

    val isNeeded: Boolean
        get() = hasNewPicture || framesSincePicture < decoderDepth || debtBits > 0

    fun onNewPicture() {
        hasNewPicture = true
    }

    fun onDrawn() {
        framesSincePicture = if (hasNewPicture) 0 else framesSincePicture + 1
        hasNewPicture = false
    }

    fun onEncoded(sizeBits: Long, shareBits: Long) {
        debtBits = maxOf(0, debtBits + sizeBits - shareBits)
    }

    fun reset() {
        hasNewPicture = false
        framesSincePicture = decoderDepth
        debtBits = 0
    }
}
