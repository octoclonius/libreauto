package io.github.octoclonius.libreauto.projection.certificate

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import io.github.octoclonius.libreauto.projection.datastore.jsonDataStore
import io.github.octoclonius.libreauto.projection.describe
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.nullable

class CertificateRepository(
    private val context: Context,
    private val externalScope: CoroutineScope,
) {

    companion object {
        const val ACTION = "io.github.octoclonius.libreauto.action.CERTIFICATE_PROVIDER"
        const val METHOD_GET_CERTIFICATE = "getCertificate"

        const val KEY_CERT = "cert"
        const val KEY_KEY = "key"
        const val KEY_ERROR = "error"

        private const val CALL_TIMEOUT_MS = 5_000L

        private const val NAME = "certificate_provider"
    }

    @Serializable
    class Choice(val packageName: String, val signer: ByteArray) {
        override fun equals(other: Any?): Boolean =
            other is Choice &&
                packageName == other.packageName &&
                signer.contentEquals(other.signer)

        override fun hashCode(): Int = 31 * packageName.hashCode() + signer.contentHashCode()
    }

    class Option(val name: String, val choice: Choice, val selected: Boolean)

    sealed interface FetchResult {
        class Success(val certificate: X509Certificate, val key: PrivateKey) : FetchResult

        class Error(val message: String) : FetchResult
    }

    private class UnavailableException(override val message: String) : Exception(message)

    private val packageManager = context.packageManager
    private val dataStore = jsonDataStore(context, NAME, Choice.serializer().nullable, null)

    val selection: Flow<Choice?> = dataStore.data

    suspend fun options(): List<Option> {
        val chosen = selection.first()
        return withContext(Dispatchers.IO) { queryOptions(chosen) }
    }

    private fun queryOptions(chosen: Choice?): List<Option> =
        packageManager
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

    suspend fun select(choice: Choice?) {
        externalScope.launch { dataStore.updateData { choice } }.join()
    }

    suspend fun fetch(choice: Choice): FetchResult =
        withContext(Dispatchers.IO) {
            try {
                request(choice)
            } catch (e: UnavailableException) {
                FetchResult.Error(e.message)
            }
        }

    private fun request(choice: Choice): FetchResult {
        val packageName = choice.packageName
        val authority = authority(packageName) ?: unavailable("Doesn't provide a certificate")
        if (!isSignedWith(packageName, choice.signer)) unavailable("Signed with another key")
        val reply = call(authority)
        val (error, certBytes, keyBytes) =
            try {
                Triple(
                    reply.getString(KEY_ERROR),
                    reply.getByteArray(KEY_CERT),
                    reply.getByteArray(KEY_KEY),
                )
            } catch (e: RuntimeException) {
                unavailable(describe(e))
            }
        return checkAnswer(error, certBytes, keyBytes)
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

    private fun unavailable(message: String): Nothing = throw UnavailableException(message)
}
