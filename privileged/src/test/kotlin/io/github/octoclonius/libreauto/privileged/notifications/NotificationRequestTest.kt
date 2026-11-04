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
    fun aClickOnAShownNotificationIsAccepted() {
        assertNull(notificationClickRequestError(rank = 0, count = 1))
        assertNull(notificationClickRequestError(rank = 2, count = 3))
    }

    @Test
    fun aClickOutsideTheShownNotificationsIsRefused() {
        assertNotNull(notificationClickRequestError(rank = -1, count = 3))
        assertNotNull(notificationClickRequestError(rank = 3, count = 3))
        assertNotNull(notificationClickRequestError(rank = 0, count = 0))
    }
}
