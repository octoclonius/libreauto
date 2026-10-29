package io.github.octoclonius.libreauto.session.certificate

import androidx.datastore.core.CorruptionException
import io.github.octoclonius.libreauto.session.JsonSerializer
import io.github.octoclonius.libreauto.session.certificate.CertificateRepository.Choice
import io.github.octoclonius.libreauto.session.read
import io.github.octoclonius.libreauto.session.readBack
import kotlinx.serialization.builtins.nullable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class CertificateChoiceTest {

    private val serializer = JsonSerializer(Choice.serializer().nullable, null)

    @Test
    fun aSavedChoiceReadsBack() {
        val choice = Choice("com.example.provider", byteArrayOf(1, -2, 3))
        assertEquals(choice, readBack(serializer, choice))
    }

    @Test
    fun noChoiceReadsBackAsNone() {
        assertNull(readBack(serializer, null))
    }

    @Test
    fun anUnreadableChoiceIsCorrupt() {
        assertThrows<CorruptionException> {
            read(serializer, """{"packageName":"com.example.provider"}""")
        }
        assertThrows<CorruptionException> { read(serializer, "not json") }
    }
}
