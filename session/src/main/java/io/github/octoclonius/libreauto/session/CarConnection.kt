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
import io.github.octoclonius.libreauto.protocol.control.Handshake
import io.github.octoclonius.libreauto.protocol.control.MessageType
import io.github.octoclonius.libreauto.protocol.control.ProtocolVersion
import io.github.octoclonius.libreauto.protocol.control.ServiceDiscoveryResponse
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
import io.github.octoclonius.libreauto.session.certificate.CertificateProvider
import io.github.octoclonius.libreauto.session.certificate.CertificateProvider.FetchResult
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.ProtocolException
import javax.net.ssl.SSLException

class CarConnection(context: Context, private val holder: CarSessionHolder) {

    companion object {
        private const val TAG = "CarConnection"
    }

    private val context = context.applicationContext
    private val certificateProvider = CertificateProvider(this.context)

    fun run(input: InputStream, output: OutputStream): Boolean {
        holder.onState(CarConnectionState.Connecting)
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
            holder.onState(
                if (holder.state.value is CarConnectionState.Connected) {
                    CarConnectionState.Disconnected
                } else {
                    CarConnectionState.Failed
                }
            )
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
        var session: CarSession? = null
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
                    if (session?.onChannelMessage(message) != true) logUnhandled(message)
                    continue
                }
                when (message.type) {
                    MessageType.SERVICE_DISCOVERY_RESPONSE ->
                        if (session == null) {
                            val started = CarSession(context, link, control, version)
                            session = started
                            holder.onSession(started)
                            start(started, parseServiceDiscoveryResponse(message.body))
                        } else {
                            Log.w(TAG, "Ignored another service discovery response")
                        }
                    MessageType.AUDIO_FOCUS_NOTIFICATION ->
                        if (session?.onAudioFocusNotification(message) != true) {
                            logUnhandled(message)
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
            if (session != null) holder.onSession(null)
            session?.close()
        }
    }

    private fun start(session: CarSession, services: ServiceDiscoveryResponse) {
        services.services.forEach { Log.i(TAG, "Service on channel ${it.id}: $it") }
        val connected =
            CarConnectionState.Connected(
                carName(services),
                isDriverOnRight(services),
                CarStatusBar(
                    isClockOnCar(services),
                    isPhoneSignalOnCar(services),
                    isBatteryOnCar(services),
                ),
            )
        Log.i(TAG, "The car is $connected")
        holder.onState(connected)
        session.start(services, connected.isDriverOnRight)
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
