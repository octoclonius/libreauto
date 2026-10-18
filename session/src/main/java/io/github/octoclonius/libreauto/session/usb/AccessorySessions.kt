package io.github.octoclonius.libreauto.session.usb

import java.util.concurrent.Executor

internal interface AccessoryLink {
    fun run(): Boolean

    fun close()
}

internal class AccessorySessions<A>(
    private val executor: Executor,
    private val stop: (startId: Int) -> Unit,
    private val onLinkLost: () -> Unit,
) {
    private val lock = Any()
    private var current: Session<A>? = null

    fun start(startId: Int, accessory: A, open: (A) -> AccessoryLink?) {
        val session = Session(startId, accessory)
        synchronized(lock) {
            current?.close()
            current = session
        }
        executor.execute { run(session, open) }
    }

    fun detach(accessory: A) {
        synchronized(lock) { current?.takeIf { it.accessory == accessory }?.close() }
    }

    fun close() {
        synchronized(lock) {
            current?.close()
            current = null
        }
    }

    private fun run(session: Session<A>, open: (A) -> AccessoryLink?) {
        try {
            if (synchronized(lock) { session.isClosed }) return
            val link = open(session.accessory) ?: return
            val isOpen =
                synchronized(lock) {
                    if (!session.isClosed) session.link = link
                    !session.isClosed
                }
            val isLost =
                try {
                    isOpen && link.run()
                } finally {
                    link.close()
                }
            if (isLost && synchronized(lock) { !session.isClosed }) onLinkLost()
        } finally {
            synchronized(lock) { if (current === session) current = null }
            stop(session.startId)
        }
    }

    private class Session<A>(val startId: Int, val accessory: A) {
        var isClosed = false
        var link: AccessoryLink? = null

        fun close() {
            isClosed = true
            link?.close()
        }
    }
}
