package io.github.octoclonius.libreauto.session

import android.content.Context
import android.os.Build
import android.util.Log
import io.github.octoclonius.libreauto.protocol.CONTROL_CHANNEL
import io.github.octoclonius.libreauto.protocol.ChannelMessage
import io.github.octoclonius.libreauto.protocol.FrameReader
import io.github.octoclonius.libreauto.protocol.FrameWriter
import io.github.octoclonius.libreauto.protocol.Handshake
import io.github.octoclonius.libreauto.protocol.InputMessageType
import io.github.octoclonius.libreauto.protocol.MediaMessageType
import io.github.octoclonius.libreauto.protocol.MessageStatus
import io.github.octoclonius.libreauto.protocol.MessageType
import io.github.octoclonius.libreauto.protocol.TlsServer
import io.github.octoclonius.libreauto.protocol.VideoConfig
import io.github.octoclonius.libreauto.protocol.VideoFormat
import io.github.octoclonius.libreauto.protocol.VideoFocusNotification
import io.github.octoclonius.libreauto.protocol.byeByeResponse
import io.github.octoclonius.libreauto.protocol.carName
import io.github.octoclonius.libreauto.protocol.channelOpenRequest
import io.github.octoclonius.libreauto.protocol.googleAutomotiveLinkRoot
import io.github.octoclonius.libreauto.protocol.inputBindingRequest
import io.github.octoclonius.libreauto.protocol.inputSource
import io.github.octoclonius.libreauto.protocol.isProjected
import io.github.octoclonius.libreauto.protocol.parseByeByeRequest
import io.github.octoclonius.libreauto.protocol.parseChannelOpenResponse
import io.github.octoclonius.libreauto.protocol.parseInputBindingResponse
import io.github.octoclonius.libreauto.protocol.parseInputReport
import io.github.octoclonius.libreauto.protocol.parseMediaAck
import io.github.octoclonius.libreauto.protocol.parseMediaSetupResponse
import io.github.octoclonius.libreauto.protocol.parsePingRequest
import io.github.octoclonius.libreauto.protocol.parseServiceDiscoveryResponse
import io.github.octoclonius.libreauto.protocol.parseVideoFocusNotification
import io.github.octoclonius.libreauto.protocol.pingResponse
import io.github.octoclonius.libreauto.protocol.serviceDiscoveryRequest
import io.github.octoclonius.libreauto.protocol.videoFormat
import io.github.octoclonius.libreauto.protocol.videoSetupRequest
import io.github.octoclonius.libreauto.protocol.videoSink
import io.github.octoclonius.libreauto.protocol.videoStart
import io.github.octoclonius.libreauto.session.certificate.CertificateProvider
import io.github.octoclonius.libreauto.session.certificate.CertificateProvider.FetchResult
import io.github.octoclonius.libreauto.session.video.TestPatternStream
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
        var input: InputChannelState? = null
        try {
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
                    video =
                        if (isChannelOpened(message, "video")) {
                            send(
                                writer,
                                tls,
                                MediaMessageType.SETUP_REQUEST,
                                videoSetupRequest(),
                                current.channel,
                            )
                            VideoChannelState.SettingUp(
                                current.channel,
                                current.configs,
                                pendingFocus = null,
                            )
                        } else {
                            null
                        }
                    continue
                }
                if (
                    current is VideoChannelState.SettingUp &&
                        message.channel == current.channel &&
                        message.type == MediaMessageType.SETUP_RESPONSE
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
                    val config =
                        current.configs.getOrNull(configIndex)
                            ?: throw ProtocolException(
                                "The car accepted video config $configIndex of " +
                                    "${current.configs.size} on channel ${current.channel}"
                            )
                    val ready =
                        VideoChannelState.Ready(
                            current.channel,
                            configIndex,
                            videoFormat(config),
                            maxUnacked,
                        )
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
                        message.type == MediaMessageType.VIDEO_FOCUS_NOTIFICATION
                ) {
                    val notification = parseVideoFocusNotification(message.body)
                    Log.i(
                        TAG,
                        "Video focus on channel ${current.channel}: mode ${notification.mode}, " +
                            "unsolicited ${notification.unsolicited}",
                    )
                    video =
                        when (current) {
                            is VideoChannelState.SettingUp ->
                                current.copy(pendingFocus = notification)
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
                if (
                    current is VideoChannelState.Started &&
                        message.channel == current.channel &&
                        message.type == MediaMessageType.ACK
                ) {
                    current.stream.acknowledge(parseMediaAck(message.body))
                    continue
                }
                val currentInput = input
                if (
                    currentInput is InputChannelState.Opening &&
                        message.channel == currentInput.channel &&
                        message.type == MessageType.CHANNEL_OPEN_RESPONSE
                ) {
                    input =
                        if (isChannelOpened(message, "input")) {
                            Log.i(TAG, "Binding the keys ${currentInput.keycodes}")
                            send(
                                writer,
                                tls,
                                InputMessageType.BINDING_REQUEST,
                                inputBindingRequest(currentInput.keycodes),
                                currentInput.channel,
                            )
                            InputChannelState.Open(currentInput.channel)
                        } else {
                            null
                        }
                    continue
                }
                if (
                    currentInput is InputChannelState.Open &&
                        message.channel == currentInput.channel
                ) {
                    when (message.type) {
                        InputMessageType.BINDING_RESPONSE -> {
                            val status = parseInputBindingResponse(message.body)
                            if (status == MessageStatus.SUCCESS) {
                                Log.i(TAG, "The car bound the keys")
                            } else {
                                Log.w(TAG, "The car refused the key binding: status $status")
                            }
                        }
                        InputMessageType.REPORT -> {
                            val report = parseInputReport(message.body)
                            val touch = report.touch
                            if (touch == null) {
                                logUnhandled(message)
                            } else {
                                Log.i(TAG, "Touch read at ${report.timestamp}: $touch")
                            }
                        }
                        else -> logUnhandled(message)
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
                        val sink = videoSink(response)
                        if (sink == null) {
                            Log.w(TAG, "The car has no video channel for its main display")
                        } else {
                            openChannel(writer, tls, sink.channel, "video")
                            video = VideoChannelState.Opening(sink.channel, sink.configs)
                            val source = inputSource(response, sink.displayId)
                            if (source == null) {
                                Log.w(
                                    TAG,
                                    "The car has no input channel for display ${sink.displayId}",
                                )
                            } else {
                                openChannel(writer, tls, source.channel, "input")
                                input = InputChannelState.Opening(source.channel, source.keycodes)
                            }
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
        } finally {
            (video as? VideoChannelState.Started)?.stream?.close()
        }
    }

    private fun openChannel(writer: FrameWriter, tls: TlsServer, channel: Int, name: String) {
        Log.i(TAG, "Opening the $name channel $channel")
        send(
            writer,
            tls,
            MessageType.CHANNEL_OPEN_REQUEST,
            channelOpenRequest(channel),
            channel,
            isControl = true,
        )
    }

    private fun isChannelOpened(message: ChannelMessage, name: String): Boolean {
        val status = parseChannelOpenResponse(message.body)
        if (status != MessageStatus.SUCCESS) {
            Log.w(TAG, "The car refused the $name channel ${message.channel}: status $status")
            return false
        }
        Log.i(TAG, "The car opened the $name channel ${message.channel}")
        return true
    }

    private fun startVideo(
        writer: FrameWriter,
        tls: TlsServer,
        video: VideoChannelState.Ready,
    ): VideoChannelState.Started {
        send(writer, tls, MediaMessageType.START, videoStart(video.configIndex), video.channel)
        Log.i(TAG, "Started video on channel ${video.channel} with config ${video.configIndex}")
        val stream =
            TestPatternStream(video.format, video.maxUnacked) { type, body ->
                send(writer, tls, type, body, video.channel)
            }
        stream.start()
        return VideoChannelState.Started(video.channel, stream)
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

    data class Opening(override val channel: Int, val configs: List<VideoConfig>) :
        VideoChannelState

    data class SettingUp(
        override val channel: Int,
        val configs: List<VideoConfig>,
        val pendingFocus: VideoFocusNotification?,
    ) : VideoChannelState

    data class Ready(
        override val channel: Int,
        val configIndex: Int,
        val format: VideoFormat,
        val maxUnacked: Int,
    ) : VideoChannelState

    data class Started(override val channel: Int, val stream: TestPatternStream) :
        VideoChannelState
}

private sealed interface InputChannelState {
    val channel: Int

    data class Opening(override val channel: Int, val keycodes: List<Int>) : InputChannelState

    data class Open(override val channel: Int) : InputChannelState
}

private fun Closeable.closeQuietly() {
    try {
        close()
    } catch (_: IOException) {}
}
