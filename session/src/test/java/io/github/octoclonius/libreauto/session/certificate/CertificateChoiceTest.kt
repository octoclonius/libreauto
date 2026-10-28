package io.github.octoclonius.libreauto.session.certificate

import io.github.octoclonius.libreauto.session.certificate.CertificateProvider.Choice
import io.github.octoclonius.libreauto.session.decodeSaved
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CertificateChoiceTest {

    @Test
    fun aSavedChoiceReadsBack() {
        val choice = Choice("com.example.provider", byteArrayOf(1, -2, 3))
        assertEquals(choice, decodeSaved<Choice>(Json.encodeToString(choice)))
    }

    @Test
    fun anUnreadableChoiceReadsAsNone() {
        assertNull(decodeSaved<Choice>("""{"packageName":"com.example.provider"}"""))
        assertNull(decodeSaved<Choice>("not json"))
    }
}
