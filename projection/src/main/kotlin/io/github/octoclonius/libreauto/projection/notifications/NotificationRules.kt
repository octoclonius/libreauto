package io.github.octoclonius.libreauto.projection.notifications

import android.app.Notification
import android.app.NotificationManager

internal const val MAX_HUN_WHEN_AGE_MILLIS = 24 * 60 * 60 * 1000L

internal interface GroupedNotification {
    val groupKey: String
    val isGroupSummary: Boolean
    val postedWhen: Long
}

internal fun isListed(
    isOngoing: Boolean,
    isNonDismissable: Boolean,
    isMedia: Boolean,
    isHiddenOnPhone: Boolean,
): Boolean = !isOngoing && !isNonDismissable && !isMedia && !isHiddenOnPhone

internal fun <T : GroupedNotification> withoutCoveredSummaries(listed: List<T>): List<T> {
    val groupsWithChildren = listed.filterNot { it.isGroupSummary }.mapTo(HashSet()) { it.groupKey }
    return listed.filterNot { it.isGroupSummary && it.groupKey in groupsWithChildren }
}

internal fun isAlerting(
    isHeadsUpEnabled: Boolean,
    importance: Int,
    isPeekSuppressed: Boolean,
    isGroupAlertSuppressed: Boolean,
    isSilent: Boolean,
    isTooOld: Boolean,
    hasInterrupted: Boolean,
    isOnlyAlertOnce: Boolean,
): Boolean =
    isHeadsUpEnabled &&
        importance >= NotificationManager.IMPORTANCE_HIGH &&
        !isPeekSuppressed &&
        !isGroupAlertSuppressed &&
        !isSilent &&
        !isTooOld &&
        !(hasInterrupted && isOnlyAlertOnce)

internal fun <T : GroupedNotification> headsUpTarget(posted: T, listed: List<T>): T? =
    when {
        posted in listed -> posted
        posted.isGroupSummary ->
            listed.filter { it.groupKey == posted.groupKey }.maxByOrNull { it.postedWhen }
        else -> null
    }

internal fun isTooOldToAlert(
    whenMillis: Long,
    nowMillis: Long,
    hasFullScreenIntent: Boolean,
    isForegroundService: Boolean,
    isUserInitiatedJob: Boolean,
): Boolean =
    whenMillis > 0 &&
        !hasFullScreenIntent &&
        !isForegroundService &&
        !isUserInitiatedJob &&
        nowMillis - whenMillis >= MAX_HUN_WHEN_AGE_MILLIS

internal fun isGroupAlertSuppressed(
    isInGroup: Boolean,
    isGroupSummary: Boolean,
    groupAlertBehavior: Int,
): Boolean =
    isInGroup &&
        when (groupAlertBehavior) {
            Notification.GROUP_ALERT_SUMMARY -> !isGroupSummary
            Notification.GROUP_ALERT_CHILDREN -> isGroupSummary
            else -> false
        }
