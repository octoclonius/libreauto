package io.github.octoclonius.libreauto.session.certificate

import io.github.octoclonius.libreauto.session.certificate.CertificateRepository.FetchResult
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

class CertificateAnswerTest {

    @Test
    fun aCertificateWithItsKeyIsAccepted() {
        val certificate = fixture("phone.crt")
        val key = fixture("phone.key")
        val result = checkAnswer(null, certificate, key)
        val success = assertInstanceOf(FetchResult.Success::class.java, result)
        assertArrayEquals(certificate, success.certificate.encoded)
        assertArrayEquals(key, success.key.encoded)
    }

    @Test
    fun theProvidersErrorIsShownEvenWithACertificate() {
        val result = checkAnswer("Not set up yet", fixture("phone.crt"), fixture("phone.key"))
        assertEquals("Not set up yet", error(result))
    }

    @Test
    fun longErrorsAreCut() {
        assertEquals(
            "x".repeat(MAX_ERROR_CHARS),
            error(checkAnswer("x".repeat(MAX_ERROR_CHARS + 1), null, null)),
        )
    }

    @Test
    fun aMissingCertificateOrKeyIsRefused() {
        val missing = "No certificate in its answer"
        assertEquals(missing, error(checkAnswer(null, null, fixture("phone.key"))))
        assertEquals(missing, error(checkAnswer(null, fixture("phone.crt"), null)))
    }

    @Test
    fun answersOverTheLimitAreRefused() {
        val tooLarge = "Answer too large"
        val over = ByteArray(MAX_BYTES + 1)
        assertEquals(tooLarge, error(checkAnswer(null, over, fixture("phone.key"))))
        assertEquals(tooLarge, error(checkAnswer(null, fixture("phone.crt"), over)))
    }

    @Test
    fun answersAtTheLimitAreRead() {
        assertEquals(
            "CertificateException: Could not parse certificate: java.io.IOException: Empty input",
            error(checkAnswer(null, ByteArray(MAX_BYTES), fixture("phone.key"))),
        )
    }

    @Test
    fun aCertificateThatIsNotX509IsRefused() {
        assertEquals(
            "CertificateParsingException: signed fields invalid",
            error(checkAnswer(null, fixture("phone.key"), fixture("phone.key"))),
        )
    }

    @Test
    fun aKeyThatIsNotPkcs8IsRefused() {
        assertEquals(
            "InvalidKeySpecException: java.security.InvalidKeyException: Unable to decode key",
            error(checkAnswer(null, fixture("phone.crt"), fixture("phone.crt"))),
        )
    }

    @Test
    fun anotherCertificatesKeyIsRefused() {
        val result = checkAnswer(null, fixture("phone.crt"), fixture("ca.key"))
        assertEquals("Key doesn't match the certificate", error(result))
    }

    @Test
    fun anEcKeyIsRefused() {
        assertEquals(
            "InvalidKeySpecException: java.security.InvalidKeyException: Invalid RSA private key",
            error(checkAnswer(null, fixture("ec.crt"), fixture("ec.key"))),
        )
    }

    @Test
    fun anEcCertificateIsRefused() {
        val result = checkAnswer(null, fixture("ec.crt"), fixture("phone.key"))
        assertEquals("Key doesn't match the certificate", error(result))
    }

    private fun error(result: FetchResult): String =
        assertInstanceOf(FetchResult.Error::class.java, result).message

    private fun fixture(name: String): ByteArray =
        checkNotNull(javaClass.getResourceAsStream(name)) { name }.use { it.readBytes() }
}
