package io.github.octoclonius.libreauto.session.audio

import android.os.ParcelFileDescriptor
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.util.Log
import io.github.octoclonius.libreauto.protocol.ChannelMessage
import io.github.octoclonius.libreauto.protocol.MessageStatus
import io.github.octoclonius.libreauto.protocol.control.MessageType
import io.github.octoclonius.libreauto.protocol.media.MediaMessageType
import io.github.octoclonius.libreauto.protocol.media.PcmConfig
import io.github.octoclonius.libreauto.protocol.media.mediaAck
import io.github.octoclonius.libreauto.protocol.media.mediaDataPayload
import io.github.octoclonius.libreauto.protocol.media.microphoneRequest
import io.github.octoclonius.libreauto.protocol.media.parseMicrophoneResponse
import io.github.octoclonius.libreauto.session.CarChannel
import io.github.octoclonius.libreauto.session.ChannelSender
import io.github.octoclonius.libreauto.session.isChannelOpened

internal class MicrophoneChannel(
    private val sender: ChannelSender,
    val config: PcmConfig,
    private val onSettled: () -> Unit,
) : CarChannel {

    companion object {
        private const val TAG = "MicrophoneChannel"
        private const val MAX_UNACKED = 2
        private const val PIPE_ATOMIC_BYTES = 4096
    }

    private val lock = Any()
    private var isOpening = true
    private var isUsable = false
    private var isOpen = false
    private var session = 0
    private var droppedBytes = 0L
    private var output: ParcelFileDescriptor? = null

    override val channel: Int
        get() = sender.channel

    val isSettled: Boolean
        get() = synchronized(lock) { !isOpening }

    val isReady: Boolean
        get() = synchronized(lock) { isUsable }

    override fun open() {
        Log.i(TAG, "Opening the microphone channel $channel")
        sender.sendOpenRequest()
    }

    override fun onMessage(message: ChannelMessage): Boolean {
        when (message.type) {
            MessageType.CHANNEL_OPEN_RESPONSE -> onOpenResponse(message)
            MediaMessageType.MICROPHONE_RESPONSE -> onMicrophoneResponse(message)
            MediaMessageType.DATA -> onData(message)
            else -> return false
        }
        return true
    }

    fun inject(pipe: ParcelFileDescriptor) {
        Os.fcntlInt(pipe.fileDescriptor, OsConstants.F_SETFL, OsConstants.O_NONBLOCK)
        val previous =
            synchronized(lock) {
                val previous = output
                output = pipe
                previous
            }
        previous?.close()
    }

    fun setRecording(isRecording: Boolean) {
        val dropped =
            synchronized(lock) {
                if (!isUsable || isOpen == isRecording) return
                isOpen = isRecording
                droppedBytes.also { droppedBytes = 0 }
            }
        sender.send(
            MediaMessageType.MICROPHONE_REQUEST,
            microphoneRequest(isRecording, MAX_UNACKED),
        )
        if (isRecording) {
            Log.i(TAG, "Opening the car's microphone for a recording")
        } else {
            Log.i(TAG, "Closing the car's microphone, $dropped bytes dropped while it was open")
        }
    }

    override fun close() {
        val closed =
            synchronized(lock) {
                isUsable = false
                isOpen = false
                output.also { output = null }
            }
        closed?.close()
    }

    private fun onOpenResponse(message: ChannelMessage) {
        val isOpened = isChannelOpened(TAG, message, "microphone")
        synchronized(lock) {
            if (!isOpening) return
            isOpening = false
            isUsable = isOpened
        }
        onSettled()
    }

    private fun onMicrophoneResponse(message: ChannelMessage) {
        val response = parseMicrophoneResponse(message.body)
        val isOpen =
            synchronized(lock) {
                session = response.session ?: 0
                isOpen
            }
        if (response.status == MessageStatus.SUCCESS) {
            Log.i(TAG, "The car answered the microphone request, session ${response.session}")
        } else if (isOpen) {
            Log.w(
                TAG,
                "The car refused to open its microphone: status ${response.status}; " +
                    "recordings get silence",
            )
        } else {
            Log.i(TAG, "The car answered closing its microphone with status ${response.status}")
        }
    }

    private fun onData(message: ChannelMessage) {
        val pcm = mediaDataPayload(message.body)
        val (ackSession, out) = synchronized(lock) { session to output.takeIf { isOpen } }
        sender.send(MediaMessageType.ACK, mediaAck(ackSession, 1))
        if (out != null) write(out, pcm)
    }

    private fun write(out: ParcelFileDescriptor, pcm: ByteArray) {
        var offset = 0
        try {
            while (offset < pcm.size) {
                val count = minOf(pcm.size - offset, PIPE_ATOMIC_BYTES)
                offset += Os.write(out.fileDescriptor, pcm, offset, count)
            }
        } catch (e: ErrnoException) {
            if (e.errno == OsConstants.EAGAIN) {
                synchronized(lock) { droppedBytes += pcm.size - offset }
                return
            }
            Log.w(TAG, "Nothing injects the car's microphone anymore", e)
            val isCurrent =
                synchronized(lock) {
                    (output === out).also { if (it) output = null }
                }
            if (isCurrent) out.close()
        }
    }
}
