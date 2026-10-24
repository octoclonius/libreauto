package io.github.octoclonius.libreauto.session.video

internal class FrameSentActions {

    private val lock = Any()
    private val pending = ArrayDeque<Pair<Long, () -> Unit>>()
    private var lastSentMicros: Long? = null
    private var isClosed = false

    fun afterFrame(presentationMicros: Long, action: () -> Unit) {
        synchronized(lock) {
            val sent = lastSentMicros
            if (isClosed || (sent != null && sent >= presentationMicros)) {
                action()
            } else {
                pending.addLast(presentationMicros to action)
            }
        }
    }

    fun onFrameSent(presentationMicros: Long) {
        synchronized(lock) {
            lastSentMicros = presentationMicros
            while (pending.firstOrNull()?.let { it.first <= presentationMicros } == true) {
                pending.removeFirst().second()
            }
        }
    }

    fun close() {
        synchronized(lock) {
            isClosed = true
            while (pending.isNotEmpty()) pending.removeFirst().second()
        }
    }
}
