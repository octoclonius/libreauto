package io.github.octoclonius.libreauto.privileged.display

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ActivityRequestTest {

    private val appUid = 10274

    @Test
    fun theAppsActivityIsAccepted() {
        assertNull(activityRequestError(isActivity = true, creatorUid = appUid, appUid = appUid))
    }

    @Test
    fun anIntentThatIsNoActivityIsRefused() {
        assertNotNull(
            activityRequestError(isActivity = false, creatorUid = appUid, appUid = appUid)
        )
    }

    @Test
    fun anIntentFromAnotherAppIsRefused() {
        assertNotNull(activityRequestError(isActivity = true, creatorUid = 10001, appUid = appUid))
    }
}
