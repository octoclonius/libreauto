package io.github.octoclonius.libreauto.privileged

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ActivityRequestTest {

    private val appUid = 10274

    @Test
    fun theAppsActivityOnTheCarDisplayIsAccepted() {
        assertNull(
            activityRequestError(7, 7, isActivity = true, creatorUid = appUid, appUid = appUid)
        )
    }

    @Test
    fun anActivityWithoutACarDisplayIsRefused() {
        assertNotNull(
            activityRequestError(7, null, isActivity = true, creatorUid = appUid, appUid = appUid)
        )
    }

    @Test
    fun anActivityOnAnotherDisplayIsRefused() {
        assertNotNull(
            activityRequestError(0, 7, isActivity = true, creatorUid = appUid, appUid = appUid)
        )
    }

    @Test
    fun anIntentThatIsNoActivityIsRefused() {
        assertNotNull(
            activityRequestError(7, 7, isActivity = false, creatorUid = appUid, appUid = appUid)
        )
    }

    @Test
    fun anIntentFromAnotherAppIsRefused() {
        assertNotNull(
            activityRequestError(7, 7, isActivity = true, creatorUid = 10001, appUid = appUid)
        )
    }
}
