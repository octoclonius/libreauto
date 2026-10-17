package io.github.octoclonius.libreauto.protocol

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.security.KeyStore
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLEngineResult.HandshakeStatus
import javax.net.ssl.SSLEngineResult.Status
import javax.net.ssl.SSLException
import javax.net.ssl.TrustManagerFactory

private const val TLS_VERSION = "TLSv1.2"

class TlsServer(certificate: X509Certificate, key: PrivateKey, root: X509Certificate) {
    private val engine = serverEngine(certificate, key, root)
    private val packetSize = engine.session.packetBufferSize
    private val applicationSize = engine.session.applicationBufferSize
    private var pending = ByteArray(0)

    @Volatile
    var isEstablished = false
        private set

    @Throws(SSLException::class)
    fun handshake(fromCar: ByteArray): ByteArray {
        if (isEstablished) throw SSLException("Handshake data after the handshake")
        if (engine.isInboundDone) throw SSLException("TLS is closed")
        val input = ByteBuffer.wrap(pending + fromCar)
        pending = ByteArray(0)
        val toCar = ByteArrayOutputStream()
        while (!isEstablished) {
            when (engine.handshakeStatus) {
                HandshakeStatus.NEED_UNWRAP,
                HandshakeStatus.NEED_UNWRAP_AGAIN -> if (!unwrap(input)) break
                HandshakeStatus.NEED_WRAP -> toCar.write(wrap())
                HandshakeStatus.NEED_TASK ->
                    generateSequence { engine.delegatedTask }.forEach { it.run() }
                HandshakeStatus.FINISHED,
                HandshakeStatus.NOT_HANDSHAKING -> isEstablished = true
            }
        }
        if (input.hasRemaining()) throw SSLException("Data after the handshake")
        return toCar.toByteArray()
    }

    @Synchronized
    @Throws(SSLException::class)
    fun encrypt(plaintext: ByteArray): ByteArray {
        check(isEstablished) { "Encrypt before the handshake" }
        require(plaintext.size in 1..MAX_RECORD_PLAINTEXT_SIZE) {
            "${plaintext.size} bytes don't make one record"
        }
        val out = ByteBuffer.allocate(packetSize)
        val result = engine.wrap(ByteBuffer.wrap(plaintext), out)
        if (result.status != Status.OK || result.bytesConsumed() != plaintext.size) {
            throw SSLException(
                "Wrap ${result.status} took ${result.bytesConsumed()} of ${plaintext.size} bytes"
            )
        }
        return out.array().copyOf(out.position())
    }

    @Throws(SSLException::class)
    fun decrypt(record: ByteArray): ByteArray? {
        if (!isEstablished) return null
        val out = ByteBuffer.allocate(applicationSize)
        val result = engine.unwrap(ByteBuffer.wrap(record), out)
        val isOneRecord = result.status == Status.OK && result.bytesConsumed() == record.size
        if (!isOneRecord || result.handshakeStatus != HandshakeStatus.NOT_HANDSHAKING) return null
        return out.array().copyOf(out.position())
    }

    private fun unwrap(input: ByteBuffer): Boolean {
        if (!input.hasRemaining()) return false
        val result = engine.unwrap(input, ByteBuffer.allocate(applicationSize))
        return when (result.status) {
            Status.OK -> true
            Status.BUFFER_UNDERFLOW -> {
                pending = ByteArray(input.remaining()).also(input::get)
                false
            }
            else -> throw SSLException("Handshake unwrap ${result.status}")
        }
    }

    private fun wrap(): ByteArray {
        val out = ByteBuffer.allocate(packetSize)
        val result = engine.wrap(ByteBuffer.allocate(0), out)
        if (result.status != Status.OK) throw SSLException("Handshake wrap ${result.status}")
        return out.array().copyOf(out.position())
    }
}

private fun serverEngine(
    certificate: X509Certificate,
    key: PrivateKey,
    root: X509Certificate,
): SSLEngine {
    val keys =
        KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null, null)
            setKeyEntry("phone", key, CharArray(0), arrayOf(certificate))
        }
    val keyManagers =
        KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
            .apply { init(keys, CharArray(0)) }
            .keyManagers
    val roots =
        KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null, null)
            setCertificateEntry("root", root)
        }
    val trustManagers =
        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            .apply { init(roots) }
            .trustManagers
    return SSLContext.getInstance(TLS_VERSION)
        .apply { init(keyManagers, trustManagers, SecureRandom()) }
        .createSSLEngine()
        .apply {
            useClientMode = false
            needClientAuth = true
            enabledProtocols = arrayOf(TLS_VERSION)
            beginHandshake()
        }
}
