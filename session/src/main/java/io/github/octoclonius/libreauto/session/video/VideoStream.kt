package io.github.octoclonius.libreauto.session.video

import android.content.Context
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.hardware.display.VirtualDisplayConfig
import android.media.MediaCodec
import android.media.MediaCodecInfo.CodecCapabilities
import android.media.MediaCodecInfo.CodecProfileLevel
import android.media.MediaFormat
import android.util.Log
import android.view.Surface
import io.github.octoclonius.libreauto.protocol.media.MediaMessageType
import io.github.octoclonius.libreauto.protocol.media.VideoFormat
import io.github.octoclonius.libreauto.protocol.media.mediaData
import java.io.Closeable
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.concurrent.withLock

internal class VideoStream(
    private val context: Context,
    private val format: VideoFormat,
    private val maxUnacked: Int,
    private val send: (type: Int, body: ByteArray) -> Unit,
) : Closeable {

    companion object {
        private const val TAG = "VideoStream"
        private const val DISPLAY_NAME = "LibreAuto"
        private const val BIT_RATE = 4_000_000
        private const val I_FRAME_INTERVAL_SECONDS = 1
        private const val DEQUEUE_TIMEOUT_US = 100_000L
    }

    private val lock = ReentrantLock()
    private val permitFreed = lock.newCondition()
    private var unacked = 0
    @Volatile private var isClosed = false

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
        var display: VirtualDisplay? = null
        try {
            encoder.configure(mediaFormat(), null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val input = encoder.createInputSurface()
            surface = input
            encoder.start()
            Log.i(
                TAG,
                "Encoding ${format.width}x${format.height} at ${format.framesPerSecond} fps " +
                    "and ${format.density} dpi",
            )
            val created = createDisplay(input)
            if (created == null) {
                Log.w(TAG, "Creating the car display failed")
                return
            }
            display = created
            CarDisplay.onCreated(created.display.displayId)
            drain(encoder)
        } catch (e: IllegalStateException) {
            Log.w(TAG, "The H.264 encoder failed", e)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "The H.264 encoder refused the format", e)
        } catch (e: IOException) {
            Log.w(TAG, "Sending video to the car failed", e)
        } finally {
            close()
            if (display != null) {
                CarDisplay.onReleased(display.display.displayId)
                display.release()
            }
            encoder.release()
            surface?.release()
        }
    }

    private fun createDisplay(surface: Surface): VirtualDisplay? =
        context
            .getSystemService(DisplayManager::class.java)
            .createVirtualDisplay(
                VirtualDisplayConfig.Builder(
                        DISPLAY_NAME,
                        format.width,
                        format.height,
                        format.density,
                    )
                    .setSurface(surface)
                    .setRequestedRefreshRate(format.framesPerSecond.toFloat())
                    .build()
            )

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
                setLong(
                    MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER,
                    TimeUnit.SECONDS.toMicros(1) / format.framesPerSecond,
                )
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
