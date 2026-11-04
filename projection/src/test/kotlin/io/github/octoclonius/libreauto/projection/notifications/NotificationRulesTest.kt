package io.github.octoclonius.libreauto.projection.notifications

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NotificationRulesTest {

    private val importanceDefault = 3
    private val importanceHigh = 4

    private val groupAlertAll = 0
    private val groupAlertSummary = 1
    private val groupAlertChildren = 2

    @Test
    fun aClearableNotificationIsListed() {
        assertTrue(listed())
    }

    @Test
    fun anOngoingNotificationIsNotListed() {
        assertFalse(listed(isClearable = false))
    }

    @Test
    fun aForegroundServiceNotificationIsNotListed() {
        assertFalse(listed(isForegroundService = true))
    }

    @Test
    fun aMediaNotificationIsNotListed() {
        assertFalse(listed(isMedia = true))
    }

    @Test
    fun aNotificationThePhoneHidesIsNotListed() {
        assertFalse(listed(isHiddenOnPhone = true))
    }

    @Test
    fun aHighImportanceNotificationAlerts() {
        assertTrue(alerting())
    }

    @Test
    fun aDefaultImportanceNotificationDoesNotAlert() {
        assertFalse(alerting(importance = importanceDefault))
    }

    @Test
    fun aNotificationWhosePeekIsSuppressedDoesNotAlert() {
        assertFalse(alerting(isPeekSuppressed = true))
    }

    @Test
    fun aNotificationWhoseGroupAlertsElsewhereDoesNotAlert() {
        assertFalse(alerting(isGroupAlertSuppressed = true))
    }

    @Test
    fun anUpdateAlertsAgain() {
        assertTrue(alerting(isUpdate = true))
    }

    @Test
    fun anUpdateThatOnlyAlertsOnceDoesNotAlert() {
        assertFalse(alerting(isUpdate = true, isOnlyAlertOnce = true))
    }

    @Test
    fun aNewNotificationThatOnlyAlertsOnceAlerts() {
        assertTrue(alerting(isOnlyAlertOnce = true))
    }

    @Test
    fun aGroupThatAlertsForAllSuppressesNothing() {
        assertFalse(groupAlertSuppressed(isGroupSummary = true, behavior = groupAlertAll))
        assertFalse(groupAlertSuppressed(isGroupSummary = false, behavior = groupAlertAll))
    }

    @Test
    fun aGroupThatAlertsForItsSummarySuppressesItsChildren() {
        assertFalse(groupAlertSuppressed(isGroupSummary = true, behavior = groupAlertSummary))
        assertTrue(groupAlertSuppressed(isGroupSummary = false, behavior = groupAlertSummary))
    }

    @Test
    fun aGroupThatAlertsForItsChildrenSuppressesItsSummary() {
        assertTrue(groupAlertSuppressed(isGroupSummary = true, behavior = groupAlertChildren))
        assertFalse(groupAlertSuppressed(isGroupSummary = false, behavior = groupAlertChildren))
    }

    @Test
    fun aNotificationOutsideAGroupIsNeverSuppressedByGrouping() {
        assertFalse(
            groupAlertSuppressed(
                isInGroup = false,
                isGroupSummary = false,
                behavior = groupAlertSummary,
            )
        )
    }

    private fun listed(
        isClearable: Boolean = true,
        isForegroundService: Boolean = false,
        isMedia: Boolean = false,
        isHiddenOnPhone: Boolean = false,
    ): Boolean = isListed(isClearable, isForegroundService, isMedia, isHiddenOnPhone)

    private fun alerting(
        importance: Int = importanceHigh,
        isPeekSuppressed: Boolean = false,
        isGroupAlertSuppressed: Boolean = false,
        isUpdate: Boolean = false,
        isOnlyAlertOnce: Boolean = false,
    ): Boolean =
        isAlerting(importance, isPeekSuppressed, isGroupAlertSuppressed, isUpdate, isOnlyAlertOnce)

    private fun groupAlertSuppressed(
        isInGroup: Boolean = true,
        isGroupSummary: Boolean,
        behavior: Int,
    ): Boolean = isGroupAlertSuppressed(isInGroup, isGroupSummary, behavior)
}
