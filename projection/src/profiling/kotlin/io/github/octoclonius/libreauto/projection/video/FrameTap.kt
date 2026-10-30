package io.github.octoclonius.libreauto.projection.video

import android.opengl.GLES20
import android.util.Log
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException

internal class FrameTap(
    private val directory: File,
    private val width: Int,
    private val height: Int,
    private val record: (String) -> Unit,
) : Closeable {

    companion object {
        private const val TAG = "FrameTap"
        private const val BURST_MICROS = 1_000_000L
        private const val PERIOD_MICROS = 10_000_000L
        private const val MAX_BUFFERS = 6
        private const val BYTES_PER_PIXEL = 4
    }

    private val writer: ExecutorService = Executors.newSingleThreadExecutor {
        Thread(it, "frame-tap").apply { isDaemon = true }
    }
    private val free = ConcurrentLinkedQueue<ByteBuffer>()
    private var allocated = 0
    private var firstMicros: Long? = null

    init {
        directory.mkdirs()
    }

    fun onDrawn(presentationMicros: Long) {
        val first = firstMicros ?: presentationMicros.also { firstMicros = it }
        if ((presentationMicros - first) % PERIOD_MICROS >= BURST_MICROS) return
        val buffer = free.poll() ?: allocate()
        if (buffer == null) {
            record("tap_skipped,$presentationMicros,${System.nanoTime()},,")
            return
        }
        val start = System.nanoTime()
        buffer.clear()
        GLES20.glReadPixels(0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buffer)
        val end = System.nanoTime()
        record("tapped,$presentationMicros,$end,${end - start},")
        try {
            writer.execute { save(presentationMicros, buffer) }
        } catch (_: RejectedExecutionException) {
            free.offer(buffer)
        }
    }

    private fun save(presentationMicros: Long, buffer: ByteBuffer) {
        try {
            FileOutputStream(File(directory, "$presentationMicros.rgba")).use {
                buffer.rewind()
                it.channel.write(buffer)
            }
        } catch (e: IOException) {
            Log.w(TAG, "Saving frame $presentationMicros failed", e)
        }
        free.offer(buffer)
    }

    private fun allocate(): ByteBuffer? {
        if (allocated >= MAX_BUFFERS) return null
        allocated++
        return ByteBuffer.allocateDirect(width * height * BYTES_PER_PIXEL)
    }

    override fun close() {
        writer.shutdown()
    }
}
