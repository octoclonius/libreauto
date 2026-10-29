package io.github.octoclonius.libreauto.projection

import androidx.datastore.core.CorruptionException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class JsonSerializerTest {

    private val serializer = JsonSerializer(ListSerializer(Int.serializer()), emptyList())

    @Test
    fun aSavedValueReadsBack() {
        assertEquals(listOf(2, 1, 3), readBack(serializer, listOf(2, 1, 3)))
    }

    @Test
    fun somethingUnreadableIsCorrupt() {
        assertThrows<CorruptionException> { read(serializer, "2/music\n1/maps") }
    }

    @Test
    fun nothingReadableIsCorrupt() {
        assertThrows<CorruptionException> { read(serializer, "") }
    }
}
