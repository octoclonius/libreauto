package io.github.octoclonius.libreauto.privileged

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class AudioRequestTest {

    private val media = 1
    private val game = 14
    private val navigation = 12
    private val assistant = 16
    private val notification = 5

    @Test
    fun mediaAndGuidanceOutputsAreAccepted() {
        assertNull(
            audioRequestError(
                listOf(
                    AudioOutput(intArrayOf(media, game), 48000, 2),
                    AudioOutput(intArrayOf(navigation, assistant), 16000, 1),
                ),
                AudioInput(16000, 1),
            )
        )
    }

    @Test
    fun noOutputsAndNoInputAreRefused() {
        assertNotNull(audioRequestError(emptyList(), null))
    }

    @Test
    fun anInputWithoutOutputsIsAccepted() {
        assertNull(audioRequestError(emptyList(), AudioInput(16000, 1)))
    }

    @Test
    fun anInputSampleRateThatIsntPositiveIsRefused() {
        assertNotNull(audioRequestError(emptyList(), AudioInput(0, 1)))
    }

    @Test
    fun onlyOneOrTwoInputChannelsAreAccepted() {
        assertNotNull(audioRequestError(emptyList(), AudioInput(16000, 0)))
        assertNotNull(audioRequestError(emptyList(), AudioInput(16000, 3)))
        assertNull(audioRequestError(emptyList(), AudioInput(16000, 2)))
    }

    @Test
    fun anOutputWithoutUsagesIsRefused() {
        assertNotNull(audioRequestError(listOf(AudioOutput(intArrayOf(), 48000, 2)), null))
    }

    @Test
    fun usagesThatStayOnThePhoneAreRefused() {
        assertNotNull(
            audioRequestError(listOf(AudioOutput(intArrayOf(notification), 48000, 2)), null)
        )
    }

    @Test
    fun aUsageInTwoOutputsIsRefused() {
        assertNotNull(
            audioRequestError(
                listOf(
                    AudioOutput(intArrayOf(media), 48000, 2),
                    AudioOutput(intArrayOf(media, navigation), 16000, 1),
                ),
                null,
            )
        )
    }

    @Test
    fun aSampleRateThatIsntPositiveIsRefused() {
        assertNotNull(audioRequestError(listOf(AudioOutput(intArrayOf(media), 0, 2)), null))
    }

    @Test
    fun onlyOneOrTwoChannelsAreAccepted() {
        assertNotNull(audioRequestError(listOf(AudioOutput(intArrayOf(media), 48000, 0)), null))
        assertNotNull(audioRequestError(listOf(AudioOutput(intArrayOf(media), 48000, 3)), null))
        assertNull(audioRequestError(listOf(AudioOutput(intArrayOf(media), 48000, 1)), null))
    }
}
