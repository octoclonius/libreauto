package io.github.octoclonius.libreauto.projection.video

import android.content.Context
import android.util.Log
import io.github.octoclonius.libreauto.projection.channel.CarChannel
import io.github.octoclonius.libreauto.projection.channel.ChannelSender
import io.github.octoclonius.libreauto.projection.channel.isChannelOpened
import io.github.octoclonius.libreauto.projection.display.CarDisplays
import io.github.octoclonius.libreauto.projection.display.CarLayout
import io.github.octoclonius.libreauto.projection.display.carLayout
import io.github.octoclonius.libreauto.projection.privileged.PrivilegedServiceConnection
import io.github.octoclonius.libreauto.projection.tasks.CarTasks
import io.github.octoclonius.libreauto.protocol.ChannelMessage
import io.github.octoclonius.libreauto.protocol.control.MessageType
import io.github.octoclonius.libreauto.protocol.control.ProtocolVersion
import io.github.octoclonius.libreauto.protocol.control.UiConfig
import io.github.octoclonius.libreauto.protocol.control.VideoConfig
import io.github.octoclonius.libreauto.protocol.media.CarInsets
import io.github.octoclonius.libreauto.protocol.media.MediaMessageType
import io.github.octoclonius.libreauto.protocol.media.VideoCodec
import io.github.octoclonius.libreauto.protocol.media.VideoFocusNotification
import io.github.octoclonius.libreauto.protocol.media.VideoFormat
import io.github.octoclonius.libreauto.protocol.media.decoderDepth
import io.github.octoclonius.libreauto.protocol.media.isProjected
import io.github.octoclonius.libreauto.protocol.media.parseMediaAck
import io.github.octoclonius.libreauto.protocol.media.parseMediaSetupResponse
import io.github.octoclonius.libreauto.protocol.media.parseUiConfigUpdate
import io.github.octoclonius.libreauto.protocol.media.parseVideoFocusNotification
import io.github.octoclonius.libreauto.protocol.media.preferredConfigIndex
import io.github.octoclonius.libreauto.protocol.media.preferredVideoCodec
import io.github.octoclonius.libreauto.protocol.media.projectedVideoFocusRequest
import io.github.octoclonius.libreauto.protocol.media.uiConfigInUse
import io.github.octoclonius.libreauto.protocol.media.uiConfigUpdateReply
import io.github.octoclonius.libreauto.protocol.media.updatedUiConfig
import io.github.octoclonius.libreauto.protocol.media.videoFormat
import io.github.octoclonius.libreauto.protocol.media.videoSetupRequest
import io.github.octoclonius.libreauto.protocol.media.withUiConfig
import java.io.IOException
import java.net.ProtocolException

