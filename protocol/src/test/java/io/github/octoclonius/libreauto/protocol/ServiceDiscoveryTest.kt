package io.github.octoclonius.libreauto.protocol

import java.net.ProtocolException
import okio.ByteString.Companion.toByteString
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ServiceDiscoveryTest {

    @Test
    fun theRequestHasTheLabelAsField4AndTheDeviceNameAsField5() {
        assertArrayEquals(
            message(4, "Android".toByteArray()) + message(5, "Pixel 9".toByteArray()),
            serviceDiscoveryRequest("Pixel 9"),
        )
    }

    @Test
    fun theResponseListsEveryServiceWithItsChannel() {
        val body =
            message(1, varint(1, 1), message(2)) +
                message(1, varint(1, 2), message(3, varint(1, 1))) +
                message(1, varint(1, 3), message(4)) +
                message(1, varint(1, 4), message(6, message(1, "Car".toByteArray()))) +
                message(1, varint(1, 5), message(15)) +
                message(2, "Google".toByteArray())

        val services = parseServiceDiscoveryResponse(body).services

        assertEquals(listOf(1, 2, 3, 4, 5), services.map { it.id })
        assertNotNull(services[0].sensor_source_service)
        assertNotNull(services[1].media_sink_service)
        assertNotNull(services[2].input_source_service)
        assertNotNull(services[3].bluetooth_service)
        assertEquals(Service(id = 5, unknownFields = message(15).toByteString()), services[4])
    }

    @Test
    fun aServiceWithoutAChannelIsRefused() {
        val refusal =
            assertThrows<ProtocolException> {
                parse(Service(media_sink_service = MediaSinkService()))
            }
        assertEquals("Service without a channel", refusal.message)
    }

    @Test
    fun aChannelOutside1To255IsRefused() {
        assertThrows<ProtocolException> { parse(Service(id = 0)) }
        assertThrows<ProtocolException> { parse(Service(id = 0x100)) }
    }

    @Test
    fun twoServicesOnOneChannelAreRefused() {
        val refusal = assertThrows<ProtocolException> { parse(Service(id = 3), Service(id = 3)) }
        assertEquals("Second service on channel 3", refusal.message)
    }

    @Test
    fun theCarIsNamedByItsDisplayName() {
        val body =
            message(2, "Make".toByteArray()) +
                message(3, "Model".toByteArray()) +
                message(14, "My car".toByteArray())
        assertEquals("My car", carName(parseServiceDiscoveryResponse(body)))
    }

    @Test
    fun aCarWithoutADisplayNameIsNamedByItsMakeAndModel() {
        val body =
            message(2, "Make".toByteArray()) +
                message(3, "Model".toByteArray()) +
                message(14)
        assertEquals("Make Model", carName(parseServiceDiscoveryResponse(body)))
    }

    @Test
    fun aCarWithoutANameHasNone() {
        assertNull(carName(parseServiceDiscoveryResponse(message(2) + message(3))))
    }

    private fun parse(vararg services: Service) =
        parseServiceDiscoveryResponse(ServiceDiscoveryResponse(services.toList()).encode())

    private fun varint(number: Int, value: Int) =
        byteArrayOf((number shl 3).toByte(), value.toByte())

    private fun message(number: Int, vararg fields: ByteArray): ByteArray {
        val body = fields.fold(ByteArray(0), ByteArray::plus)
        return byteArrayOf((number shl 3 or 2).toByte(), body.size.toByte()) + body
    }
}
