package io.github.octoclonius.libreauto.session.video

import android.util.Log
import io.github.octoclonius.libreauto.protocol.ChannelMessage
import io.github.octoclonius.libreauto.protocol.control.MessageType
import io.github.octoclonius.libreauto.protocol.control.VideoConfig
import io.github.octoclonius.libreauto.protocol.media.MediaMessageType
import io.github.octoclonius.libreauto.protocol.media.VideoFocusNotification
import io.github.octoclonius.libreauto.protocol.media.VideoFormat
import io.github.octoclonius.libreauto.protocol.media.isProjected
import io.github.octoclonius.libreauto.protocol.media.parseMediaAck
import io.github.octoclonius.libreauto.protocol.media.parseMediaSetupResponse
import io.github.octoclonius.libreauto.protocol.media.parseVideoFocusNotification
import io.github.octoclonius.libreauto.protocol.media.preferredConfigIndex
import io.github.octoclonius.libreauto.protocol.media.projectedVideoFocusRequest
import io.github.octoclonius.libreauto.protocol.media.videoFormat
import io.github.octoclonius.libreauto.protocol.media.videoSetupRequest
import io.github.octoclonius.libreauto.protocol.media.videoStart
import io.github.octoclonius.libreauto.session.ChannelSender
import io.github.octoclonius.libreauto.session.isChannelOpened
import java.io.Closeable
import java.io.IOException
import java.net.ProtocolException

class VideoChannel(
    private val sender: ChannelSender,
    configs: List<VideoConfig>,
) : Closeable {

    companion object {
        private const val TAG = "VideoChannel"
    }

    private var state: VideoChannelState? = VideoChannelState.Opening(configs)

    val channel: Int
        get() = sender.channel

    val format: VideoFormat?
        get() = (state as? VideoChannelState.Started)?.format

    fun open() {
        Log.i(TAG, "Opening the video channel $channel")
        sender.sendOpenRequest()
    }

    fun onMessage(message: ChannelMessage): Boolean {
        val current = state
        when {
            current is VideoChannelState.Opening &&
                message.type == MessageType.CHANNEL_OPEN_RESPONSE ->
                state =
                    if (isChannelOpened(TAG, message, "video")) {
                        sender.send(MediaMessageType.SETUP_REQUEST, videoSetupRequest())
                        VideoChannelState.SettingUp(current.configs, pendingFocus = null)
                    } else {
                        null
                    }
            current is VideoChannelState.SettingUp &&
                message.type == MediaMessageType.SETUP_RESPONSE ->
                state = onSetupResponse(current, message)
            current != null &&
                current !is VideoChannelState.Opening &&
                message.type == MediaMessageType.VIDEO_FOCUS_NOTIFICATION ->
                state = onFocus(current, message)
            current is VideoChannelState.Started && message.type == MediaMessageType.ACK ->
                current.stream.acknowledge(parseMediaAck(message.body))
            else -> return false
        }
        return true
    }

    override fun close() {
        (state as? VideoChannelState.Started)?.stream?.close()
    }

    private fun onSetupResponse(
        current: VideoChannelState.SettingUp,
        message: ChannelMessage,
    ): VideoChannelState {
        val response = parseMediaSetupResponse(message.body)
        Log.i(
            TAG,
            "The car set up the video channel $channel: " +
                "status ${response.status}, " +
                "up to ${response.max_unacked} unacked frames, " +
                "configs ${response.config_indexes}",
        )
        val maxUnacked = response.max_unacked
        if (maxUnacked == null || maxUnacked <= 0) {
            throw ProtocolException(
                "The car allows $maxUnacked unacked frames on the video channel $channel"
            )
        }
        val configIndex =
            preferredConfigIndex(current.configs, response.config_indexes, H264Encoder::supports)
                ?: throw IOException(
                    "No H.264 encoder can encode the video configs " +
                        "${response.config_indexes} the car accepted on channel $channel"
                )
        val config = current.configs[configIndex]
        Log.i(TAG, "Video config $configIndex has density ${config.density}")
        val format = videoFormat(config)
        if (!format.areContentInsetsUsable) {
            Log.w(
                TAG,
                "Ignored the content insets ${format.contentInsets} of video config " +
                    "$configIndex: they leave no content area in the visible " +
                    "${format.visibleWidth}x${format.visibleHeight}",
            )
        }
        val ready = VideoChannelState.Ready(configIndex, format, maxUnacked)
        if (current.pendingFocus?.isProjected == true) return start(ready)
        sender.send(MediaMessageType.VIDEO_FOCUS_REQUEST, projectedVideoFocusRequest())
        Log.i(TAG, "Requested projected video focus on channel $channel")
        return ready
    }

    private fun onFocus(current: VideoChannelState, message: ChannelMessage): VideoChannelState {
        val notification = parseVideoFocusNotification(message.body)
        Log.i(
            TAG,
            "Video focus on channel $channel: mode ${notification.mode}, " +
                "unsolicited ${notification.unsolicited}",
        )
        return when (current) {
            is VideoChannelState.SettingUp -> current.copy(pendingFocus = notification)
            is VideoChannelState.Ready ->
                if (notification.isProjected) start(current) else current
            else -> current
        }
    }

    private fun start(ready: VideoChannelState.Ready): VideoChannelState.Started {
        sender.send(MediaMessageType.START, videoStart(ready.configIndex))
        Log.i(TAG, "Started video on channel $channel with config ${ready.configIndex}")
        val stream = VideoStream(ready.format, ready.maxUnacked, sender::send)
        stream.start()
        return VideoChannelState.Started(ready.format, stream)
    }
}

private sealed interface VideoChannelState {

    data class Opening(val configs: List<VideoConfig>) : VideoChannelState

    data class SettingUp(
        val configs: List<VideoConfig>,
        val pendingFocus: VideoFocusNotification?,
    ) : VideoChannelState

    data class Ready(val configIndex: Int, val format: VideoFormat, val maxUnacked: Int) :
        VideoChannelState

    data class Started(val format: VideoFormat, val stream: VideoStream) : VideoChannelState
}
