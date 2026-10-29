package io.github.octoclonius.libreauto.projection

import java.io.IOException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DescribeTest {

    @Test
    fun anExceptionIsItsTypeAndMessage() {
        assertEquals("IOException: Broken pipe", describe(IOException("Broken pipe")))
    }

    @Test
    fun anExceptionWithoutAMessageIsItsType() {
        assertEquals("IllegalStateException", describe(IllegalStateException()))
    }

    @Test
    fun aLongDescriptionIsCut() {
        val description = describe(IOException("x".repeat(MAX_DESCRIPTION_CHARS)))
        assertEquals(MAX_DESCRIPTION_CHARS, description.length)
        assertEquals("IOException: x", description.take(14))
    }
}
