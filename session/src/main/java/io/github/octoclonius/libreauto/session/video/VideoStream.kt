package io.github.octoclonius.libreauto.session.video

import android.content.Context
import android.util.Log
import io.github.octoclonius.libreauto.protocol.media.MediaMessageType
import io.github.octoclonius.libreauto.protocol.media.mediaStart
import java.io.Closeable
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

internal class VideoStream(
    private val context: Context,
    private val layout: CarLayout,
    private val configIndex: Int,
    private val decoderDepth: Int,
    private val maxUnacked: Int,
    private val send: (type: Int, body: ByteArray) -> Unit,
) : Closeable {

    companion object {
        private const val TAG = "VideoStream"
        private const val FIRST_SESSION = 0
    }

    private val executor: ExecutorService =
        Executors.newSingleThreadExecutor { Thread(it, "video-stream").apply { isDaemon = true } }
    private var session = FIRST_SESSION
    @Volatile private var encoder: VideoEncoder? = null
    private var compositor: VideoCompositor? = null
    private var rail: RailVirtualDisplay? = null
    private var display: PrivilegedDisplay? = null

    fun start() {
        executor.execute(::setUp)
        resume()
    }

    fun resume() {
        val started = VideoEncoder(layout, maxUnacked, session, send)
        encoder = started
        executor.execute {
            val current = compositor
            if (current == null) {
                Log.w(TAG, "No video session ${started.session} without a compositor")
                return@execute
            }
            if (!sendOrLog(MediaMessageType.START, mediaStart(started.session, configIndex))) {
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
            if (sendOrLog(MediaMessageType.STOP, ByteArray(0))) {
                Log.i(TAG, "Stopped video session ${stopped.session}")
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
        try {
            val created =
                VideoCompositor(layout.format, listOf(layout.apps, layout.rail), decoderDepth) {
                    encoder?.close()
                }
            compositor = created
            val (appsInput, railInput) = created.inputs
            rail = RailVirtualDisplay(context, railInput, layout)
            display = PrivilegedDisplay(appsInput, layout).also { it.start() }
        } catch (e: IllegalStateException) {
            Log.w(TAG, "The compositor failed", e)
            release()
        }
    }

    private fun sendOrLog(type: Int, body: ByteArray): Boolean =
        try {
            send(type, body)
            true
        } catch (e: IOException) {
            Log.w(TAG, "Sending video message $type to the car failed", e)
            false
        }

    private fun release() {
        display?.close()
        rail?.close()
        compositor?.close()
        display = null
        rail = null
        compositor = null
    }
}
