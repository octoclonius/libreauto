package io.github.octoclonius.libreauto.session

import android.content.Context
import android.util.Log
import io.github.octoclonius.libreauto.protocol.FrameReader
import io.github.octoclonius.libreauto.protocol.FrameWriter
import io.github.octoclonius.libreauto.protocol.Handshake
import io.github.octoclonius.libreauto.protocol.TlsServer
import io.github.octoclonius.libreauto.protocol.googleAutomotiveLinkRoot
import io.github.octoclonius.libreauto.session.certificate.CertificateProvider
import io.github.octoclonius.libreauto.session.certificate.CertificateProvider.FetchResult
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

class CarConnection(context: Context) {

    companion object {
        private const val TAG = "CarConnection"
    }

    private val certificateProvider = CertificateProvider(context.applicationContext)

    fun run(input: InputStream, output: OutputStream) {
        try {
            val credentials = credentials() ?: return
            val tls = TlsServer(credentials.certificate, credentials.key, googleAutomotiveLinkRoot())
            val version = Handshake(FrameReader(input, tls::decrypt), FrameWriter(output), tls).run()
            Log.i(TAG, "Handshake done with a car on protocol $version")
        } catch (e: IOException) {
            Log.w(TAG, "Handshake failed", e)
        } finally {
            input.closeQuietly()
            output.closeQuietly()
        }
    }

    private fun credentials(): FetchResult.Success? {
        val choice = certificateProvider.selection()
        if (choice == null) {
            Log.w(TAG, "No certificate provider selected")
            return null
        }
        return when (val result = certificateProvider.fetch(choice)) {
            is FetchResult.Success -> result
            is FetchResult.Error -> {
                Log.w(TAG, "No certificate from ${choice.packageName}: ${result.message}")
                null
            }
        }
    }
}

private fun Closeable.closeQuietly() {
    try {
        close()
    } catch (_: IOException) {}
}
