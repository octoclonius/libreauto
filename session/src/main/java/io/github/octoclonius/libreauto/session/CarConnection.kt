package io.github.octoclonius.libreauto.session

import android.content.Context
import android.os.Build
import android.util.Log
import io.github.octoclonius.libreauto.protocol.CONTROL_CHANNEL
import io.github.octoclonius.libreauto.protocol.FrameReader
import io.github.octoclonius.libreauto.protocol.FrameWriter
import io.github.octoclonius.libreauto.protocol.Handshake
import io.github.octoclonius.libreauto.protocol.MessageType
import io.github.octoclonius.libreauto.protocol.ServiceDiscoveryResponse
import io.github.octoclonius.libreauto.protocol.TlsServer
import io.github.octoclonius.libreauto.protocol.googleAutomotiveLinkRoot
import io.github.octoclonius.libreauto.protocol.parseServiceDiscoveryResponse
import io.github.octoclonius.libreauto.protocol.serviceDiscoveryRequest
import io.github.octoclonius.libreauto.session.certificate.CertificateProvider
import io.github.octoclonius.libreauto.session.certificate.CertificateProvider.FetchResult
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.ProtocolException

class CarConnection(context: Context) {

    companion object {
        private const val TAG = "CarConnection"
    }

    private val certificateProvider = CertificateProvider(context.applicationContext)

    fun run(input: InputStream, output: OutputStream) {
        try {
            val credentials = credentials() ?: return
            val tls = TlsServer(credentials.certificate, credentials.key, googleAutomotiveLinkRoot())
            val reader = FrameReader(input, tls::decrypt)
            val writer = FrameWriter(output)
            val version = Handshake(reader, writer, tls).run()
            Log.i(TAG, "Handshake done with a car on protocol $version")
            val discovery = discoverServices(reader, writer, tls)
            discovery.services.forEach { Log.i(TAG, "Service on channel ${it.id}: $it") }
        } catch (e: IOException) {
            Log.w(TAG, "Connection to the car failed", e)
        } finally {
            input.closeQuietly()
            output.closeQuietly()
        }
    }

    private fun discoverServices(
        reader: FrameReader,
        writer: FrameWriter,
        tls: TlsServer,
    ): ServiceDiscoveryResponse {
        writer.write(
            CONTROL_CHANNEL,
            isControl = false,
            MessageType.SERVICE_DISCOVERY_REQUEST,
            serviceDiscoveryRequest("${Build.MANUFACTURER} ${Build.MODEL}"),
            tls::encrypt,
        )
        while (true) {
            val message = reader.readMessage()
            if (!message.isEncrypted) {
                throw ProtocolException(
                    "Plaintext message 0x%04x on channel %d after the handshake"
                        .format(message.type, message.channel)
                )
            }
            if (
                message.channel == CONTROL_CHANNEL &&
                    message.type == MessageType.SERVICE_DISCOVERY_RESPONSE
            ) {
                return parseServiceDiscoveryResponse(message.body)
            }
            Log.i(
                TAG,
                "Message 0x%04x on channel %d before the service discovery response"
                    .format(message.type, message.channel),
            )
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
