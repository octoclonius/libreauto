package io.github.octoclonius.libreauto.session.video

import android.media.MediaCodec
import android.util.Log
import android.view.Surface
import io.github.octoclonius.libreauto.protocol.media.MediaMessageType
import io.github.octoclonius.libreauto.protocol.media.mediaData
import java.io.Closeable
import java.io.IOException
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal class VideoEncoder(
    private val layout: CarLayout,
    private val maxUnacked: Int,
    val session: Int,
    private val send: (type: Int, body: ByteArray) -> Unit,
) : Closeable {

    companion object {
        private const val TAG = "VideoEncoder"
        private const val DEQUEUE_TIMEOUT_US = 100_000L
    }

    private val lock = ReentrantLock()
    private val permitFreed = lock.newCondition()
    private var unacked = 0
    @Volatile private var isClosed = false

    fun acknowledge(count: Int) {
        lock.withLock {
            unacked -= minOf(count, unacked)
            permitFreed.signalAll()
        }
    }

    override fun close() {
        lock.withLock {
            isClosed = true
            permitFreed.signalAll()
        }
    }

    fun run(compositor: VideoCompositor) {
        val format = layout.format
        val width = format.frameWidth
        val height = format.frameHeight
        val framesPerSecond = format.framesPerSecond
        val name = H264Encoder.name(width, height, framesPerSecond)
        if (name == null) {
            Log.w(TAG, "No H.264 encoder for ${width}x$height at $framesPerSecond fps")
            return
        }
        val encoder =
            try {
                MediaCodec.createByCodecName(name)
            } catch (e: IOException) {
                Log.w(TAG, "Can't create the H.264 encoder $name", e)
                return
            }
        var surface: Surface? = null
        try {
            encoder.configure(
                H264Encoder.format(width, height, framesPerSecond),
                null,
                null,
                MediaCodec.CONFIGURE_FLAG_ENCODE,
            )
            val input = encoder.createInputSurface()
            surface = input
            encoder.start()
            Log.i(
                TAG,
                "Encoding session $session: ${width}x$height at $framesPerSecond fps with " +
                    "$name, showing ${format.visibleWidth}x${format.visibleHeight} inside " +
                    "margins ${format.margins} from a " +
                    "${format.displayWidth}x${format.displayHeight} area at " +
                    "${format.density} dpi: apps in ${layout.apps} at ${layout.appsDensity} dpi, " +
                    "rail in ${layout.rail}",
            )
            compositor.attach(input)
            drain(encoder)
        } catch (e: IllegalStateException) {
            Log.w(TAG, "The H.264 encoder or the compositor failed", e)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "The H.264 encoder refused the format", e)
        } catch (e: IOException) {
            Log.w(TAG, "Sending video to the car failed", e)
        } finally {
            close()
            try {
                compositor.detach()
            } catch (e: IllegalStateException) {
                Log.w(TAG, "Detaching the compositor from session $session failed", e)
            }
            encoder.release()
            surface?.release()
            Log.i(TAG, "Released the encoder of session $session")
        }
    }

    private fun drain(encoder: MediaCodec) {
        val info = MediaCodec.BufferInfo()
        while (!isClosed) {
            val index = encoder.dequeueOutputBuffer(info, DEQUEUE_TIMEOUT_US)
            if (index < 0) continue
            val buffer = checkNotNull(encoder.getOutputBuffer(index))
            val data = ByteArray(buffer.remaining()).also { buffer.get(it) }
            encoder.releaseOutputBuffer(index, false)
            when {
                info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0 ->
                    send(MediaMessageType.CODEC_CONFIG, data)
                data.isNotEmpty() && takePermit() ->
                    send(MediaMessageType.DATA, mediaData(info.presentationTimeUs, data))
            }
        }
    }

    private fun takePermit(): Boolean =
        lock.withLock {
            while (unacked >= maxUnacked && !isClosed) permitFreed.await()
            if (isClosed) return false
            unacked++
            true
        }
}