internal class VideoChannel(
    private val context: Context,
    private val sender: ChannelSender,
    configs: List<VideoConfig>,
    private val car: ProtocolVersion,
    private val isDriverOnRight: Boolean,
    private val displays: CarDisplays,
    private val tasks: CarTasks,
    private val privileged: PrivilegedServiceConnection,
    private val profiler: VideoProfiler,
) : CarChannel {

    companion object {
        private const val TAG = "VideoChannel"
    }

    private var state: VideoChannelState? = VideoChannelState.Opening(configs)

    override val channel: Int
        get() = sender.channel

    val layout: CarLayout?
        get() = (state as? VideoChannelState.Started)?.layout

    override fun open() {
        Log.i(TAG, "Opening the video channel $channel")
        sender.sendOpenRequest()
    }

    override fun onMessage(message: ChannelMessage): Boolean {
        val current = state
        when {
            current is VideoChannelState.Opening &&
                message.type == MessageType.CHANNEL_OPEN_RESPONSE ->
                state =
                    if (isChannelOpened(TAG, message, "video")) {
                        setUp(current.configs)
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
            current != null &&
                current !is VideoChannelState.Opening &&
                message.type == MediaMessageType.UPDATE_UI_CONFIG_REQUEST ->
                state = onUiConfigUpdate(current, message)
            current is VideoChannelState.Started && message.type == MediaMessageType.ACK -> {
                val ack = parseMediaAck(message.body)
                current.stream.acknowledge(ack.session, ack.count)
            }
            else -> return false
        }
        return true
    }

    override fun close() {
        (state as? VideoChannelState.Started)?.stream?.close()
        displays.car.onCarDensity(null)
    }

    private fun setUp(configs: List<VideoConfig>): VideoChannelState.SettingUp {
        val codec =
            preferredVideoCodec(configs, EncoderSettings::encoderKind)
                ?: throw IOException(
                    "The phone can't encode any of the video configs $configs " +
                        "the car offered on channel $channel"
                )
        sender.send(MediaMessageType.SETUP_REQUEST, videoSetupRequest(codec))
        Log.i(TAG, "Requested the video setup on channel $channel in $codec")
        return VideoChannelState.SettingUp(
            configs,
            codec,
            pendingFocus = null,
            pendingUiConfigs = emptyList(),
        )
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
            preferredConfigIndex(
                current.configs,
                response.config_indexes,
                current.codec,
                EncoderSettings::encoderKind,
            )
                ?: throw IOException(
                    "The phone can't encode any of the video configs " +
                        "${response.config_indexes} the car accepted on channel $channel " +
                        "with ${current.codec}"
                )
        val config = current.configs[configIndex]
        val decoderDepth = decoderDepth(config, car)
        Log.i(
            TAG,
            "Video config $configIndex has density ${config.density}, decoder depth $decoderDepth",
        )
        var format = videoFormat(config)
        displays.car.onCarDensity(format.density)
        logUnused(format, "video config $configIndex")
        var uiConfig = config.ui_config ?: UiConfig()
        for (update in current.pendingUiConfigs) {
            val (updatedFormat, updatedUiConfig) = updated(format, uiConfig, update)
            format = updatedFormat
            uiConfig = updatedUiConfig
            reply(format, uiConfig)
        }
        val ready =
            VideoChannelState.Ready(
                configIndex,
                current.codec,
                format,
                uiConfig,
                decoderDepth,
                maxUnacked,
            )
        if (current.pendingFocus?.isProjected == true) return start(ready)
        sender.send(MediaMessageType.VIDEO_FOCUS_REQUEST, projectedVideoFocusRequest())
        Log.i(TAG, "Requested projected video focus on channel $channel")
        return ready
    }

    private fun onUiConfigUpdate(
        current: VideoChannelState,
        message: ChannelMessage,
    ): VideoChannelState {
        val update = parseUiConfigUpdate(message.body)
        Log.i(TAG, "The car updated its UI config on channel $channel: $update")
        return when (current) {
            is VideoChannelState.SettingUp ->
                current.copy(pendingUiConfigs = current.pendingUiConfigs + update)
            is VideoChannelState.Ready -> {
                val (format, uiConfig) = updated(current.format, current.uiConfig, update)
                reply(format, uiConfig)
                current.copy(format = format, uiConfig = uiConfig)
            }
            is VideoChannelState.Started -> {
                val (format, uiConfig) = updated(current.layout.format, current.uiConfig, update)
                val previous = current.layout
                val layout = carLayout(format, isDriverOnRight, previous.appsDensity)
                if (layout.appsDensity < previous.appsDensity) {
                    Log.i(
                        TAG,
                        "Lowered the apps density from ${previous.appsDensity} to " +
                            "${layout.appsDensity} dpi to keep the tablet layout",
                    )
                }
                if (layout == previous) {
                    reply(format, uiConfig)
                } else {
                    current.stream.updateLayout(layout) { reply(format, uiConfig) }
                }
                current.copy(layout = layout, uiConfig = uiConfig)
            }
            else -> current
        }
    }

    private fun updated(
        format: VideoFormat,
        uiConfig: UiConfig,
        update: UiConfig,
    ): Pair<VideoFormat, UiConfig> {
        val next = updatedUiConfig(uiConfig, update, car)
        val nextFormat = format.withUiConfig(next)
        if (update.margins != null && uiConfigInUse(nextFormat, next).margins != next.margins) {
            Log.w(
                TAG,
                "Kept the margins ${format.margins}: the car's new margins ${next.margins} " +
                    "would need another video config",
            )
        }
        logUnused(nextFormat, "the UI config update")
        return nextFormat to next
    }

    private fun reply(format: VideoFormat, uiConfig: UiConfig) {
        val inUse = uiConfigInUse(format, uiConfig)
        if (sender.send(MediaMessageType.UPDATE_UI_CONFIG_REPLY, uiConfigUpdateReply(inUse))) {
            Log.i(TAG, "Replied to the UI config update on channel $channel with $inUse")
        }
    }

    private fun logUnused(format: VideoFormat, source: String) {
        if (!format.areContentInsetsUsable) {
            Log.w(
                TAG,
                "Ignored the content insets ${format.contentInsets} of $source: " +
                    "they leave no content area in the visible " +
                    "${format.visibleWidth}x${format.visibleHeight}",
            )
        }
        if (!format.areStableInsetsUsable) {
            Log.w(
                TAG,
                "Ignored the stable insets ${format.stableInsets} of $source: " +
                    "they leave no content area in the visible " +
                    "${format.visibleWidth}x${format.visibleHeight}",
            )
        }
        if (format.cutouts.size > 1) {
            Log.w(
                TAG,
                "Only the first of the cutouts ${format.cutouts} of $source " +
                    "is kept out of the layout",
            )
        }
        if (format.cutouts.isNotEmpty() && format.displayCutoutInsets == CarInsets.NONE) {
            Log.w(
                TAG,
                "Ignored the cutout ${format.cutouts.first()} of $source: " +
                    "it leaves no content area in the visible " +
                    "${format.visibleWidth}x${format.visibleHeight}",
            )
        }
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
            is VideoChannelState.Ready -> if (notification.isProjected) start(current) else current
            is VideoChannelState.Started -> onFocusChanged(current, notification.isProjected)
            else -> current
        }
    }

    private fun onFocusChanged(
        current: VideoChannelState.Started,
        isProjected: Boolean,
    ): VideoChannelState.Started {
        if (isProjected == current.isProjected) return current
        if (isProjected) current.stream.resume() else current.stream.pause()
        return current.copy(isProjected = isProjected)
    }

    private fun start(ready: VideoChannelState.Ready): VideoChannelState.Started {
        Log.i(
            TAG,
            "Starting video on channel $channel with config ${ready.configIndex} in ${ready.codec}",
        )
        val layout = carLayout(ready.format, isDriverOnRight)
        val stream =
            VideoStream(
                context,
                layout,
                ready.configIndex,
                ready.codec,
                ready.decoderDepth,
                ready.maxUnacked,
                sender::send,
                displays,
                tasks,
                privileged,
                profiler,
            )
        stream.start()
        return VideoChannelState.Started(layout, ready.uiConfig, stream, isProjected = true)
    }
}

private sealed interface VideoChannelState {

    data class Opening(val configs: List<VideoConfig>) : VideoChannelState

    data class SettingUp(
        val configs: List<VideoConfig>,
        val codec: VideoCodec,
        val pendingFocus: VideoFocusNotification?,
        val pendingUiConfigs: List<UiConfig>,
    ) : VideoChannelState

    data class Ready(
        val configIndex: Int,
        val codec: VideoCodec,
        val format: VideoFormat,
        val uiConfig: UiConfig,
        val decoderDepth: Int,
        val maxUnacked: Int,
    ) : VideoChannelState

    data class Started(
        val layout: CarLayout,
        val uiConfig: UiConfig,
        val stream: VideoStream,
        val isProjected: Boolean,
    ) : VideoChannelState
}
