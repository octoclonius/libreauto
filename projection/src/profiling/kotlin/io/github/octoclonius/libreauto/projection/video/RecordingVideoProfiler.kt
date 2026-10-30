package io.github.octoclonius.libreauto.projection.video

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.util.Log
import io.github.octoclonius.libreauto.projection.BuildConfig
import io.github.octoclonius.libreauto.projection.display.Area
import io.github.octoclonius.libreauto.projection.display.CarLayout
import java.io.BufferedWriter
import java.io.File
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

internal fun videoProfiler(context: Context): VideoProfiler = RecordingVideoProfiler(context)

internal class RecordingVideoProfiler(private val context: Context) : VideoProfiler {

    companion object {
        private const val TAG = "VideoProfiler"
        private const val FORMAT_VERSION = 1
        private const val DIRECTORY = "profiling"
        private const val LINES_PER_FLUSH = 120
    }

    private val executor: ExecutorService = Executors.newSingleThreadExecutor {
        Thread(it, "video-profiler").apply { isDaemon = true }
    }
    private var writer: BufferedWriter? = null
    private var lines = 0
    @Volatile private var frameTap: FrameTap? = null

    override fun onStart(session: Int, layout: CarLayout, encoder: String, bitRate: Int) {
        val monotonicNanos = System.nanoTime()
        val boottimeNanos = SystemClock.elapsedRealtimeNanos()
        val wallMillis = System.currentTimeMillis()
        val format = layout.format
        val name = "video-$wallMillis-session$session"
        val directory = context.getExternalFilesDir(DIRECTORY)
        if (BuildConfig.FRAME_TAP && directory != null) {
            frameTap?.close()
            frameTap =
                FrameTap(File(directory, name), format.frameWidth, format.frameHeight) { event ->
                    record(event)
                }
        }
        val header =
            listOf(
                "# libreauto-profiling $FORMAT_VERSION",
                "# commit=${BuildConfig.GIT_COMMIT} dirty=${BuildConfig.GIT_DIRTY} " +
                    "build=${BuildConfig.BUILD_TYPE} frame_tap=${BuildConfig.FRAME_TAP}",
                "# device=${Build.MANUFACTURER}/${Build.MODEL} android=${Build.VERSION.RELEASE} " +
                    "build_id=${Build.ID}",
                "# session=$session encoder=$encoder bitrate=$bitRate",
                "# frame=${format.frameWidth}x${format.frameHeight} fps=${format.framesPerSecond} " +
                    "density=${format.density} margins=${format.margins.left}," +
                    "${format.margins.top},${format.margins.right},${format.margins.bottom}",
                "# apps=${frameArea(layout, layout.apps)} apps_density=${layout.appsDensity} " +
                    "rail=${frameArea(layout, layout.rail)}",
                "# clock monotonic_ns=$monotonicNanos boottime_ns=$boottimeNanos " +
                    "wall_ms=$wallMillis",
                "event,pts_us,time_ns,value,flags",
            )
        executor.execute {
            closeWriter()
            if (directory == null) {
                Log.w(TAG, "No external files directory for the video profile")
                return@execute
            }
            writer =
                try {
                    File(directory, "$name.csv").bufferedWriter().also { opened ->
                        header.forEach { opened.appendLine(it) }
                        opened.flush()
                    }
                } catch (e: IOException) {
                    Log.w(TAG, "Can't write the video profile $name", e)
                    null
                }
        }
    }

    override fun onDrawn(presentationNanos: Long) {
        val now = System.nanoTime()
        val presentationMicros = TimeUnit.NANOSECONDS.toMicros(presentationNanos)
        record("drawn,$presentationMicros,$now,,")
        frameTap?.onDrawn(presentationMicros)
    }

    override fun onEncoded(presentationMicros: Long, size: Int, isKeyFrame: Boolean) {
        val now = System.nanoTime()
        record("encoded,$presentationMicros,$now,$size,${if (isKeyFrame) "key" else ""}")
    }

    override fun onPermitted(presentationMicros: Long) {
        val now = System.nanoTime()
        record("permitted,$presentationMicros,$now,,")
    }

    override fun onSent(presentationMicros: Long) {
        val now = System.nanoTime()
        record("sent,$presentationMicros,$now,,")
    }

    override fun onAcknowledged(session: Int, count: Int) {
        val now = System.nanoTime()
        record("acked,,$now,$count,")
    }

    override fun onStop(session: Int) {
        frameTap?.close()
        frameTap = null
        executor.execute(::closeWriter)
    }

    private fun record(line: String) {
        executor.execute {
            val current = writer ?: return@execute
            try {
                current.appendLine(line)
                if (++lines % LINES_PER_FLUSH == 0) current.flush()
            } catch (e: IOException) {
                Log.w(TAG, "Writing the video profile failed", e)
                closeWriter()
            }
        }
    }

    private fun closeWriter() {
        try {
            writer?.close()
        } catch (e: IOException) {
            Log.w(TAG, "Closing the video profile failed", e)
        }
        writer = null
    }

    private fun frameArea(layout: CarLayout, area: Area): String {
        val format = layout.format
        val left = format.margins.left + format.visibleX(area.left)
        val top = format.margins.top + format.visibleY(area.top)
        val right = format.margins.left + format.visibleX(area.right)
        val bottom = format.margins.top + format.visibleY(area.bottom)
        return "$left,$top,$right,$bottom"
    }
}
