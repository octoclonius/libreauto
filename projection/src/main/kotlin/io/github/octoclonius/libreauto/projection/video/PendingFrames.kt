package io.github.octoclonius.libreauto.projection.video

internal class PendingFrames(private val max: Int) {

    private val drawn = ArrayDeque<Long>()

    val hasRoom: Boolean
        get() = drawn.size < max

    fun onDrawn(presentationMicros: Long) {
        drawn.addLast(presentationMicros)
    }

    fun onSent(presentationMicros: Long) {
        while (drawn.firstOrNull()?.let { it <= presentationMicros } == true) drawn.removeFirst()
    }

    fun clear() {
        drawn.clear()
    }
}
