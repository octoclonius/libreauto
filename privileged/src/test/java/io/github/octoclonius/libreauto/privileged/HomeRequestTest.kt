package io.github.octoclonius.libreauto.privileged

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class HomeRequestTest {

    private val appUid = 10274

    @Test
    fun theAppsActivityOnTheCarDisplayIsAccepted() {
        assertNull(homeRequestError(7, 7, isActivity = true, creatorUid = appUid, appUid = appUid))
    }

    @Test
    fun aHomeWithoutACarDisplayIsRefused() {
        assertNotNull(
            homeRequestError(7, null, isActivity = true, creatorUid = appUid, appUid = appUid)
        )
    }

    @Test
    fun aHomeOnAnotherDisplayIsRefused() {
        assertNotNull(
            homeRequestError(0, 7, isActivity = true, creatorUid = appUid, appUid = appUid)
        )
    }

    @Test
    fun anIntentThatIsNoActivityIsRefused() {
        assertNotNull(
            homeRequestError(7, 7, isActivity = false, creatorUid = appUid, appUid = appUid)
        )
    }

    @Test
    fun anIntentFromAnotherAppIsRefused() {
        assertNotNull(
            homeRequestError(7, 7, isActivity = true, creatorUid = 10001, appUid = appUid)
        )
    }
}
