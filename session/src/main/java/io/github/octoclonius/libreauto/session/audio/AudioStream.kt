package io.github.octoclonius.libreauto.session.audio

import android.os.ParcelFileDescriptor
import android.util.Log
import io.github.octoclonius.libreauto.protocol.media.MediaMessageType
import io.github.octoclonius.libreauto.protocol.media.PcmConfig
import io.github.octoclonius.libreauto.protocol.media.mediaData
import io.github.octoclonius.libreauto.protocol.media.mediaStart
import io.github.octoclonius.libreauto.protocol.media.pcmFrameBytes
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.concurrent.withLock

internal class AudioStream(
    private val config: PcmConfig,
    private val frameSamples: Int,
    private val maxUnacked: Int,
    private val send: (type: Int, body: ByteArray) -> Unit,
    private val onStartedOrStopped: () -> Unit,
) : Closeable {

    companion object {
        private const val TAG = "AudioStream"
    }

    private val frameNanos = TimeUnit.SECONDS.toNanos(frameSamples.toLong()) / config.sampleRate

    private val lock = ReentrantLock()
    private val acked = lock.newCondition()
    private var isActive = false
    private var isTailing = false
    private var isAllowed = false
    private var isStarted = false
    private var session = 0
    private var unacked = 0
    private var carQueueEndNanos = 0L
    private val drainTimer =
        Executors.newSingleThreadScheduledExecutor { Thread(it, "audio-stream-drain") }
    private var input: InputStream? = null
    private var reader: Thread? = null
    @Volatile private var isClosed = false

    val isIdle: Boolean
        get() = lock.withLock { !isStarted }

    fun read(pipe: ParcelFileDescriptor) {
        stopReading()
        val stream = ParcelFileDescriptor.AutoCloseInputStream(pipe)
        input = stream
        reader = thread(isDaemon = true, name = "audio-stream") { readFrames(stream) }
    }

    fun setActive(isActive: Boolean) {
        lock.withLock {
            this.isActive = isActive
            isTailing = !isActive && isStarted
        }
    }

    fun setAllowed(isAllowed: Boolean) {
        val stopped =
            lock.withLock {
                this.isAllowed = isAllowed
                if (!isAllowed) isTailing = false
                if (!isAllowed && isStarted) {
                    stopLocked()
                    true
                } else {
                    false
                }
            }
        if (stopped) onStartedOrStopped()
    }

    fun onAck(ackedSession: Int?, count: Int) {
        lock.withLock {
            if (ackedSession != null && ackedSession != session) return
            unacked -= minOf(count, unacked)
            acked.signalAll()
        }
    }

    override fun close() {
        isClosed = true
        drainTimer.shutdownNow()
        stopReading()
        lock.withLock {
            acked.signalAll()
            if (isStarted) stopLocked()
        }
    }

    private fun stopReading() {
        input?.close()
        reader?.join()
        input = null
        reader = null
    }

    private fun readFrames(input: InputStream) {
        val frameBytes = pcmFrameBytes(config, frameSamples)
        try {
            while (!isClosed) {
                val frame = ByteArray(frameBytes)
                if (input.readNBytes(frame, 0, frameBytes) < frameBytes) break
                onFrame(frame, System.nanoTime())
            }
        } catch (e: IOException) {
            if (!isClosed) Log.w(TAG, "Reading the diverted audio failed", e)
        }
        endTail()
    }

    private fun onFrame(frame: ByteArray, capturedNanos: Long) {
        if (isTailingWith(frame)) {
            endTail()
            return
        }
        val started =
            lock.withLock {
                if (!isAllowed || (!isActive && !isTailing)) return
                val starting = !isStarted
                if (starting) startLocked()
                sendLocked(frame, capturedNanos)
                starting
            }
        if (started) onStartedOrStopped()
    }

    private fun isTailingWith(frame: ByteArray): Boolean =
        lock.withLock { isTailing } && frame.all { it == 0.toByte() }

    private fun endTail() {
        val stopped =
            lock.withLock {
                isTailing = false
                stopWhenCarQueueEmptyLocked()
            }
        if (stopped) onStartedOrStopped()
    }

    private fun stopWhenCarQueueEmptyLocked(): Boolean {
        if (!isStarted || isActive || isTailing) return false
        val remainingNanos = carQueueEndNanos - System.nanoTime()
        if (remainingNanos > 0) {
            val drainingSession = session
            drainTimer.schedule(
                Runnable { onCarQueueEmpty(drainingSession) },
                remainingNanos,
                TimeUnit.NANOSECONDS,
            )
            return false
        }
        stopLocked()
        return true
    }

    private fun onCarQueueEmpty(drainingSession: Int) {
        val stopped =
            lock.withLock { drainingSession == session && stopWhenCarQueueEmptyLocked() }
        if (stopped) onStartedOrStopped()
    }

    private fun startLocked() {
        isStarted = true
        session++
        unacked = 0
        carQueueEndNanos = 0L
        send(MediaMessageType.START, mediaStart(session, config.index))
        Log.i(TAG, "Started the stream, session $session")
    }

    private fun stopLocked() {
        isStarted = false
        unacked = 0
        send(MediaMessageType.STOP, ByteArray(0))
        Log.i(TAG, "Stopped the stream, session $session")
    }

    private fun sendLocked(pcm: ByteArray, capturedNanos: Long) {
        var remainingNanos = frameNanos
        while (unacked >= maxUnacked && isStarted && !isClosed) {
            if (remainingNanos <= 0) return
            remainingNanos = acked.awaitNanos(remainingNanos)
        }
        if (!isStarted || isClosed) return
        unacked++
        send(MediaMessageType.DATA, mediaData(TimeUnit.NANOSECONDS.toMicros(capturedNanos), pcm))
        carQueueEndNanos = maxOf(carQueueEndNanos, System.nanoTime()) + frameNanos
    }
}
