package io.github.octoclonius.libreauto.projection.channel

import io.github.octoclonius.libreauto.protocol.FrameWriter
import java.io.ByteArrayOutputStream
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CarLinkTest {

    private companion object {
        const val CONTROL_CHANNEL = 0
        const val MEDIA_CHANNEL = 4
        const val BYE_BYE_RESPONSE = 0x0010
        const val MEDIA_DATA = 0x0000
    }

    private val output = ByteArrayOutputStream()
    private val link = CarLink(FrameWriter(output)) { it }

    @Test
    fun sendsUntilTheLastMessage() {
        assertTrue(link.send(MEDIA_CHANNEL, isControl = false, MEDIA_DATA, byteArrayOf(1)))
        assertTrue(
            link.sendLast(CONTROL_CHANNEL, isControl = false, BYE_BYE_RESPONSE, ByteArray(0))
        )
        val written = output.size()

        assertFalse(link.send(MEDIA_CHANNEL, isControl = false, MEDIA_DATA, byteArrayOf(2)))
        assertFalse(
            link.sendLast(CONTROL_CHANNEL, isControl = false, BYE_BYE_RESPONSE, ByteArray(0))
        )
        assertEquals(written, output.size())
        assertFalse(link.isLost)
    }
}
