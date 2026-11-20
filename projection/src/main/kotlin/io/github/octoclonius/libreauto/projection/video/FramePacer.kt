package io.github.octoclonius.libreauto.projection.video

import java.util.concurrent.TimeUnit

internal class FramePacer(framesPerSecond: Int) {

    companion object {
        const val FRAME_TIME_DEVIATION = 0.2
    }

    private val intervalNanos = TimeUnit.SECONDS.toNanos(1) / framesPerSecond
    private val deviationNanos = (intervalNanos * FRAME_TIME_DEVIATION).toLong()
    private var dueNanos: Long? = null

    fun isDue(vsyncNanos: Long): Boolean {
        val due = dueNanos ?: return true
        return vsyncNanos >= due - deviationNanos
    }

    fun onDrawn(vsyncNanos: Long) {
        val next = (dueNanos ?: vsyncNanos) + intervalNanos
        dueNanos = if (next - deviationNanos <= vsyncNanos) vsyncNanos + intervalNanos else next
    }

    fun delayMillis(nowNanos: Long): Long {
        val due = dueNanos ?: return 0
        return TimeUnit.NANOSECONDS.toMillis(due - deviationNanos - nowNanos).coerceAtLeast(0)
    }

    fun reset() {
        dueNanos = null
    }
}
