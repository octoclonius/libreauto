package io.github.octoclonius.libreauto.protocol.control

import java.io.IOException
import java.net.ProtocolException

private const val LABEL_TEXT = "Android"
private const val MAX_CHANNEL = 0xFF

private const val DRIVER_POSITION_RIGHT = 1

private const val NATIVE_PHONE_SIGNAL = 2
private const val NATIVE_BATTERY = 4

internal const val DEFAULT_DISPLAY_ID = 0

fun serviceDiscoveryRequest(deviceName: String): ByteArray =
    ServiceDiscoveryRequest(label_text = LABEL_TEXT, device_name = deviceName).encode()

@Throws(IOException::class)
fun parseServiceDiscoveryResponse(body: ByteArray): ServiceDiscoveryResponse {
    val response = ServiceDiscoveryResponse.ADAPTER.decode(body)
    val channels = HashSet<Int>()
    for (service in response.services) {
        val channel = service.id ?: throw ProtocolException("Service without a channel")
        if (channel !in 1..MAX_CHANNEL) {
            throw ProtocolException("Service on channel $channel, outside 1..$MAX_CHANNEL")
        }
        if (!channels.add(channel)) throw ProtocolException("Second service on channel $channel")
    }
    return response
}

fun carName(response: ServiceDiscoveryResponse): String? =
    response.display_name?.takeIf { it.isNotBlank() }
        ?: listOfNotNull(response.make, response.model)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .ifEmpty { null }

fun isDriverOnRight(response: ServiceDiscoveryResponse): Boolean =
    response.driver_position == DRIVER_POSITION_RIGHT

fun isPhoneSignalOnCar(response: ServiceDiscoveryResponse): Boolean =
    isOnCar(response, NATIVE_PHONE_SIGNAL)

fun isBatteryOnCar(response: ServiceDiscoveryResponse): Boolean = isOnCar(response, NATIVE_BATTERY)

private fun isOnCar(response: ServiceDiscoveryResponse, flag: Int): Boolean =
    (response.native_status_flags ?: 0) and flag != 0
