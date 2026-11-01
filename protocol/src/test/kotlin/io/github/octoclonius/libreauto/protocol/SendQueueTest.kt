package io.github.octoclonius.libreauto.protocol

import java.nio.ByteBuffer
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows

@Timeout(10, unit = TimeUnit.SECONDS)
class SendQueueTest {

    private val queue =
        SendQueue().apply {
            addChannel(CONTROL, SendPriority.DEFAULT)
            addChannel(INPUT, SendPriority.DEFAULT)
            addChannel(MEDIA, SendPriority.AUDIO)
            addChannel(VIDEO, SendPriority.VIDEO)
        }

    @Test
    fun messagesOfAChannelKeepTheirOrder() {
        queue.offer(message(MEDIA, type = 1))
        queue.offer(message(MEDIA, type = 2))
        queue.offer(message(MEDIA, type = 3))
        assertEquals(listOf(1, 2, 3), List(3) { queue.take()!!.type })
    }

    @Test
    fun defaultChannelsGoBeforeAudioAndAudioBeforeVideo() {
        queue.offer(message(VIDEO))
        queue.offer(message(MEDIA))
        queue.offer(message(CONTROL))
        assertEquals(listOf(CONTROL, MEDIA, VIDEO), List(3) { queue.take()!!.channel })
    }

    @Test
    fun aHigherPriorityMessageGoesBetweenTheFramesOfALowerOne() {
        queue.offer(message(VIDEO, frames = 3))
        val first = queue.take()!!
        queue.offer(message(CONTROL))
        val rest = List(3) { queue.take()!! }
        assertEquals(
            listOf(VIDEO, CONTROL, VIDEO, VIDEO),
            (listOf(first) + rest).map { it.channel },
        )
        assertEquals(
            listOf(FIRST, FIRST or LAST, 0, LAST),
            (listOf(first) + rest).map { it.flags },
        )
    }

    @Test
    fun channelsOfEqualPriorityTakeTurnsFrameByFrame() {
        queue.offer(message(CONTROL, frames = 2))
        queue.offer(message(INPUT, frames = 2))
        assertEquals(listOf(CONTROL, INPUT, CONTROL, INPUT), List(4) { queue.take()!!.channel })
    }

    @Test
    fun theLastMessageDropsEverythingQueuedAndEndsTheQueue() {
        queue.offer(message(MEDIA))
        queue.offer(message(VIDEO))
        queue.offer(message(CONTROL, type = 1))
        assertTrue(queue.endWith(message(CONTROL, type = BYE_BYE_RESPONSE)))

        assertFalse(queue.offer(message(CONTROL)))
        assertFalse(queue.endWith(message(CONTROL, type = BYE_BYE_RESPONSE)))
        assertEquals(BYE_BYE_RESPONSE, queue.take()!!.type)
        assertNull(queue.take())
    }

    @Test
    fun theLastMessageFollowsTheStartedMessageOfItsChannelOnly() {
        queue.offer(message(VIDEO, frames = 3))
        queue.take()
        queue.offer(message(CONTROL, frames = 2))
        queue.take()
        queue.endWith(message(CONTROL, type = BYE_BYE_RESPONSE))

        val rest = queue.take()!!
        assertEquals(CONTROL, rest.channel)
        assertEquals(LAST, rest.flags)
        assertEquals(BYE_BYE_RESPONSE, queue.take()!!.type)
        assertNull(queue.take())
    }

    @Test
    fun endingDropsEverythingQueued() {
        queue.offer(message(VIDEO, frames = 2))
        queue.take()
        queue.offer(message(CONTROL))
        queue.end()

        assertNull(queue.take())
        assertFalse(queue.offer(message(CONTROL)))
    }

    @Test
    fun endingKeepsTheLastMessage() {
        queue.endWith(message(CONTROL, type = BYE_BYE_RESPONSE))
        queue.end()
        assertEquals(BYE_BYE_RESPONSE, queue.take()!!.type)
        assertNull(queue.take())
    }

    @Test
    fun takeWaitsForAMessage() {
        val taken = CompletableFuture.supplyAsync { queue.take() }
        queue.offer(message(MEDIA))
        assertEquals(MEDIA, taken.get()!!.channel)
    }

    @Test
    fun takeWaitsUntilTheQueueEnds() {
        val taken = CompletableFuture.supplyAsync { queue.take() }
        queue.end()
        assertNull(taken.get())
    }

    @Test
    fun aChannelThatWasNotAddedIsAProgrammingError() {
        assertThrows<IllegalStateException> { queue.offer(message(UNKNOWN)) }
    }

    private fun message(channel: Int, frames: Int = 1, type: Int = 1) =
        OutgoingMessage(
            channel,
            isControl = false,
            type,
            ByteArray((frames - 1) * MAX_FRAME_SIZE),
        )

    private val OutgoingFrame.type: Int
        get() = ByteBuffer.wrap(plaintext).short.toInt()

    private companion object {
        const val CONTROL = 0
        const val INPUT = 1
        const val MEDIA = 2
        const val VIDEO = 3
        const val UNKNOWN = 9
        const val FIRST = 0x01
        const val LAST = 0x02
        const val BYE_BYE_RESPONSE = 0x0010
    }
}
