package io.github.octoclonius.libreauto.protocol.control

import java.io.IOException
import java.net.ProtocolException

private const val LABEL_TEXT = "Android"
private const val MAX_CHANNEL = 0xFF

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
