package io.github.octoclonius.libreauto.session.video

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.MediaCodec
import android.media.MediaCodecInfo.CodecCapabilities
import android.media.MediaCodecInfo.CodecProfileLevel
import android.media.MediaFormat
import android.util.Log
import android.view.Surface
import io.github.octoclonius.libreauto.protocol.MediaMessageType
import io.github.octoclonius.libreauto.protocol.VideoFormat
import io.github.octoclonius.libreauto.protocol.mediaData
import java.io.Closeable
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.concurrent.withLock

class TestPatternStream(
    private val format: VideoFormat,
    private val maxUnacked: Int,
    private val send: (type: Int, body: ByteArray) -> Unit,
) : Closeable {

    companion object {
        private const val TAG = "TestPatternStream"
        private const val BIT_RATE = 4_000_000
        private const val I_FRAME_INTERVAL_SECONDS = 1
        private const val DEQUEUE_TIMEOUT_US = 100_000L
        private const val SWEEP_SECONDS = 2
        private val BAR_COLORS =
            intArrayOf(
                Color.WHITE,
                Color.YELLOW,
                Color.CYAN,
                Color.GREEN,
                Color.MAGENTA,
                Color.RED,
                Color.BLUE,
            )
    }

    private val lock = ReentrantLock()
    private val permitFreed = lock.newCondition()
    private var unacked = 0
    @Volatile private var isClosed = false
    private val paint = Paint()

    fun start() {
        thread(isDaemon = true, name = "video-encoder") { encode() }
    }

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

    private fun encode() {
        val encoder =
            try {
                MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            } catch (e: IOException) {
                Log.w(TAG, "No H.264 encoder", e)
                close()
                return
            }
        var surface: Surface? = null
        var drawing: Thread? = null
        try {
            encoder.configure(mediaFormat(), null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val input = encoder.createInputSurface()
            surface = input
            encoder.start()
            Log.i(
                TAG,
                "Encoding ${format.width}x${format.height} at ${format.framesPerSecond} fps",
            )
            drawing = thread(isDaemon = true, name = "video-pattern") { draw(input) }
            drain(encoder)
        } catch (e: IllegalStateException) {
            Log.w(TAG, "The H.264 encoder failed", e)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "The H.264 encoder refused the format", e)
        } catch (e: IOException) {
            Log.w(TAG, "Sending video to the car failed", e)
        } finally {
            close()
            encoder.release()
            drawing?.join()
            surface?.release()
        }
    }

    private fun mediaFormat(): MediaFormat =
        MediaFormat.createVideoFormat(
                MediaFormat.MIMETYPE_VIDEO_AVC,
                format.width,
                format.height,
            )
            .apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_PROFILE, CodecProfileLevel.AVCProfileBaseline)
                setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
                setInteger(MediaFormat.KEY_FRAME_RATE, format.framesPerSecond)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL_SECONDS)
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

    private fun draw(surface: Surface) {
        val frameNanos = TimeUnit.SECONDS.toNanos(1) / format.framesPerSecond
        var frame = 0
        var next = System.nanoTime()
        try {
            while (!isClosed) {
                val canvas = surface.lockHardwareCanvas()
                try {
                    drawPattern(canvas, frame++)
                } finally {
                    surface.unlockCanvasAndPost(canvas)
                }
                next += frameNanos
                val wait = next - System.nanoTime()
                if (wait > 0) TimeUnit.NANOSECONDS.sleep(wait) else next = System.nanoTime()
            }
        } catch (e: IllegalStateException) {
            if (!isClosed) {
                Log.w(TAG, "Drawing the test pattern failed", e)
                close()
            }
        }
    }

    private fun drawPattern(canvas: Canvas, frame: Int) {
        val width = canvas.width.toFloat()
        val height = canvas.height.toFloat()
        val barWidth = width / BAR_COLORS.size
        BAR_COLORS.forEachIndexed { index, color ->
            paint.color = color
            canvas.drawRect(index * barWidth, 0f, (index + 1) * barWidth, height, paint)
        }
        val sweepFrames = format.framesPerSecond * SWEEP_SECONDS
        val movingBarWidth = barWidth / 2
        val x = (width + movingBarWidth) * (frame % sweepFrames) / sweepFrames - movingBarWidth
        paint.color = Color.BLACK
        canvas.drawRect(x, 0f, x + movingBarWidth, height, paint)
    }
}
