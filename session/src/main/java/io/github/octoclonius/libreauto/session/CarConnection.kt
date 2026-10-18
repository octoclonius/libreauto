package io.github.octoclonius.libreauto.session

import android.content.Context
import android.os.Build
import android.util.Log
import io.github.octoclonius.libreauto.protocol.CONTROL_CHANNEL
import io.github.octoclonius.libreauto.protocol.ChannelMessage
import io.github.octoclonius.libreauto.protocol.FrameReader
import io.github.octoclonius.libreauto.protocol.FrameWriter
import io.github.octoclonius.libreauto.protocol.Handshake
import io.github.octoclonius.libreauto.protocol.MessageStatus
import io.github.octoclonius.libreauto.protocol.MessageType
import io.github.octoclonius.libreauto.protocol.TlsServer
import io.github.octoclonius.libreauto.protocol.VideoFocusNotification
import io.github.octoclonius.libreauto.protocol.byeByeResponse
import io.github.octoclonius.libreauto.protocol.carName
import io.github.octoclonius.libreauto.protocol.channelOpenRequest
import io.github.octoclonius.libreauto.protocol.googleAutomotiveLinkRoot
import io.github.octoclonius.libreauto.protocol.isProjected
import io.github.octoclonius.libreauto.protocol.parseByeByeRequest
import io.github.octoclonius.libreauto.protocol.parseChannelOpenResponse
import io.github.octoclonius.libreauto.protocol.parseMediaSetupResponse
import io.github.octoclonius.libreauto.protocol.parsePingRequest
import io.github.octoclonius.libreauto.protocol.parseServiceDiscoveryResponse
import io.github.octoclonius.libreauto.protocol.parseVideoFocusNotification
import io.github.octoclonius.libreauto.protocol.pingResponse
import io.github.octoclonius.libreauto.protocol.serviceDiscoveryRequest
import io.github.octoclonius.libreauto.protocol.videoChannel
import io.github.octoclonius.libreauto.protocol.videoSetupRequest
import io.github.octoclonius.libreauto.protocol.videoStart
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
        var video: VideoChannelState? = null
        while (true) {
            val message = reader.readMessage()
            if (!message.isEncrypted) {
                throw ProtocolException(
                    "Plaintext message 0x%04x on channel %d after the handshake"
                        .format(message.type, message.channel)
                )
            }
            val current = video
            if (
                current is VideoChannelState.Opening &&
                    message.channel == current.channel &&
                    message.type == MessageType.CHANNEL_OPEN_RESPONSE
            ) {
                val status = parseChannelOpenResponse(message.body)
                video =
                    if (status == MessageStatus.SUCCESS) {
                        Log.i(TAG, "The car opened the video channel ${current.channel}")
                        send(
                            writer,
                            tls,
                            MessageType.MEDIA_SETUP_REQUEST,
                            videoSetupRequest(),
                            current.channel,
                        )
                        VideoChannelState.SettingUp(current.channel, pendingFocus = null)
                    } else {
                        Log.w(
                            TAG,
                            "The car refused the video channel ${current.channel}: status $status",
                        )
                        null
                    }
                continue
            }
            if (
                current is VideoChannelState.SettingUp &&
                    message.channel == current.channel &&
                    message.type == MessageType.MEDIA_SETUP_RESPONSE
            ) {
                val response = parseMediaSetupResponse(message.body)
                Log.i(
                    TAG,
                    "The car set up the video channel ${current.channel}: " +
                        "status ${response.status}, " +
                        "up to ${response.max_unacked} unacked frames, " +
                        "configs ${response.config_indexes}",
                )
                val maxUnacked = response.max_unacked
                if (maxUnacked == null || maxUnacked <= 0) {
                    throw ProtocolException(
                        "The car allows $maxUnacked unacked frames on the video channel " +
                            "${current.channel}"
                    )
                }
                val configIndex =
                    response.config_indexes.firstOrNull()
                        ?: throw ProtocolException(
                            "The car accepted no video config on channel ${current.channel}"
                        )
                val ready = VideoChannelState.Ready(current.channel, configIndex)
                video =
                    if (current.pendingFocus?.isProjected == true) {
                        startVideo(writer, tls, ready)
                    } else {
                        ready
                    }
                continue
            }
            if (
                current != null &&
                    current !is VideoChannelState.Opening &&
                    message.channel == current.channel &&
                    message.type == MessageType.VIDEO_FOCUS_NOTIFICATION
            ) {
                val notification = parseVideoFocusNotification(message.body)
                Log.i(
                    TAG,
                    "Video focus on channel ${current.channel}: mode ${notification.mode}, " +
                        "unsolicited ${notification.unsolicited}",
                )
                video =
                    when (current) {
                        is VideoChannelState.SettingUp -> current.copy(pendingFocus = notification)
                        is VideoChannelState.Ready ->
                            if (notification.isProjected) {
                                startVideo(writer, tls, current)
                            } else {
                                current
                            }
                        else -> current
                    }
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
                    val channel = videoChannel(response)
                    if (channel == null) {
                        Log.w(TAG, "The car has no video channel for its main display")
                    } else {
                        Log.i(TAG, "Opening the video channel $channel")
                        send(
                            writer,
                            tls,
                            MessageType.CHANNEL_OPEN_REQUEST,
                            channelOpenRequest(channel),
                            channel,
                            isControl = true,
                        )
                        video = VideoChannelState.Opening(channel)
                    }
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

    private fun startVideo(
        writer: FrameWriter,
        tls: TlsServer,
        video: VideoChannelState.Ready,
    ): VideoChannelState.Started {
        send(writer, tls, MessageType.MEDIA_START, videoStart(video.configIndex), video.channel)
        Log.i(TAG, "Started video on channel ${video.channel} with config ${video.configIndex}")
        return VideoChannelState.Started(video.channel, video.configIndex)
    }

    private fun send(
        writer: FrameWriter,
        tls: TlsServer,
        type: Int,
        body: ByteArray,
        channel: Int = CONTROL_CHANNEL,
        isControl: Boolean = false,
    ) = writer.write(channel, isControl, type, body, tls::encrypt)

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

private sealed interface VideoChannelState {
    val channel: Int

    data class Opening(override val channel: Int) : VideoChannelState

    data class SettingUp(
        override val channel: Int,
        val pendingFocus: VideoFocusNotification?,
    ) : VideoChannelState

    data class Ready(override val channel: Int, val configIndex: Int) : VideoChannelState

    data class Started(override val channel: Int, val configIndex: Int) : VideoChannelState
}

private fun Closeable.closeQuietly() {
    try {
        close()
    } catch (_: IOException) {}
}
