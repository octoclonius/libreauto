package io.github.octoclonius.libreauto.session

import android.content.Context
import android.os.Build
import android.util.Log
import io.github.octoclonius.libreauto.protocol.CONTROL_CHANNEL
import io.github.octoclonius.libreauto.protocol.ChannelMessage
import io.github.octoclonius.libreauto.protocol.FrameReader
import io.github.octoclonius.libreauto.protocol.FrameWriter
import io.github.octoclonius.libreauto.protocol.Handshake
import io.github.octoclonius.libreauto.protocol.MessageType
import io.github.octoclonius.libreauto.protocol.TlsServer
import io.github.octoclonius.libreauto.protocol.byeByeResponse
import io.github.octoclonius.libreauto.protocol.carName
import io.github.octoclonius.libreauto.protocol.googleAutomotiveLinkRoot
import io.github.octoclonius.libreauto.protocol.parseByeByeRequest
import io.github.octoclonius.libreauto.protocol.parsePingRequest
import io.github.octoclonius.libreauto.protocol.parseServiceDiscoveryResponse
import io.github.octoclonius.libreauto.protocol.pingResponse
import io.github.octoclonius.libreauto.protocol.serviceDiscoveryRequest
import io.github.octoclonius.libreauto.session.certificate.CertificateProvider
import io.github.octoclonius.libreauto.session.certificate.CertificateProvider.FetchResult
import java.io.Closeable
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.ProtocolException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class CarConnection(context: Context) {

    companion object {
        private const val TAG = "CarConnection"

        private val _state = MutableStateFlow<CarConnectionState>(CarConnectionState.Disconnected)
        val state: StateFlow<CarConnectionState> = _state.asStateFlow()
    }

    private val certificateProvider = CertificateProvider(context.applicationContext)

    fun run(input: InputStream, output: OutputStream) {
        _state.value = CarConnectionState.Connecting
        try {
            val credentials = credentials() ?: return
            val tls = TlsServer(credentials.certificate, credentials.key, googleAutomotiveLinkRoot())
            val reader = FrameReader(input, tls::decrypt)
            val writer = FrameWriter(output)
            val version = Handshake(reader, writer, tls).run()
            Log.i(TAG, "Handshake done with a car on protocol $version")
            send(
                writer,
                tls,
                MessageType.SERVICE_DISCOVERY_REQUEST,
                serviceDiscoveryRequest("${Build.MANUFACTURER} ${Build.MODEL}"),
            )
            serve(reader, writer, tls)
        } catch (_: EOFException) {
            Log.i(TAG, "The car closed the connection")
        } catch (e: IOException) {
            Log.w(TAG, "Connection to the car failed", e)
        } finally {
            _state.value =
                if (_state.value is CarConnectionState.Connected) {
                    CarConnectionState.Disconnected
                } else {
                    CarConnectionState.Failed
                }
            input.closeQuietly()
            output.closeQuietly()
        }
    }

    private fun serve(reader: FrameReader, writer: FrameWriter, tls: TlsServer) {
        while (true) {
            val message = reader.readMessage()
            if (!message.isEncrypted) {
                throw ProtocolException(
                    "Plaintext message 0x%04x on channel %d after the handshake"
                        .format(message.type, message.channel)
                )
            }
            if (message.channel != CONTROL_CHANNEL) {
                logUnhandled(message)
                continue
            }
            when (message.type) {
                MessageType.SERVICE_DISCOVERY_RESPONSE -> {
                    val response = parseServiceDiscoveryResponse(message.body)
                    response.services.forEach { Log.i(TAG, "Service on channel ${it.id}: $it") }
                    _state.value = CarConnectionState.Connected(carName(response))
                }
                MessageType.PING_REQUEST ->
                    send(
                        writer,
                        tls,
                        MessageType.PING_RESPONSE,
                        pingResponse(parsePingRequest(message.body)),
                    )
                MessageType.BYE_BYE_REQUEST -> {
                    val reason = parseByeByeRequest(message.body)
                    Log.i(TAG, "The car says bye-bye with reason $reason")
                    send(writer, tls, MessageType.BYE_BYE_RESPONSE, byeByeResponse())
                    return
                }
                else -> logUnhandled(message)
            }
        }
    }

    private fun send(writer: FrameWriter, tls: TlsServer, type: Int, body: ByteArray) =
        writer.write(CONTROL_CHANNEL, isControl = false, type, body, tls::encrypt)

    private fun logUnhandled(message: ChannelMessage) {
        Log.i(TAG, "Message 0x%04x on channel %d".format(message.type, message.channel))
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
