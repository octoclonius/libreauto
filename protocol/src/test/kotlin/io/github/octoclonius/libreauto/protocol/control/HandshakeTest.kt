package io.github.octoclonius.libreauto.protocol.control

import io.github.octoclonius.libreauto.protocol.FrameReader
import io.github.octoclonius.libreauto.protocol.FrameWriter
import io.github.octoclonius.libreauto.protocol.OutgoingMessage
import io.github.octoclonius.libreauto.protocol.TestCar
import io.github.octoclonius.libreauto.protocol.TlsServer
import io.github.octoclonius.libreauto.protocol.phoneTlsServer
import java.io.InputStream
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.net.ProtocolException
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows

@Timeout(10, unit = TimeUnit.SECONDS)
class HandshakeTest {

    @Test
    fun aCarFromTheRootGetsThroughVersionTlsAndAuthComplete() {
        val tls = phoneTlsServer()
        val car = CarSide(TestCar(), authStatus = 0)
        val version = car.connect { input, output -> handshake(input, output, tls).run() }
        assertEquals(CAR_VERSION, version)
        assertArrayEquals(
            byteArrayOf(0, 1, 0, 7, 0, 0),
            car.versionResponse,
        )
        assertTrue(tls.isEstablished)
        assertArrayEquals(byteArrayOf(1, 2, 3), car.tls.decrypt(tls.encrypt(byteArrayOf(1, 2, 3))))
    }

    @Test
    fun aFailedAuthCompleteIsRefused() {
        val tls = phoneTlsServer()
        val car = CarSide(TestCar(), authStatus = 1)
        val refusal =
            assertThrows<ProtocolException> {
                car.connect { input, output -> handshake(input, output, tls).run() }
            }
        assertEquals("Auth complete with status 1", refusal.message)
        assertTrue(tls.isEstablished)
    }

    private fun handshake(input: InputStream, output: OutputStream, tls: TlsServer) =
        Handshake(FrameReader(input, tls::decrypt, clock = { 0 }), FrameWriter(output), tls)

    private class CarSide(val tls: TestCar, private val authStatus: Int) {
        var versionResponse: ByteArray? = null
            private set

        fun <T> connect(phone: (InputStream, OutputStream) -> T): T {
            val carOutput = PipedOutputStream()
            val phoneInput = PipedInputStream(carOutput, PIPE_SIZE)
            val phoneOutput = PipedOutputStream()
            val carInput = PipedInputStream(phoneOutput, PIPE_SIZE)
            var failure: Throwable? = null
            val car = thread {
                try {
                    run(
                        FrameReader(carInput, decrypt = { null }, clock = { 0 }),
                        FrameWriter(carOutput),
                    )
                } catch (e: Throwable) {
                    failure = e
                }
            }
            val result =
                try {
                    phone(phoneInput, phoneOutput)
                } finally {
                    phoneOutput.close()
                    car.join()
                }
            failure?.let { throw it }
            return result
        }

        private fun run(reader: FrameReader, writer: FrameWriter) {
            writer.send(VERSION_REQUEST, byteArrayOf(0, 1, 0, 7))
            versionResponse = reader.receive(VERSION_RESPONSE)
            writer.send(TLS_HANDSHAKE, tls.step(ByteArray(0)))
            while (!tls.isEstablished) {
                val toPhone = tls.step(reader.receive(TLS_HANDSHAKE))
                if (toPhone.isNotEmpty()) writer.send(TLS_HANDSHAKE, toPhone)
            }
            writer.send(AUTH_COMPLETE, byteArrayOf(0x08, authStatus.toByte()))
        }

        private fun FrameReader.receive(type: Int): ByteArray {
            val message = readMessage()
            assertEquals(0, message.channel)
            assertEquals(type, message.type)
            assertFalse(message.isEncrypted)
            return message.body
        }

        private fun FrameWriter.send(type: Int, body: ByteArray) =
            write(OutgoingMessage(0, isControl = false, type, body))
    }

    private companion object {
        val CAR_VERSION = ProtocolVersion(1, 7)
        const val PIPE_SIZE = 64 * 1024
        const val VERSION_REQUEST = 0x0001
        const val VERSION_RESPONSE = 0x0002
        const val TLS_HANDSHAKE = 0x0003
        const val AUTH_COMPLETE = 0x0004
    }
}
