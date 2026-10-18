package io.github.octoclonius.libreauto.session

import android.content.Context
import android.os.Build
import android.util.Log
import io.github.octoclonius.libreauto.protocol.CONTROL_CHANNEL
import io.github.octoclonius.libreauto.protocol.ChannelMessage
import io.github.octoclonius.libreauto.protocol.FrameReader
import io.github.octoclonius.libreauto.protocol.FrameWriter
import io.github.octoclonius.libreauto.protocol.TlsServer
import io.github.octoclonius.libreauto.protocol.control.Handshake
import io.github.octoclonius.libreauto.protocol.control.MessageType
import io.github.octoclonius.libreauto.protocol.control.byeByeResponse
import io.github.octoclonius.libreauto.protocol.control.carName
import io.github.octoclonius.libreauto.protocol.control.parseByeByeRequest
import io.github.octoclonius.libreauto.protocol.control.parsePingRequest
import io.github.octoclonius.libreauto.protocol.control.parseServiceDiscoveryResponse
import io.github.octoclonius.libreauto.protocol.control.pingResponse
import io.github.octoclonius.libreauto.protocol.control.serviceDiscoveryRequest
import io.github.octoclonius.libreauto.protocol.googleAutomotiveLinkRoot
import io.github.octoclonius.libreauto.protocol.input.inputSource
import io.github.octoclonius.libreauto.protocol.media.videoSink
import io.github.octoclonius.libreauto.session.certificate.CertificateProvider
import io.github.octoclonius.libreauto.session.certificate.CertificateProvider.FetchResult
import io.github.octoclonius.libreauto.session.input.InputChannel
import io.github.octoclonius.libreauto.session.video.VideoChannel
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

    private val context = context.applicationContext
    private val certificateProvider = CertificateProvider(this.context)

    fun run(input: InputStream, output: OutputStream) {
        _state.value = CarConnectionState.Connecting
        try {
            val credentials = credentials() ?: return
            val tls = TlsServer(credentials.certificate, credentials.key, googleAutomotiveLinkRoot())
            val reader = FrameReader(input, tls::decrypt)
            val writer = FrameWriter(output)
            val version = Handshake(reader, writer, tls).run()
            Log.i(TAG, "Handshake done with a car on protocol $version")
            val control = ChannelSender(writer, tls, CONTROL_CHANNEL)
            control.send(
                MessageType.SERVICE_DISCOVERY_REQUEST,
                serviceDiscoveryRequest("${Build.MANUFACTURER} ${Build.MODEL}"),
            )
            serve(reader, writer, tls, control)
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

    private fun serve(
        reader: FrameReader,
        writer: FrameWriter,
        tls: TlsServer,
        control: ChannelSender,
    ) {
        var video: VideoChannel? = null
        var input: InputChannel? = null
        try {
            while (true) {
                val message = reader.readMessage()
                if (!message.isEncrypted) {
                    throw ProtocolException(
                        "Plaintext message 0x%04x on channel %d after the handshake"
                            .format(message.type, message.channel)
                    )
                }
                if (video != null && message.channel == video.channel) {
                    if (!video.onMessage(message)) logUnhandled(message)
                    continue
                }
                if (input != null && message.channel == input.channel) {
                    if (!input.onMessage(message)) logUnhandled(message)
                    continue
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
                        val sink = videoSink(response)
                        if (sink == null) {
                            Log.w(TAG, "The car has no video channel for its main display")
                        } else {
                            val videoChannel =
                                VideoChannel(
                                        context,
                                        ChannelSender(writer, tls, sink.channel),
                                        sink.configs,
                                    )
                                    .also { it.open() }
                            video = videoChannel
                            val source = inputSource(response, sink.displayId)
                            if (source == null) {
                                Log.w(
                                    TAG,
                                    "The car has no input channel for display ${sink.displayId}",
                                )
                            } else {
                                input =
                                    InputChannel(
                                            ChannelSender(writer, tls, source.channel),
                                            source.keycodes,
                                            videoChannel::format,
                                        )
                                        .also { it.open() }
                            }
                        }
                    }
                    MessageType.PING_REQUEST ->
                        control.send(
                            MessageType.PING_RESPONSE,
                            pingResponse(parsePingRequest(message.body)),
                        )
                    MessageType.BYE_BYE_REQUEST -> {
                        val reason = parseByeByeRequest(message.body)
                        Log.i(TAG, "The car says bye-bye with reason $reason")
                        control.send(MessageType.BYE_BYE_RESPONSE, byeByeResponse())
                        return
                    }
                    else -> logUnhandled(message)
                }
            }
        } finally {
            video?.close()
        }
    }

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
