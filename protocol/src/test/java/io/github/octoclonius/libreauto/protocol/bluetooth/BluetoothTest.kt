package io.github.octoclonius.libreauto.protocol.bluetooth

import io.github.octoclonius.libreauto.protocol.control.ProtocolVersion
import io.github.octoclonius.libreauto.protocol.control.parseServiceDiscoveryResponse
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BluetoothTest {

    private val numericComparison = 2
    private val passkeyEntry = 3
    private val pin = 4
    private val carAddress = "00:11:22:33:44:55"

    @Test
    fun theCarsBluetoothIsItsChannelAddressAndMethod() {
        val bluetooth =
            carBluetooth(discovery(service(9, string(1, carAddress), packed(2, numericComparison))))
        assertEquals(9, bluetooth?.channel)
        assertEquals(carAddress, bluetooth?.carAddress)
        assertEquals(numericComparison, bluetooth?.pairingMethod)
    }

    @Test
    fun pinWinsWhenTheCarOffersBoth() {
        val bluetooth =
            carBluetooth(
                discovery(service(9, string(1, carAddress), packed(2, pin, numericComparison)))
            )
        assertEquals(pin, bluetooth?.pairingMethod)
    }

    @Test
    fun unpackedMethodsAreRead() {
        val bluetooth =
            carBluetooth(discovery(service(9, string(1, carAddress), varint(2, numericComparison))))
        assertEquals(numericComparison, bluetooth?.pairingMethod)
    }

    @Test
    fun aCarWithoutASupportedMethodHasNoBluetooth() {
        assertNull(
            carBluetooth(discovery(service(9, string(1, carAddress), packed(2, passkeyEntry))))
        )
    }

    @Test
    fun aCarWithoutAnAddressHasNoBluetooth() {
        assertNull(carBluetooth(discovery(service(9, packed(2, pin)))))
        assertNull(carBluetooth(discovery(service(9, string(1, ""), packed(2, pin)))))
    }

    @Test
    fun theSkipAddressHasNoBluetooth() {
        assertNull(
            carBluetooth(discovery(service(9, string(1, "SKIP_THIS_BLUETOOTH"), packed(2, pin))))
        )
    }

    @Test
    fun aCarWithoutABluetoothServiceHasNoBluetooth() {
        assertNull(carBluetooth(discovery(varint(1, 5) + message(3, varint(2, 3)))))
    }

    @Test
    fun theRequestHasThePhonesAddressAsField1AndTheMethodAsField2() {
        assertArrayEquals(
            string(1, carAddress) + varint(2, pin),
            pairingRequest(carAddress, pin),
        )
    }

    @Test
    fun theResponseHasTheStatusAsField1AndAlreadyPairedAsField2() {
        val response = parsePairingResponse(varint(1, 0) + varint(2, 1))
        assertEquals(0, response.status)
        assertTrue(response.isAlreadyPaired)
        assertFalse(parsePairingResponse(varint(1, 0)).isAlreadyPaired)
    }

    @Test
    fun theAuthenticationDataIsField1() {
        assertEquals("123456", parseAuthenticationData(string(1, "123456")))
        assertNull(parseAuthenticationData(ByteArray(0)))
    }

    @Test
    fun theAuthenticationResultHasTheStatusAsField1() {
        assertArrayEquals(
            bytes(0x08, 0xF1, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0x01),
            authenticationResult(-15),
        )
    }

    @Test
    fun theAuthenticationResultIsSentFromProtocol1Point5() {
        assertFalse(isAuthenticationResultSent(ProtocolVersion(1, 4)))
        assertTrue(isAuthenticationResultSent(ProtocolVersion(1, 5)))
        assertTrue(isAuthenticationResultSent(ProtocolVersion(2, 0)))
    }

    private fun discovery(vararg services: ByteArray) =
        parseServiceDiscoveryResponse(services.map { message(1, it) }.reduce(ByteArray::plus))

    private fun service(channel: Int, vararg fields: ByteArray) =
        varint(1, channel) + message(6, *fields)

    private fun varint(number: Int, value: Int): ByteArray =
        byteArrayOf((number shl 3).toByte()) + rawVarint(value)

    private fun rawVarint(value: Int): ByteArray {
        val out = mutableListOf<Byte>()
        var rest = value
        while (rest >= 0x80) {
            out += ((rest and 0x7F) or 0x80).toByte()
            rest = rest ushr 7
        }
        out += rest.toByte()
        return out.toByteArray()
    }

    private fun packed(number: Int, vararg values: Int): ByteArray {
        val body = values.map(::rawVarint).fold(ByteArray(0), ByteArray::plus)
        return byteArrayOf((number shl 3 or 2).toByte(), body.size.toByte()) + body
    }

    private fun string(number: Int, value: String): ByteArray {
        val body = value.toByteArray()
        return byteArrayOf((number shl 3 or 2).toByte(), body.size.toByte()) + body
    }

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    private fun message(number: Int, vararg fields: ByteArray): ByteArray {
        val body = fields.fold(ByteArray(0), ByteArray::plus)
        return byteArrayOf((number shl 3 or 2).toByte(), body.size.toByte()) + body
    }
}
