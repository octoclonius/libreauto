package io.github.octoclonius.libreauto.privileged.notifications

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class NotificationRequestTest {

    private val appPackage = "io.github.octoclonius.libreauto"

    @Test
    fun theAppsListenerIsAccepted() {
        assertNull(notificationAccessRequestError(appPackage, appPackage))
    }

    @Test
    fun anotherAppsListenerIsRefused() {
        assertNotNull(notificationAccessRequestError("com.example.reader", appPackage))
    }

    @Test
    fun aShownNotificationIsAccepted() {
        assertNull(notificationVisibilityRequestError(rank = 0, count = 1))
        assertNull(notificationVisibilityRequestError(rank = 2, count = 3))
    }

    @Test
    fun aNotificationOutsideTheShownOnesIsRefused() {
        assertNotNull(notificationVisibilityRequestError(rank = -1, count = 3))
        assertNotNull(notificationVisibilityRequestError(rank = 3, count = 3))
        assertNotNull(notificationVisibilityRequestError(rank = 0, count = 0))
    }
}
