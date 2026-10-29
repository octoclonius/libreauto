package io.github.octoclonius.libreauto.protocol

import java.security.cert.CertificateException
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class TlsServerTest {

    @Test
    fun aCarFromTheRootGetsATls12SessionBothWays() {
        val server = phoneTlsServer()
        val car = TestCar("car")
        car.handshakeWith(server)
        assertTrue(server.isEstablished)
        assertTrue(car.isEstablished)
        assertEquals("TLSv1.2", car.protocol)
        assertArrayEquals(byteArrayOf(1, 2, 3), car.decrypt(server.encrypt(byteArrayOf(1, 2, 3))))
        assertArrayEquals(byteArrayOf(4, 5), server.decrypt(car.encrypt(byteArrayOf(4, 5))))
    }

    @Test
    fun aCarFromAnotherRootIsRefused() {
        val server = phoneTlsServer()
        val car = TestCar("other-car")
        val refusal = assertThrows<SSLHandshakeException> { car.handshakeWith(server) }
        assertInstanceOf(CertificateException::class.java, refusal.cause)
        assertFalse(server.isEstablished)
        assertThrows<SSLException> { server.handshake(ByteArray(0)) }
    }
}
