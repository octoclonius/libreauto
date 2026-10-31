package io.github.octoclonius.libreauto.projection.video

import android.media.MediaCodec
import android.os.Bundle
import android.util.Log
import android.view.Surface
import io.github.octoclonius.libreauto.projection.display.CarLayout
import io.github.octoclonius.libreauto.projection.media.encoderName
import io.github.octoclonius.libreauto.protocol.media.MediaMessageType
import io.github.octoclonius.libreauto.protocol.media.VideoCodec
import io.github.octoclonius.libreauto.protocol.media.VideoMode
import io.github.octoclonius.libreauto.protocol.media.mediaData
import java.io.Closeable
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal class VideoEncoder(
    private val layout: CarLayout,
    private val codec: VideoCodec,
    private val maxUnacked: Int,
    val session: Int,
    private val send: (type: Int, body: ByteArray) -> Boolean,
    private val profiler: VideoProfiler,
) : Closeable {

    companion object {
        private const val TAG = "VideoEncoder"
        private const val DEQUEUE_TIMEOUT_US = 100_000L
    }

    private val lock = ReentrantLock()
    private val permitFreed = lock.newCondition()
    private var unacked = 0
    private val sentActions = FrameSentActions()
    @Volatile private var isClosed = false
    @Volatile private var mediaCodec: MediaCodec? = null

    fun acknowledge(count: Int) {
        lock.withLock {
            unacked -= minOf(count, unacked)
            permitFreed.signalAll()
        }
        profiler.onAcknowledged(session, count)
    }

    override fun close() {
        lock.withLock {
            isClosed = true
            permitFreed.signalAll()
        }
        sentActions.close()
    }

    fun afterFrame(presentationNanos: Long, action: () -> Unit) {
        sentActions.afterFrame(TimeUnit.NANOSECONDS.toMicros(presentationNanos), action)
    }

    fun setBitRate(bitRate: Int) {
        val current = mediaCodec ?: return
        try {
            current.setParameters(
                Bundle().apply { putInt(MediaCodec.PARAMETER_KEY_VIDEO_BITRATE, bitRate) }
            )
            Log.i(TAG, "Changed the bit rate of session $session to $bitRate bit/s")
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Changing the bit rate of session $session failed", e)
        }
    }

    fun run(compositor: VideoCompositor) {
        val format = layout.format
        val width = format.frameWidth
        val height = format.frameHeight
        val framesPerSecond = format.framesPerSecond
        val bitRate = EncoderSettings.bitRate(codec, format.pixelsOutsideCutouts, framesPerSecond)
        val encoderFormat =
            EncoderSettings.format(VideoMode(codec, width, height, framesPerSecond), bitRate)
        val name = encoderName(encoderFormat)
        if (name == null) {
            Log.w(TAG, "No $codec encoder for ${width}x$height at $framesPerSecond fps")
            return
        }
        val encoder =
            try {
                MediaCodec.createByCodecName(name)
            } catch (e: IOException) {
                Log.w(TAG, "Can't create the $codec encoder $name", e)
                return
            }
        var surface: Surface? = null
        try {
            encoder.configure(encoderFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val input = encoder.createInputSurface()
            surface = input
            encoder.start()
            mediaCodec = encoder
            Log.i(
                TAG,
                "Encoding session $session in $codec: ${width}x$height at $framesPerSecond fps and " +
                    "$bitRate bit/s with $name, showing " +
                    "${format.visibleWidth}x${format.visibleHeight} inside " +
                    "margins ${format.margins} from a " +
                    "${format.displayWidth}x${format.displayHeight} area at " +
                    "${format.density} dpi: apps in ${layout.apps} at ${layout.appsDensity} dpi, " +
                    "rail in ${layout.rail}",
            )
            profiler.onStart(session, layout, name, bitRate)
            compositor.attach(input)
            drain(encoder, compositor)
        } catch (e: IllegalStateException) {
            Log.w(TAG, "The $codec encoder or the compositor failed", e)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "The $codec encoder refused the format", e)
        } finally {
            close()
            profiler.onStop(session)
            mediaCodec = null
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

    private fun drain(encoder: MediaCodec, compositor: VideoCompositor) {
        val info = MediaCodec.BufferInfo()
        while (!isClosed) {
            val index = encoder.dequeueOutputBuffer(info, DEQUEUE_TIMEOUT_US)
            if (index < 0) continue
            val buffer = checkNotNull(encoder.getOutputBuffer(index))
            val data = ByteArray(buffer.remaining()).also { buffer.get(it) }
            encoder.releaseOutputBuffer(index, false)
            when {
                info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0 ->
                    if (!send(MediaMessageType.CODEC_CONFIG, data)) return
                data.isNotEmpty() -> {
                    val isKeyFrame = info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME != 0
                    profiler.onEncoded(info.presentationTimeUs, data.size, isKeyFrame)
                    if (!takePermit()) continue
                    profiler.onPermitted(info.presentationTimeUs)
                    if (!send(MediaMessageType.DATA, mediaData(info.presentationTimeUs, data))) {
                        return
                    }
                    profiler.onSent(info.presentationTimeUs)
                    sentActions.onFrameSent(info.presentationTimeUs)
                    compositor.onFrameSent(info.presentationTimeUs)
                }
                else -> compositor.onFrameSent(info.presentationTimeUs)
            }
        }
    }

    private fun takePermit(): Boolean = lock.withLock {
        while (unacked >= maxUnacked && !isClosed) permitFreed.await()
        if (isClosed) return false
        unacked++
        true
    }
}
