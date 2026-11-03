package io.github.octoclonius.libreauto.protocol

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.security.KeyFactory
import java.security.KeyStore
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLEngineResult.HandshakeStatus
import javax.net.ssl.TrustManagerFactory

class TestCar {
    private val engine: SSLEngine =
        SSLContext.getInstance("TLS")
            .apply {
                init(null, trusting(certificate("ca")), SecureRandom())
            }
            .createSSLEngine()
            .apply {
                useClientMode = true
                beginHandshake()
            }

    private val packetSize = engine.session.packetBufferSize
    private val applicationSize = engine.session.applicationBufferSize

    val isEstablished
        get() = engine.handshakeStatus == HandshakeStatus.NOT_HANDSHAKING

    val protocol: String
        get() = engine.session.protocol

    fun handshakeWith(server: TlsServer) {
        var toServer = step(ByteArray(0))
        while (toServer.isNotEmpty()) toServer = step(server.handshake(toServer))
    }

    fun encrypt(plaintext: ByteArray): ByteArray {
        val out = ByteBuffer.allocate(packetSize)
        engine.wrap(ByteBuffer.wrap(plaintext), out)
        return out.array().copyOf(out.position())
    }

    fun decrypt(record: ByteArray): ByteArray {
        val out = ByteBuffer.allocate(applicationSize)
        engine.unwrap(ByteBuffer.wrap(record), out)
        return out.array().copyOf(out.position())
    }

    fun step(fromServer: ByteArray): ByteArray {
        val input = ByteBuffer.wrap(fromServer)
        val toServer = ByteArrayOutputStream()
        while (true) {
            when (engine.handshakeStatus) {
                HandshakeStatus.NEED_WRAP -> toServer.write(encrypt(ByteArray(0)))
                HandshakeStatus.NEED_TASK -> engine.delegatedTask.run()
                HandshakeStatus.NEED_UNWRAP ->
                    if (input.hasRemaining()) {
                        engine.unwrap(input, ByteBuffer.allocate(applicationSize))
                    } else {
                        break
                    }
                else -> break
            }
        }
        return toServer.toByteArray()
    }
}

fun phoneTlsServer() = TlsServer(certificate("phone"), key("phone"))

private fun trusting(root: X509Certificate) =
    TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        .apply {
            init(
                KeyStore.getInstance(KeyStore.getDefaultType()).apply {
                    load(null, null)
                    setCertificateEntry("root", root)
                }
            )
        }
        .trustManagers

private fun certificate(name: String): X509Certificate =
    fixture("$name.crt").inputStream().use {
        CertificateFactory.getInstance("X.509").generateCertificate(it) as X509Certificate
    }

private fun key(name: String): PrivateKey =
    KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(fixture("$name.key")))

private fun fixture(name: String): ByteArray =
    checkNotNull(TestCar::class.java.getResourceAsStream(name)) { name }.use { it.readBytes() }
