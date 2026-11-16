package io.github.octoclonius.libreauto.projection.video

internal class OverlaySwitch(count: Int) {

    companion object {
        const val BLAST_TIMEOUT_DURATION = 5_000L
    }

    private class Overlay {
        var pictureNanos: Long? = null
        var expectedContent: Int? = null
        var drawnContent: Int? = null
        var drawnLatchNanos = 0L

        val isCurrent: Boolean
            get() {
                val picture = pictureNanos ?: return false
                val expected = expectedContent ?: return true
                return drawnContent == expected && picture >= drawnLatchNanos
            }
    }

    private val overlays = List(count) { Overlay() }
    private var pending: Set<Int>? = null

    var deadlineMillis: Long? = null
        private set

    var shown: Set<Int> = emptySet()
        private set

    fun show(next: Set<Int>, nowMillis: Long): Boolean {
        if (pending == null) deadlineMillis = nowMillis + BLAST_TIMEOUT_DURATION
        pending = next
        return apply(nowMillis)
    }

    fun expect(overlay: Int, contentId: Int) {
        overlays[overlay].expectedContent = contentId
    }

    fun reportDrawn(overlay: Int, contentId: Int, latchNanos: Long, nowMillis: Long): Boolean {
        overlays[overlay].run {
            drawnContent = contentId
            drawnLatchNanos = latchNanos
        }
        return apply(nowMillis)
    }

    fun onPicture(overlay: Int, timestampNanos: Long, nowMillis: Long): Boolean {
        overlays[overlay].pictureNanos = timestampNanos
        return apply(nowMillis)
    }

    fun onDeadline(nowMillis: Long): Boolean = apply(nowMillis)

    private fun apply(nowMillis: Long): Boolean {
        val next = pending ?: return false
        val isReady = next.all { it in shown || overlays[it].isCurrent }
        if (!isReady && nowMillis < checkNotNull(deadlineMillis)) return false
        pending = null
        deadlineMillis = null
        if (next == shown) return false
        shown = next
        return true
    }
}
