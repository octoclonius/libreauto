package io.github.octoclonius.libreauto.protocol.bluetooth

import io.github.octoclonius.libreauto.protocol.control.ProtocolVersion
import io.github.octoclonius.libreauto.protocol.control.ServiceDiscoveryResponse
import java.io.IOException

object BluetoothPairingMethod {
    const val NUMERIC_COMPARISON = 2
    const val PIN = 4
}

private const val SKIPPED_ADDRESS = "SKIP_THIS_BLUETOOTH"
private val AUTHENTICATION_RESULT_VERSION = ProtocolVersion(1, 5)

class CarBluetooth(val channel: Int, val carAddress: String, val pairingMethod: Int)

class PairingResponse(val status: Int?, val isAlreadyPaired: Boolean)

fun carBluetooth(response: ServiceDiscoveryResponse): CarBluetooth? {
    for (service in response.services) {
        val bluetooth = service.bluetooth_service ?: continue
        val channel = service.id ?: continue
        val address = bluetooth.car_address?.takeIf { it.isNotEmpty() && it != SKIPPED_ADDRESS }
        val method = pairingMethod(bluetooth.supported_pairing_methods)
        if (address == null || method == null) return null
        return CarBluetooth(channel, address, method)
    }
    return null
}

private fun pairingMethod(methods: List<Int>): Int? =
    when {
        BluetoothPairingMethod.PIN in methods -> BluetoothPairingMethod.PIN
        BluetoothPairingMethod.NUMERIC_COMPARISON in methods ->
            BluetoothPairingMethod.NUMERIC_COMPARISON
        else -> null
    }

fun pairingRequest(phoneAddress: String, pairingMethod: Int): ByteArray =
    BluetoothPairingRequest(phone_address = phoneAddress, pairing_method = pairingMethod).encode()

@Throws(IOException::class)
fun parsePairingResponse(body: ByteArray): PairingResponse {
    val response = BluetoothPairingResponse.ADAPTER.decode(body)
    return PairingResponse(response.status, response.already_paired == true)
}

@Throws(IOException::class)
fun parseAuthenticationData(body: ByteArray): String? =
    BluetoothAuthenticationData.ADAPTER.decode(body).auth_data

fun authenticationResult(status: Int): ByteArray =
    BluetoothAuthenticationResult(status = status).encode()

fun isAuthenticationResultSent(version: ProtocolVersion): Boolean =
    version >= AUTHENTICATION_RESULT_VERSION
