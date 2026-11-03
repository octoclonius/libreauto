package io.github.octoclonius.libreauto.projection

import android.os.Build
import android.os.SystemClock
import android.util.Log
import io.github.octoclonius.libreauto.projection.certificate.CertificateRepository
import io.github.octoclonius.libreauto.projection.certificate.CertificateRepository.FetchResult
import io.github.octoclonius.libreauto.projection.channel.CarLink
import io.github.octoclonius.libreauto.projection.channel.ChannelSender
import io.github.octoclonius.libreauto.projection.channel.logUnhandled
import io.github.octoclonius.libreauto.protocol.CONTROL_CHANNEL
import io.github.octoclonius.libreauto.protocol.ChannelMessage
import io.github.octoclonius.libreauto.protocol.FrameReader
import io.github.octoclonius.libreauto.protocol.FrameWriter
import io.github.octoclonius.libreauto.protocol.SendPriority
import io.github.octoclonius.libreauto.protocol.TlsServer
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
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.ProtocolException
import javax.net.ssl.SSLException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

internal class CarConnection(
    private val holder: CarSessionHolder,
    private val certificates: CertificateRepository,
    private val newSession: (CarLink, ChannelSender, ProtocolVersion, CarInfo) -> CarSession,
) {

    companion object {
        private const val TAG = "CarConnection"
    }

    fun run(input: InputStream, output: OutputStream): Boolean {
        holder.onState(CarConnectionState.Connecting)
        try {
            val credentials = runBlocking { credentials() } ?: return false
            val tls = TlsServer(credentials.certificate, credentials.key)
            val reader = FrameReader(input, tls::decrypt, SystemClock::uptimeNanos)
            val version = Handshake(reader, FrameWriter(output), tls).run()
            Log.i(TAG, "Handshake done with a car on protocol $version")
            CarLink(FrameWriter(output, tls::encrypt)).use { link ->
                link.start()
                val control = link.sender(CONTROL_CHANNEL, SendPriority.DEFAULT)
                control.send(
                    MessageType.SERVICE_DISCOVERY_REQUEST,
                    serviceDiscoveryRequest("${Build.MANUFACTURER} ${Build.MODEL}"),
                )
                serve(reader, link, control, version)
            }
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
                session?.failure?.let { throw it }
                if (!message.isEncrypted) {
                    throw ProtocolException(
                        "Plaintext message 0x%04x on channel %d after the handshake"
                            .format(message.type, message.channel)
                    )
                }
                if (message.channel != CONTROL_CHANNEL) {
                    route(session, message)
                    continue
                }
                when (message.type) {
                    MessageType.SERVICE_DISCOVERY_RESPONSE ->
                        if (session == null) {
                            session = start(link, control, version, message)
                        } else {
                            Log.w(TAG, "Ignored another service discovery response")
                        }
                    MessageType.AUDIO_FOCUS_NOTIFICATION -> route(session, message)
                    MessageType.PING_REQUEST ->
                        control.send(
                            MessageType.PING_RESPONSE,
                            pingResponse(parsePingRequest(message.body)),
                        )
                    MessageType.BYE_BYE_REQUEST -> {
                        val reason = parseByeByeRequest(message.body)
                        Log.i(TAG, "The car says bye-bye with reason $reason")
                        control.sendLast(MessageType.BYE_BYE_RESPONSE, byeByeResponse())
                        return
                    }
                    else -> logUnhandled(TAG, message)
                }
            }
        } finally {
            if (session != null) holder.onSession(null)
            session?.close()
        }
    }

    private fun route(session: CarSession?, message: ChannelMessage) {
        if (session == null) logUnhandled(TAG, message) else session.onMessage(message)
    }

    private fun start(
        link: CarLink,
        control: ChannelSender,
        version: ProtocolVersion,
        message: ChannelMessage,
    ): CarSession {
        val services = parseServiceDiscoveryResponse(message.body)
        services.services.forEach { Log.i(TAG, "Service on channel ${it.id}: $it") }
        val car =
            CarInfo(
                carName(services),
                isDriverOnRight(services),
                CarStatusBar(
                    isClockOnCar(services),
                    isPhoneSignalOnCar(services),
                    isBatteryOnCar(services),
                ),
            )
        Log.i(TAG, "The car is $car")
        val session = newSession(link, control, version, car)
        holder.onSession(session)
        holder.onState(CarConnectionState.Connected(car))
        session.start(services)
        return session
    }

    private suspend fun credentials(): FetchResult.Success? {
        val choice = certificates.selection.first()
        if (choice == null) {
            Log.w(TAG, "No certificate provider selected")
            return null
        }
        return when (val result = certificates.fetch(choice)) {
            is FetchResult.Success -> result
            is FetchResult.Error -> {
                Log.w(TAG, "No certificate from ${choice.packageName}: ${result.message}")
                null
            }
        }
    }
}
