package io.github.octoclonius.libreauto.privileged

private val ADDRESS = Regex("([0-9A-F]{2}:){5}[0-9A-F]{2}")
internal const val MAX_PIN_BYTES = 16

internal fun bluetoothRequestError(address: String): String? =
    if (ADDRESS.matches(address)) null else "\"$address\" isn't a Bluetooth address"

internal fun pinRequestError(address: String, pin: String): String? {
    val size = pin.toByteArray().size
    return bluetoothRequestError(address)
        ?: if (size in 1..MAX_PIN_BYTES) null
        else "A PIN of $size bytes isn't 1 to $MAX_PIN_BYTES bytes"
}
