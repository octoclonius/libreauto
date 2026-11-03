package io.github.octoclonius.libreauto.protocol

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TlsServerTest {

    @Test
    fun aCarWithoutACertificateGetsATls12SessionBothWays() {
        val server = phoneTlsServer()
        val car = TestCar()
        car.handshakeWith(server)
        assertTrue(server.isEstablished)
        assertTrue(car.isEstablished)
        assertEquals("TLSv1.2", car.protocol)
        assertArrayEquals(byteArrayOf(1, 2, 3), car.decrypt(server.encrypt(byteArrayOf(1, 2, 3))))
        assertArrayEquals(byteArrayOf(4, 5), server.decrypt(car.encrypt(byteArrayOf(4, 5))))
    }
}
