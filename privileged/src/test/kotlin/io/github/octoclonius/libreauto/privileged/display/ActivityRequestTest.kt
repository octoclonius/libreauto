package io.github.octoclonius.libreauto.privileged.display

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ActivityRequestTest {

    @Test
    fun anActivityIsAccepted() {
        assertNull(activityRequestError(isActivity = true))
    }

    @Test
    fun anIntentThatIsNoActivityIsRefused() {
        assertNotNull(activityRequestError(isActivity = false))
    }
}
