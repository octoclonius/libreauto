package io.github.octoclonius.libreauto.protocol

import java.io.ByteArrayOutputStream
import java.net.Socket
import java.nio.ByteBuffer
import java.security.KeyFactory
import java.security.KeyStore
import java.security.Principal
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLEngineResult.HandshakeStatus
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509ExtendedKeyManager

class TestCar(name: String) {
    private val engine: SSLEngine =
        SSLContext.getInstance("TLS")
            .apply {
                init(
                    arrayOf(AlwaysPresenting(certificate(name), key(name))),
                    trusting(certificate("ca")),
                    SecureRandom(),
                )
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

    private class AlwaysPresenting(
        private val certificate: X509Certificate,
        private val key: PrivateKey,
    ) : X509ExtendedKeyManager() {
        override fun chooseEngineClientAlias(
            keyType: Array<out String>?,
            issuers: Array<out Principal>?,
            engine: SSLEngine?,
        ) = ALIAS

        override fun chooseClientAlias(
            keyType: Array<out String>?,
            issuers: Array<out Principal>?,
            socket: Socket?,
        ) = ALIAS

        override fun getClientAliases(keyType: String?, issuers: Array<out Principal>?) =
            arrayOf(ALIAS)

        override fun chooseServerAlias(
            keyType: String?,
            issuers: Array<out Principal>?,
            socket: Socket?,
        ): String? = null

        override fun getServerAliases(
            keyType: String?,
            issuers: Array<out Principal>?,
        ): Array<String>? = null

        override fun getCertificateChain(alias: String?) = arrayOf(certificate)

        override fun getPrivateKey(alias: String?) = key

        private companion object {
            const val ALIAS = "car"
        }
    }
}

fun phoneTlsServer() = TlsServer(certificate("phone"), key("phone"), certificate("ca"))

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
