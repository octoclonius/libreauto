package io.github.octoclonius.libreauto.session.certificate

import io.github.octoclonius.libreauto.session.certificate.CertificateProvider.FetchResult
import io.github.octoclonius.libreauto.session.describe
import java.io.ByteArrayInputStream
import java.security.KeyFactory
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec

internal const val MAX_BYTES = 16 * 1024
internal const val MAX_ERROR_CHARS = 256

internal fun checkAnswer(error: String?, certBytes: ByteArray?, keyBytes: ByteArray?): FetchResult {
    if (error != null) return FetchResult.Error(error.take(MAX_ERROR_CHARS))
    if (certBytes == null || keyBytes == null) {
        return FetchResult.Error("No certificate in its answer")
    }
    if (certBytes.size > MAX_BYTES || keyBytes.size > MAX_BYTES) {
        return FetchResult.Error("Answer too large")
    }
    val certificate =
        try {
            CertificateFactory.getInstance("X.509")
                .generateCertificate(ByteArrayInputStream(certBytes)) as X509Certificate
        } catch (e: Exception) {
            return FetchResult.Error(describe(e))
        }
    val key =
        try {
            KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(keyBytes))
                as RSAPrivateKey
        } catch (e: Exception) {
            return FetchResult.Error(describe(e))
        }
    if ((certificate.publicKey as? RSAPublicKey)?.modulus != key.modulus) {
        return FetchResult.Error("Key doesn't match the certificate")
    }
    return FetchResult.Success(certificate, key)
}
