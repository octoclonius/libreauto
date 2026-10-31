package io.github.octoclonius.libreauto.projection.video

import android.content.Context
import android.util.Log
import io.github.octoclonius.libreauto.projection.closeAll
import io.github.octoclonius.libreauto.projection.display.Area
import io.github.octoclonius.libreauto.projection.display.CarDisplays
import io.github.octoclonius.libreauto.projection.display.CarLayout
import io.github.octoclonius.libreauto.projection.display.OverlayVirtualDisplay
import io.github.octoclonius.libreauto.projection.display.PrivilegedDisplay
import io.github.octoclonius.libreauto.projection.display.RailVirtualDisplay
import io.github.octoclonius.libreauto.projection.privileged.PrivilegedServiceConnection
import io.github.octoclonius.libreauto.projection.tasks.CarTasks
import io.github.octoclonius.libreauto.protocol.media.MediaMessageType
import io.github.octoclonius.libreauto.protocol.media.VideoCodec
import io.github.octoclonius.libreauto.protocol.media.VideoFormat
import io.github.octoclonius.libreauto.protocol.media.mediaStart
import java.io.Closeable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

internal class VideoStream(
    private val context: Context,
    @Volatile private var layout: CarLayout,
    private val configIndex: Int,
    private val codec: VideoCodec,
    private val decoderDepth: Int,
    private val maxUnacked: Int,
    private val send: (type: Int, body: ByteArray) -> Boolean,
    private val displays: CarDisplays,
    private val tasks: CarTasks,
    private val privileged: PrivilegedServiceConnection,
    private val profiler: VideoProfiler,
) : Closeable {

    companion object {
        private const val TAG = "VideoStream"
        private const val FIRST_SESSION = 0
        private const val OVERLAY_LAYER = 2
    }

    private val executor: ExecutorService = Executors.newSingleThreadExecutor {
        Thread(it, "video-stream").apply { isDaemon = true }
    }
    private var session = FIRST_SESSION
    @Volatile private var encoder: VideoEncoder? = null
    private val displaysLock = Any()
    private var compositor: VideoCompositor? = null
    private var rail: RailVirtualDisplay? = null
    private var overlay: OverlayVirtualDisplay? = null
    private var display: PrivilegedDisplay? = null

    fun start() {
        executor.execute(::setUp)
        resume()
    }

    fun resume() {
        val started = VideoEncoder(layout, codec, maxUnacked, session, send, profiler)
        encoder = started
        executor.execute {
            val current = compositor
            if (current == null) {
                Log.w(TAG, "No video session ${started.session} without a compositor")
                return@execute
            }
            if (!send(MediaMessageType.START, mediaStart(started.session, configIndex))) {
                return@execute
            }
            Log.i(TAG, "Started video session ${started.session} with config $configIndex")
            started.run(current)
        }
    }

    fun pause() {
        val stopped = encoder ?: return
        encoder = null
        stopped.close()
        session++
        executor.execute {
            if (send(MediaMessageType.STOP, ByteArray(0))) {
                Log.i(TAG, "Stopped video session ${stopped.session}")
            }
        }
    }

    fun updateLayout(layout: CarLayout, onApplied: () -> Unit) {
        val bitRate = bitRate(layout.format)
        if (bitRate != bitRate(this.layout.format)) encoder?.setBitRate(bitRate)
        synchronized(displaysLock) {
            this.layout = layout
            try {
                rail?.resize(layout)
            } catch (e: RuntimeException) {
                Log.w(TAG, "Resizing the rail display failed", e)
            }
            try {
                overlay?.resize(layout)
            } catch (e: RuntimeException) {
                Log.w(TAG, "Resizing the overlay display failed", e)
            }
            display?.resize(layout)
            val current = compositor
            if (current == null) {
                onApplied()
            } else {
                current.setAreas(areas(layout)) { lastFrameNanos ->
                    val drawing = encoder
                    if (lastFrameNanos == null || drawing == null) {
                        onApplied()
                    } else {
                        drawing.afterFrame(lastFrameNanos, onApplied)
                    }
                }
            }
        }
    }

    fun acknowledge(ackedSession: Int?, count: Int) {
        val current = encoder ?: return
        if (ackedSession != null && ackedSession != current.session) return
        current.acknowledge(count)
    }

    override fun close() {
        encoder?.close()
        encoder = null
        executor.execute(::release)
        executor.shutdown()
    }

    private fun setUp() {
        synchronized(displaysLock) {
            try {
                val created =
                    VideoCompositor(layout.format, areas(layout), decoderDepth, profiler) {
                        encoder?.close()
                    }
                compositor = created
                val (appsInput, railInput, overlayInput) = created.inputs
                rail = RailVirtualDisplay(context, railInput, layout, displays.rail)
                overlay =
                    OverlayVirtualDisplay(context, overlayInput, layout, displays.overlay) {
                        created.setHidden(OVERLAY_LAYER, !it)
                    }
                display =
                    PrivilegedDisplay(
                            appsInput,
                            layout,
                            privileged,
                            displays.car,
                            tasks,
                        )
                        .also { it.start() }
            } catch (e: IllegalStateException) {
                Log.w(TAG, "The compositor failed", e)
                release()
            }
        }
    }

    private fun areas(layout: CarLayout): List<Area> = listOf(layout.apps, layout.rail, layout.apps)

    private fun bitRate(format: VideoFormat): Int =
        EncoderSettings.bitRate(codec, format.pixelsOutsideCutouts, format.framesPerSecond)

    private fun release() {
        synchronized(displaysLock) {
            closeAll(TAG) {
                step("Releasing the car display") { display?.close() }
                step("Releasing the overlay display") { overlay?.close() }
                step("Releasing the rail display") { rail?.close() }
                step("Releasing the compositor") { compositor?.close() }
            }
            display = null
            overlay = null
            rail = null
            compositor = null
        }
    }
}
