package io.github.octoclonius.libreauto.projection.notifications

import android.app.Notification
import android.app.NotificationManager

internal fun isListed(
    isOngoing: Boolean,
    isNonDismissable: Boolean,
    isMedia: Boolean,
    isHiddenOnPhone: Boolean,
): Boolean = !isOngoing && !isNonDismissable && !isMedia && !isHiddenOnPhone

internal fun isAlerting(
    importance: Int,
    isPeekSuppressed: Boolean,
    isGroupAlertSuppressed: Boolean,
    isUpdate: Boolean,
    isOnlyAlertOnce: Boolean,
): Boolean =
    importance >= NotificationManager.IMPORTANCE_HIGH &&
        !isPeekSuppressed &&
        !isGroupAlertSuppressed &&
        !(isUpdate && isOnlyAlertOnce)

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
