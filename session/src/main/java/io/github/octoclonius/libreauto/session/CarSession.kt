package io.github.octoclonius.libreauto.session

import android.content.Context
import android.util.Log
import io.github.octoclonius.libreauto.protocol.ChannelMessage
import io.github.octoclonius.libreauto.protocol.bluetooth.carBluetooth
import io.github.octoclonius.libreauto.protocol.control.ProtocolVersion
import io.github.octoclonius.libreauto.protocol.control.ServiceDiscoveryResponse
import io.github.octoclonius.libreauto.protocol.input.inputSource
import io.github.octoclonius.libreauto.protocol.media.AudioType
import io.github.octoclonius.libreauto.protocol.media.audioSink
import io.github.octoclonius.libreauto.protocol.media.audioSource
import io.github.octoclonius.libreauto.protocol.media.videoSink
import io.github.octoclonius.libreauto.session.audio.AudioChannel
import io.github.octoclonius.libreauto.session.audio.CarAudio
import io.github.octoclonius.libreauto.session.audio.MicrophoneChannel
import io.github.octoclonius.libreauto.session.bluetooth.BluetoothChannel
import io.github.octoclonius.libreauto.session.input.CarButtons
import io.github.octoclonius.libreauto.session.input.InputChannel
import io.github.octoclonius.libreauto.session.tasks.CarTasks
import io.github.octoclonius.libreauto.session.video.CarDisplays
import io.github.octoclonius.libreauto.session.video.VideoChannel
import java.io.Closeable

class CarSession internal constructor(
    private val context: Context,
    private val link: CarLink,
    private val control: ChannelSender,
    private val version: ProtocolVersion,
) : Closeable {

    private companion object {
        const val TAG = "CarSession"
    }

    val displays = CarDisplays()
    val tasks = CarTasks()
    val buttons = CarButtons(context)

    private val channels = LinkedHashMap<Int, CarChannel>()
    private var audio: CarAudio? = null

    internal fun start(services: ServiceDiscoveryResponse, isDriverOnRight: Boolean) {
        startVideo(services, isDriverOnRight)
        startAudio(services)
        startBluetooth(services)
    }

    internal fun onChannelMessage(message: ChannelMessage): Boolean =
        channels[message.channel]?.onMessage(message) == true

    internal fun onAudioFocusNotification(message: ChannelMessage): Boolean {
        val current = audio ?: return false
        current.onFocusNotification(message)
        return true
    }

    override fun close() {
        closeAll(TAG) {
            step("Closing the car audio") { audio?.close() }
            for (channel in channels.values) {
                step("Closing the channel ${channel.channel}") { channel.close() }
            }
        }
    }

    private fun startVideo(services: ServiceDiscoveryResponse, isDriverOnRight: Boolean) {
        val sink = videoSink(services)
        if (sink == null) {
            Log.w(TAG, "The car has no video channel for its main display")
            return
        }
        val video =
            VideoChannel(
                context,
                ChannelSender(link, sink.channel),
                sink.configs,
                version,
                isDriverOnRight,
                displays,
                tasks,
            )
        open(video)
        val source = inputSource(services, sink.displayId)
        if (source == null) {
            Log.w(TAG, "The car has no input channel for display ${sink.displayId}")
            return
        }
        open(
            InputChannel(
                ChannelSender(link, source.channel),
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
        val carAudio = CarAudio(context, control)
        audio = carAudio
        val audioChannels =
            sinks.map { (type, sink) ->
                AudioChannel(
                    type,
                    ChannelSender(link, sink.channel),
                    sink.configs,
                    carAudio::onChannelSettled,
                    carAudio::onStreamStartedOrStopped,
                )
            }
        val microphone =
            source?.let {
                MicrophoneChannel(
                    ChannelSender(link, it.channel),
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
        open(BluetoothChannel(context, ChannelSender(link, bluetooth.channel), bluetooth, version))
    }

    private fun open(channel: CarChannel) {
        channels[channel.channel] = channel
        channel.open()
    }
}
