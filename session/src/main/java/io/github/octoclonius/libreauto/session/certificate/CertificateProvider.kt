package io.github.octoclonius.libreauto.session.certificate

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import java.io.ByteArrayInputStream
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class CertificateProvider(private val context: Context) {

    companion object {
        const val ACTION = "io.github.octoclonius.libreauto.action.CERTIFICATE_PROVIDER"
        const val METHOD_GET_CERTIFICATE = "getCertificate"

        const val KEY_CERT = "cert"
        const val KEY_KEY = "key"
        const val KEY_ERROR = "error"

        private const val MAX_BYTES = 16 * 1024
        private const val MAX_ERROR_CHARS = 256
        private const val CALL_TIMEOUT_MS = 5_000L

        private const val PREFS = "certificate_provider"
        private const val PREF_PACKAGE = "package"
        private const val PREF_SIGNER = "signer_sha256"
    }

    class Choice(val packageName: String, val signer: ByteArray) {
        override fun equals(other: Any?): Boolean =
            other is Choice && packageName == other.packageName && signer.contentEquals(other.signer)

        override fun hashCode(): Int = 31 * packageName.hashCode() + signer.contentHashCode()
    }

    class Option(val name: String, val choice: Choice, val selected: Boolean)

    sealed interface FetchResult {
        class Success(val certificate: X509Certificate, val key: PrivateKey) : FetchResult

        class Error(val message: String) : FetchResult
    }

    private class UnavailableException(override val message: String) : Exception(message)

    private val packageManager = context.packageManager
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun selection(): Choice? {
        val packageName = prefs.getString(PREF_PACKAGE, null) ?: return null
        val signer =
            try {
                prefs.getString(PREF_SIGNER, null)?.let { Base64.getDecoder().decode(it) }
            } catch (_: IllegalArgumentException) {
                null
            }
        if (signer != null) return Choice(packageName, signer)
        return Choice(packageName, signer(packageName) ?: return null).also { select(it) }
    }

    fun options(): List<Option> {
        val chosen = selection()
        return packageManager
            .queryIntentContentProviders(Intent(ACTION), 0)
            .map { it.providerInfo.applicationInfo }
            .distinctBy { it.packageName }
            .mapNotNull { info ->
                signer(info.packageName)?.let { signer ->
                    Option(
                        packageManager.getApplicationLabel(info).toString(),
                        Choice(info.packageName, signer),
                        selected =
                            chosen?.packageName == info.packageName &&
                                isSignedWith(info.packageName, chosen.signer),
                    )
                }
            }
            .sortedBy { it.name }
    }

    fun select(choice: Choice?) {
        val edit = prefs.edit()
        if (choice == null) {
            edit.remove(PREF_PACKAGE).remove(PREF_SIGNER)
        } else {
            edit
                .putString(PREF_PACKAGE, choice.packageName)
                .putString(PREF_SIGNER, Base64.getEncoder().encodeToString(choice.signer))
        }
        edit.apply()
    }

    fun fetch(choice: Choice): FetchResult =
        try {
            request(choice)
        } catch (e: UnavailableException) {
            FetchResult.Error(e.message)
        }

    private fun request(choice: Choice): FetchResult.Success {
        val packageName = choice.packageName
        val authority = authority(packageName) ?: unavailable("Doesn't provide a certificate")
        if (!isSignedWith(packageName, choice.signer)) unavailable("Signed with another key")
        val reply = call(authority)
        val (certBytes, keyBytes) =
            try {
                reply.getString(KEY_ERROR)?.let { unavailable(it.take(MAX_ERROR_CHARS)) }
                reply.getByteArray(KEY_CERT) to reply.getByteArray(KEY_KEY)
            } catch (e: RuntimeException) {
                unavailable(describe(e))
            }
        if (certBytes == null || keyBytes == null) unavailable("No certificate in its answer")
        if (certBytes.size > MAX_BYTES || keyBytes.size > MAX_BYTES) unavailable("Answer too large")
        val certificate =
            try {
                CertificateFactory.getInstance("X.509")
                    .generateCertificate(ByteArrayInputStream(certBytes)) as X509Certificate
            } catch (e: Exception) {
                unavailable(describe(e))
            }
        val key =
            try {
                KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(keyBytes))
                    as RSAPrivateKey
            } catch (e: Exception) {
                unavailable(describe(e))
            }
        if ((certificate.publicKey as? RSAPublicKey)?.modulus != key.modulus) {
            unavailable("Key doesn't match the certificate")
        }
        return FetchResult.Success(certificate, key)
    }

    private fun call(authority: String): Bundle {
        val client =
            try {
                context.contentResolver.acquireUnstableContentProviderClient(authority)
            } catch (e: SecurityException) {
                unavailable(describe(e))
            } ?: unavailable("Can't be reached")
        try {
            val call = FutureTask { client.call(METHOD_GET_CERTIFICATE, null, null) }
            Thread(call, "certificate-provider").apply { isDaemon = true }.start()
            return try {
                call.get(CALL_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            } catch (_: TimeoutException) {
                unavailable("Timed out")
            } catch (e: ExecutionException) {
                unavailable(describe(e.cause ?: e))
            } ?: unavailable("Answered nothing")
        } finally {
            client.close()
        }
    }

    private fun authority(packageName: String): String? =
        packageManager
            .queryIntentContentProviders(Intent(ACTION).setPackage(packageName), 0)
            .firstOrNull()
            ?.providerInfo
            ?.authority
            ?.substringBefore(';')

    private fun signer(packageName: String): ByteArray? {
        val signing =
            try {
                packageManager
                    .getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                    .signingInfo
            } catch (_: PackageManager.NameNotFoundException) {
                null
            } ?: return null
        if (signing.hasMultipleSigners()) return null
        return signing.signingCertificateHistory.lastOrNull()?.let {
            MessageDigest.getInstance("SHA-256").digest(it.toByteArray())
        }
    }

    private fun isSignedWith(packageName: String, signer: ByteArray): Boolean =
        packageManager.hasSigningCertificate(packageName, signer, PackageManager.CERT_INPUT_SHA256)

    private fun describe(e: Throwable): String =
        listOfNotNull(e.javaClass.simpleName, e.message).joinToString(": ").take(MAX_ERROR_CHARS)

    private fun unavailable(message: String): Nothing = throw UnavailableException(message)
}
