package io.github.octoclonius.libreauto.session

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.util.Log
import io.github.octoclonius.libreauto.protocol.CONTROL_CHANNEL
import io.github.octoclonius.libreauto.protocol.ChannelMessage
import io.github.octoclonius.libreauto.protocol.FrameReader
import io.github.octoclonius.libreauto.protocol.FrameWriter
import io.github.octoclonius.libreauto.protocol.TlsServer
import io.github.octoclonius.libreauto.protocol.bluetooth.carBluetooth
import io.github.octoclonius.libreauto.protocol.control.Handshake
import io.github.octoclonius.libreauto.protocol.control.MessageType
import io.github.octoclonius.libreauto.protocol.control.ProtocolVersion
import io.github.octoclonius.libreauto.protocol.control.byeByeResponse
import io.github.octoclonius.libreauto.protocol.control.carName
import io.github.octoclonius.libreauto.protocol.control.isBatteryOnCar
import io.github.octoclonius.libreauto.protocol.control.isClockOnCar
import io.github.octoclonius.libreauto.protocol.control.isDriverOnRight
import io.github.octoclonius.libreauto.protocol.control.isPhoneSignalOnCar
import io.github.octoclonius.libreauto.protocol.control.parseByeByeRequest
import io.github.octoclonius.libreauto.protocol.control.parsePingRequest
import io.github.octoclonius.libreauto.protocol.control.parseServiceDiscoveryResponse
import io.github.octoclonius.libreauto.protocol.control.pingResponse
import io.github.octoclonius.libreauto.protocol.control.serviceDiscoveryRequest
import io.github.octoclonius.libreauto.protocol.googleAutomotiveLinkRoot
import io.github.octoclonius.libreauto.protocol.input.inputSource
import io.github.octoclonius.libreauto.protocol.media.AudioType
import io.github.octoclonius.libreauto.protocol.media.audioSink
import io.github.octoclonius.libreauto.protocol.media.audioSource
import io.github.octoclonius.libreauto.protocol.media.videoSink
import io.github.octoclonius.libreauto.session.audio.AudioChannel
import io.github.octoclonius.libreauto.session.audio.CarAudio
import io.github.octoclonius.libreauto.session.audio.MicrophoneChannel
import io.github.octoclonius.libreauto.session.bluetooth.BluetoothChannel
import io.github.octoclonius.libreauto.session.certificate.CertificateProvider
import io.github.octoclonius.libreauto.session.certificate.CertificateProvider.FetchResult
import io.github.octoclonius.libreauto.session.input.CarButtons
import io.github.octoclonius.libreauto.session.input.InputChannel
import io.github.octoclonius.libreauto.session.video.VideoChannel
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.ProtocolException
import javax.net.ssl.SSLException
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

    fun run(input: InputStream, output: OutputStream): Boolean {
        _state.value = CarConnectionState.Connecting
        try {
            val credentials = credentials() ?: return false
            val tls = TlsServer(credentials.certificate, credentials.key, googleAutomotiveLinkRoot())
            val reader = FrameReader(input, tls::decrypt, SystemClock::uptimeNanos)
            val writer = FrameWriter(output)
            val version = Handshake(reader, writer, tls).run()
            Log.i(TAG, "Handshake done with a car on protocol $version")
            val link = CarLink(writer, tls)
            val control = ChannelSender(link, CONTROL_CHANNEL)
            control.send(
                MessageType.SERVICE_DISCOVERY_REQUEST,
                serviceDiscoveryRequest("${Build.MANUFACTURER} ${Build.MODEL}"),
            )
            serve(reader, link, control, version)
            return false
        } catch (_: EOFException) {
            Log.i(TAG, "The car closed the connection")
            return true
        } catch (e: ProtocolException) {
            Log.w(TAG, "The car broke the protocol", e)
            return false
        } catch (e: SSLException) {
            Log.w(TAG, "The TLS handshake with the car failed", e)
            return false
        } catch (e: IOException) {
            Log.w(TAG, "Connection to the car failed", e)
            return true
        } finally {
            _state.value =
                if (_state.value is CarConnectionState.Connected) {
                    CarConnectionState.Disconnected
                } else {
                    CarConnectionState.Failed
                }
            closeAll(TAG) {
                step("Closing the input from the car") { input.close() }
                step("Closing the output to the car") { output.close() }
            }
        }
    }

    private fun serve(
        reader: FrameReader,
        link: CarLink,
        control: ChannelSender,
        version: ProtocolVersion,
    ) {
        val channels = LinkedHashMap<Int, CarChannel>()
        var audio: CarAudio? = null
        try {
            while (true) {
                val message = reader.readMessage()
                if (link.isLost) throw IOException("Sending to the car failed")
                if (!message.isEncrypted) {
                    throw ProtocolException(
                        "Plaintext message 0x%04x on channel %d after the handshake"
                            .format(message.type, message.channel)
                    )
                }
                if (message.channel != CONTROL_CHANNEL) {
                    val target = channels[message.channel]
                    if (target == null || !target.onMessage(message)) logUnhandled(message)
                    continue
                }
                when (message.type) {
                    MessageType.SERVICE_DISCOVERY_RESPONSE -> {
                        val response = parseServiceDiscoveryResponse(message.body)
                        response.services.forEach { Log.i(TAG, "Service on channel ${it.id}: $it") }
                        val connected =
                            CarConnectionState.Connected(
                                carName(response),
                                isDriverOnRight(response),
                                CarStatusBar(
                                    isClockOnCar(response),
                                    isPhoneSignalOnCar(response),
                                    isBatteryOnCar(response),
                                ),
                            )
                        Log.i(TAG, "The car is $connected")
                        _state.value = connected
                        val sink = videoSink(response)
                        if (sink == null) {
                            Log.w(TAG, "The car has no video channel for its main display")
                        } else {
                            val videoChannel =
                                VideoChannel(
                                    context,
                                    ChannelSender(link, sink.channel),
                                    sink.configs,
                                    version,
                                    connected.isDriverOnRight,
                                )
                            channels[videoChannel.channel] = videoChannel
                            videoChannel.open()
                            val source = inputSource(response, sink.displayId)
                            if (source == null) {
                                Log.w(
                                    TAG,
                                    "The car has no input channel for display ${sink.displayId}",
                                )
                            } else {
                                val input =
                                    InputChannel(
                                        ChannelSender(link, source.channel),
                                        source.keycodes,
                                        videoChannel::layout,
                                        CarButtons(context),
                                    )
                                channels[input.channel] = input
                                input.open()
                            }
                        }
                        val audioSinks =
                            listOf(AudioType.MEDIA, AudioType.GUIDANCE).mapNotNull { type ->
                                audioSink(response, type)?.let { type to it }
                            }
                        if (audioSinks.isEmpty()) {
                            Log.w(TAG, "The car has no media or guidance audio channel")
                        }
                        val audioSource = audioSource(response)
                        if (audioSource == null) Log.w(TAG, "The car has no microphone")
                        if (audioSinks.isNotEmpty() || audioSource != null) {
                            val carAudio = CarAudio(context, control)
                            audio = carAudio
                            val audioChannels =
                                audioSinks.map { (type, audioSink) ->
                                    AudioChannel(
                                        type,
                                        ChannelSender(link, audioSink.channel),
                                        audioSink.configs,
                                        carAudio::onChannelSettled,
                                        carAudio::onStreamStartedOrStopped,
                                    )
                                }
                            val microphone =
                                audioSource?.let {
                                    MicrophoneChannel(
                                        ChannelSender(link, it.channel),
                                        it.config,
                                        carAudio::onChannelSettled,
                                    )
                                }
                            val opened = audioChannels + listOfNotNull(microphone)
                            opened.forEach { channels[it.channel] = it }
                            carAudio.start(audioChannels, microphone)
                            opened.forEach(CarChannel::open)
                        }
                        val carBluetooth = carBluetooth(response)
                        if (carBluetooth == null) {
                            Log.w(TAG, "The car has no Bluetooth service LibreAuto can pair with")
                        } else {
                            val bluetooth =
                                BluetoothChannel(
                                    context,
                                    ChannelSender(link, carBluetooth.channel),
                                    carBluetooth,
                                    version,
                                )
                            channels[bluetooth.channel] = bluetooth
                            bluetooth.open()
                        }
                    }
                    MessageType.AUDIO_FOCUS_NOTIFICATION ->
                        audio?.onFocusNotification(message) ?: logUnhandled(message)
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
            closeAll(TAG) {
                step("Closing the car audio") { audio?.close() }
                for (channel in channels.values) {
                    step("Closing the channel ${channel.channel}") { channel.close() }
                }
            }
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
