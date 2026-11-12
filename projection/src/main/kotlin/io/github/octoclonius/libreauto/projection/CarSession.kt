package io.github.octoclonius.libreauto.projection

import android.content.Context
import android.util.Log
import io.github.octoclonius.libreauto.projection.audio.AudioChannel
import io.github.octoclonius.libreauto.projection.audio.CarAudio
import io.github.octoclonius.libreauto.projection.audio.MicrophoneChannel
import io.github.octoclonius.libreauto.projection.bluetooth.BluetoothChannel
import io.github.octoclonius.libreauto.projection.calls.CallRepository
import io.github.octoclonius.libreauto.projection.channel.CarChannel
import io.github.octoclonius.libreauto.projection.channel.CarLink
import io.github.octoclonius.libreauto.projection.channel.ChannelSender
import io.github.octoclonius.libreauto.projection.channel.logUnhandled
import io.github.octoclonius.libreauto.projection.display.CarDisplays
import io.github.octoclonius.libreauto.projection.display.CarHome
import io.github.octoclonius.libreauto.projection.input.CarButtons
import io.github.octoclonius.libreauto.projection.input.InputChannel
import io.github.octoclonius.libreauto.projection.media.MediaResumption
import io.github.octoclonius.libreauto.projection.permissions.PermissionsRepository
import io.github.octoclonius.libreauto.projection.privileged.PrivilegedServiceConnection
import io.github.octoclonius.libreauto.projection.tasks.CarTasks
import io.github.octoclonius.libreauto.projection.tasks.OpenCarTasksRepository
import io.github.octoclonius.libreauto.projection.video.VideoChannel
import io.github.octoclonius.libreauto.projection.video.VideoProfiler
import io.github.octoclonius.libreauto.protocol.CONTROL_CHANNEL
import io.github.octoclonius.libreauto.protocol.ChannelMessage
import io.github.octoclonius.libreauto.protocol.SendPriority
import io.github.octoclonius.libreauto.protocol.bluetooth.carBluetooth
import io.github.octoclonius.libreauto.protocol.control.MessageType
import io.github.octoclonius.libreauto.protocol.control.ProtocolVersion
import io.github.octoclonius.libreauto.protocol.control.ServiceDiscoveryResponse
import io.github.octoclonius.libreauto.protocol.input.inputSource
import io.github.octoclonius.libreauto.protocol.media.AudioType
import io.github.octoclonius.libreauto.protocol.media.audioSink
import io.github.octoclonius.libreauto.protocol.media.audioSource
import io.github.octoclonius.libreauto.protocol.media.videoSink
import java.io.Closeable
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking

class CarSession
internal constructor(
    private val context: Context,
    private val link: CarLink,
    private val control: ChannelSender,
    private val version: ProtocolVersion,
    val car: CarInfo,
    private val calls: CallRepository,
    private val privileged: PrivilegedServiceConnection,
    private val permissions: PermissionsRepository,
    carHome: CarHome,
    openTasks: OpenCarTasksRepository,
    private val mediaResumption: MediaResumption,
    private val videoProfiler: VideoProfiler,
    private val dispatcher: CoroutineDispatcher,
) : Closeable {

    private companion object {
        const val TAG = "CarSession"
    }

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    val displays = CarDisplays(carHome)
    val tasks = CarTasks(displays.car, scope, openTasks)
    val buttons = CarButtons(context, calls)

    private val inbox = SessionInbox(scope, ::handle)
    private val channels = LinkedHashMap<Int, CarChannel>()
    private var audio: CarAudio? = null

    internal val failure: IOException?
        get() = inbox.failure

    internal fun start(services: ServiceDiscoveryResponse) {
        inbox.start {
            startVideo(services)
            startAudio(services)
            startBluetooth(services)
        }
    }

    internal fun onMessage(message: ChannelMessage) {
        inbox.send(message)
    }

    override fun close() {
        closeAll(TAG) {
            step("Stopping the messages from the car") { inbox.close() }
            step("Closing the channels") { runBlocking(dispatcher) { closeChannels() } }
            step("Canceling the session's work") { scope.cancel() }
        }
    }

    private suspend fun closeChannels() {
        closeAll(TAG) {
            step("Moving the car's tasks to the phone") { tasks.moveToPhone() }
            step("Closing the car audio") { audio?.close() }
            for (channel in channels.values) {
                step("Closing the channel ${channel.channel}") { channel.close() }
            }
        }
    }

    private fun handle(message: ChannelMessage) {
        val isHandled =
            if (message.channel == CONTROL_CHANNEL) {
                message.type == MessageType.AUDIO_FOCUS_NOTIFICATION && onAudioFocus(message)
            } else {
                channels[message.channel]?.onMessage(message) == true
            }
        if (!isHandled) logUnhandled(TAG, message)
    }

    private fun onAudioFocus(message: ChannelMessage): Boolean {
        val current = audio ?: return false
        current.onFocusNotification(message)
        return true
    }

    private fun startVideo(services: ServiceDiscoveryResponse) {
        val sink = videoSink(services)
        if (sink == null) {
            Log.w(TAG, "The car has no video channel for its main display")
            return
        }
        val video =
            VideoChannel(
                context,
                link.sender(sink.channel, SendPriority.VIDEO),
                sink.configs,
                version,
                car.isDriverOnRight,
                displays,
                tasks,
                privileged,
                videoProfiler,
            )
        open(video)
        val source = inputSource(services, sink.displayId)
        if (source == null) {
            Log.w(TAG, "The car has no input channel for display ${sink.displayId}")
            return
        }
        open(
            InputChannel(
                link.sender(source.channel, SendPriority.DEFAULT),
                source.keycodes,
                video::layout,
                buttons,
                displays,
            )
        )
    }

    private fun startAudio(services: ServiceDiscoveryResponse) {
        val sinks =
            listOf(AudioType.MEDIA, AudioType.GUIDANCE).mapNotNull { type ->
                audioSink(services, type)?.let { type to it }
            }
        if (sinks.isEmpty()) Log.w(TAG, "The car has no media or guidance audio channel")
        val source = audioSource(services)
        if (source == null) Log.w(TAG, "The car has no microphone")
        if (sinks.isEmpty() && source == null) return
        val carAudio = CarAudio(context, control, calls, privileged, mediaResumption, scope)
        audio = carAudio
        val audioChannels = sinks.map { (type, sink) ->
            AudioChannel(
                type,
                link.sender(sink.channel, SendPriority.AUDIO),
                sink.codec,
                sink.configs,
                carAudio::onChannelSettled,
                carAudio::onStreamStartedOrStopped,
            )
        }
        val microphone = source?.let {
            MicrophoneChannel(
                link.sender(it.channel, SendPriority.DEFAULT),
                it.config,
                carAudio::onChannelSettled,
            )
        }
        val all = audioChannels + listOfNotNull(microphone)
        all.forEach { channels[it.channel] = it }
        carAudio.start(audioChannels, microphone)
        all.forEach(CarChannel::open)
    }

    private fun startBluetooth(services: ServiceDiscoveryResponse) {
        val bluetooth = carBluetooth(services)
        if (bluetooth == null) {
            Log.w(TAG, "The car has no Bluetooth service LibreAuto can pair with")
            return
        }
        open(
            BluetoothChannel(
                context,
                link.sender(bluetooth.channel, SendPriority.DEFAULT),
                bluetooth,
                version,
                privileged,
                permissions,
                scope,
            )
        )
    }

    private fun open(channel: CarChannel) {
        channels[channel.channel] = channel
        channel.open()
    }
}
