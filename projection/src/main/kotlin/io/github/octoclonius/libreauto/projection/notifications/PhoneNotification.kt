package io.github.octoclonius.libreauto.projection.notifications

import android.app.PendingIntent
import android.content.pm.ApplicationInfo
import android.graphics.drawable.Icon
import android.os.UserHandle

data class PhoneNotification(
    val key: String,
    val packageName: String,
    val user: UserHandle,
    val app: ApplicationInfo?,
    val substituteAppName: String?,
    val title: String,
    val text: String,
    val smallIcon: Icon?,
    val prefersSmallIcon: Boolean,
    val contentIntent: PendingIntent?,
)
