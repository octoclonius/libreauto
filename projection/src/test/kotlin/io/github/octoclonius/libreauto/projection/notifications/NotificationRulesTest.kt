package io.github.octoclonius.libreauto.projection.notifications

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NotificationRulesTest {

    private val importanceDefault = 3
    private val importanceHigh = 4

    private val groupAlertAll = 0
    private val groupAlertSummary = 1
    private val groupAlertChildren = 2

    private val now = 1_800_000_000_000L

    @Test
    fun aDismissableNotificationIsListed() {
        assertTrue(listed())
    }

    @Test
    fun anOngoingNotificationIsNotListed() {
        assertFalse(listed(isOngoing = true))
    }

    @Test
    fun aNonDismissableNotificationIsNotListed() {
        assertFalse(listed(isNonDismissable = true))
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
    fun aSummaryWithListedChildrenIsLeftOut() {
        val summary = grouped("chat", isGroupSummary = true)
        val child = grouped("chat")
        assertEquals(listOf(child), withoutCoveredSummaries(listOf(summary, child)))
    }

    @Test
    fun aSummaryWithoutListedChildrenStays() {
        val summary = grouped("chat", isGroupSummary = true)
        val other = grouped("mail")
        assertEquals(listOf(summary, other), withoutCoveredSummaries(listOf(summary, other)))
    }

    @Test
    fun aHighImportanceNotificationAlerts() {
        assertTrue(alerting())
    }

    @Test
    fun nothingAlertsWhileHeadsUpsAreOff() {
        assertFalse(alerting(isHeadsUpEnabled = false))
    }

    @Test
    fun aListedNotificationIsItsOwnHeadsUp() {
        val posted = grouped("chat")
        assertSame(posted, headsUpTarget(posted, listOf(grouped("mail"), posted)))
    }

    @Test
    fun aSummaryHandsItsHeadsUpToItsNewestChild() {
        val summary = grouped("chat", isGroupSummary = true)
        val older = grouped("chat", postedWhen = 1)
        val newer = grouped("chat", postedWhen = 2)
        assertSame(newer, headsUpTarget(summary, listOf(older, newer, grouped("mail", 3))))
    }

    @Test
    fun aSummaryWithoutListedChildrenHasNoHeadsUp() {
        assertNull(headsUpTarget(grouped("chat", isGroupSummary = true), listOf(grouped("mail"))))
    }

    @Test
    fun anUnlistedNotificationHasNoHeadsUp() {
        assertNull(headsUpTarget(grouped("chat"), listOf(grouped("mail"))))
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
    fun aSilentNotificationDoesNotAlert() {
        assertFalse(alerting(isSilent = true))
    }

    @Test
    fun aTooOldNotificationDoesNotAlert() {
        assertFalse(alerting(isTooOld = true))
    }

    @Test
    fun aNotificationThatAlertedAlertsAgain() {
        assertTrue(alerting(hasInterrupted = true))
    }

    @Test
    fun aNotificationThatAlertedOnlyAlertsOnce() {
        assertFalse(alerting(hasInterrupted = true, isOnlyAlertOnce = true))
    }

    @Test
    fun aNotificationThatNeverAlertedAlertsEvenIfOnlyOnce() {
        assertTrue(alerting(isOnlyAlertOnce = true))
    }

    @Test
    fun aNotificationFromADayAgoIsTooOld() {
        assertTrue(tooOld(whenMillis = now - MAX_HUN_WHEN_AGE_MILLIS))
    }

    @Test
    fun aNotificationFromJustUnderADayAgoIsNotTooOld() {
        assertFalse(tooOld(whenMillis = now - MAX_HUN_WHEN_AGE_MILLIS + 1))
    }

    @Test
    fun aNotificationWithoutATimeIsNotTooOld() {
        assertFalse(tooOld(whenMillis = 0))
    }

    @Test
    fun fullScreenForegroundAndUserJobNotificationsAreNeverTooOld() {
        assertFalse(tooOld(hasFullScreenIntent = true))
        assertFalse(tooOld(isForegroundService = true))
        assertFalse(tooOld(isUserInitiatedJob = true))
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
        isOngoing: Boolean = false,
        isNonDismissable: Boolean = false,
        isMedia: Boolean = false,
        isHiddenOnPhone: Boolean = false,
    ): Boolean = isListed(isOngoing, isNonDismissable, isMedia, isHiddenOnPhone)

    private class Grouped(
        override val groupKey: String,
        override val isGroupSummary: Boolean,
        override val postedWhen: Long,
    ) : GroupedNotification

    private fun grouped(
        groupKey: String,
        postedWhen: Long = 0,
        isGroupSummary: Boolean = false,
    ): GroupedNotification = Grouped(groupKey, isGroupSummary, postedWhen)

    private fun alerting(
        isHeadsUpEnabled: Boolean = true,
        importance: Int = importanceHigh,
        isPeekSuppressed: Boolean = false,
        isGroupAlertSuppressed: Boolean = false,
        isSilent: Boolean = false,
        isTooOld: Boolean = false,
        hasInterrupted: Boolean = false,
        isOnlyAlertOnce: Boolean = false,
    ): Boolean =
        isAlerting(
            isHeadsUpEnabled,
            importance,
            isPeekSuppressed,
            isGroupAlertSuppressed,
            isSilent,
            isTooOld,
            hasInterrupted,
            isOnlyAlertOnce,
        )

    private fun tooOld(
        whenMillis: Long = now - MAX_HUN_WHEN_AGE_MILLIS,
        hasFullScreenIntent: Boolean = false,
        isForegroundService: Boolean = false,
        isUserInitiatedJob: Boolean = false,
    ): Boolean =
        isTooOldToAlert(
            whenMillis,
            now,
            hasFullScreenIntent,
            isForegroundService,
            isUserInitiatedJob,
        )

    private fun groupAlertSuppressed(
        isInGroup: Boolean = true,
        isGroupSummary: Boolean,
        behavior: Int,
    ): Boolean = isGroupAlertSuppressed(isInGroup, isGroupSummary, behavior)
}
