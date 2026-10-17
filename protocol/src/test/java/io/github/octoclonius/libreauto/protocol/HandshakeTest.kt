package io.github.octoclonius.libreauto.protocol

import java.io.InputStream
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.net.ProtocolException
import java.nio.ByteBuffer
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
        val car = CarSide(TestCar("car"), MessageStatus.SUCCESS)
        val version = car.connect { input, output -> handshake(input, output, tls).run() }
        assertEquals(CAR_VERSION, version)
        assertArrayEquals(
            VersionResponse(ProtocolVersion(1, 7), MessageStatus.SUCCESS).toBody(),
            car.versionResponse,
        )
        assertTrue(tls.isEstablished)
        assertArrayEquals(byteArrayOf(1, 2, 3), car.tls.decrypt(tls.encrypt(byteArrayOf(1, 2, 3))))
    }

    @Test
    fun aFailedAuthCompleteIsRefused() {
        val tls = phoneTlsServer()
        val car = CarSide(TestCar("car"), authStatus = 1)
        val refusal =
            assertThrows<ProtocolException> {
                car.connect { input, output -> handshake(input, output, tls).run() }
            }
        assertEquals("Auth complete with status 1", refusal.message)
        assertTrue(tls.isEstablished)
    }

    private fun handshake(input: InputStream, output: OutputStream, tls: TlsServer) =
        Handshake(FrameReader(input, tls::decrypt), FrameWriter(output), tls)

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
                    run(FrameReader(carInput) { null }, FrameWriter(carOutput))
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
            val request =
                ByteBuffer.allocate(VERSION_REQUEST_SIZE)
                    .putShort(CAR_VERSION.major.toShort())
                    .putShort(CAR_VERSION.minor.toShort())
                    .array()
            writer.send(MessageType.VERSION_REQUEST, request)
            versionResponse = reader.receive(MessageType.VERSION_RESPONSE)
            writer.send(MessageType.TLS_HANDSHAKE, tls.step(ByteArray(0)))
            while (!tls.isEstablished) {
                val toPhone = tls.step(reader.receive(MessageType.TLS_HANDSHAKE))
                if (toPhone.isNotEmpty()) writer.send(MessageType.TLS_HANDSHAKE, toPhone)
            }
            writer.send(MessageType.AUTH_COMPLETE, AuthComplete(status = authStatus).encode())
        }

        private fun FrameReader.receive(type: Int): ByteArray {
            val message = readMessage()
            assertEquals(CONTROL_CHANNEL, message.channel)
            assertEquals(type, message.type)
            assertFalse(message.isEncrypted)
            return message.body
        }

        private fun FrameWriter.send(type: Int, body: ByteArray) =
            write(CONTROL_CHANNEL, isControl = false, type, body)
    }

    private companion object {
        val CAR_VERSION = ProtocolVersion(1, 7)
        const val PIPE_SIZE = 64 * 1024
    }
}
