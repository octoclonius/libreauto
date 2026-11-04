package io.github.octoclonius.libreauto.projection.notifications

import android.app.PendingIntent
import android.os.UserHandle

data class PhoneNotification(
    val key: String,
    val packageName: String,
    val user: UserHandle,
    val title: String,
    val text: String,
    val contentIntent: PendingIntent?,
)
