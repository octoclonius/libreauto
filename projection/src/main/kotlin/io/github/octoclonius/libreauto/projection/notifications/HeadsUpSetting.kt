package io.github.octoclonius.libreauto.projection.notifications

import android.content.ContentResolver
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

private const val HEADS_UP_NOTIFICATIONS_ENABLED = "heads_up_notifications_enabled"
private const val HEADS_UP_OFF = 0

internal fun isHeadsUpEnabled(resolver: ContentResolver): Boolean =
    Settings.Global.getInt(resolver, HEADS_UP_NOTIFICATIONS_ENABLED, HEADS_UP_OFF) != HEADS_UP_OFF

internal fun headsUpEnabled(resolver: ContentResolver): Flow<Boolean> = callbackFlow {
    val observer =
        object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(isHeadsUpEnabled(resolver))
            }
        }
    resolver.registerContentObserver(
        Settings.Global.getUriFor(HEADS_UP_NOTIFICATIONS_ENABLED),
        false,
        observer,
    )
    trySend(isHeadsUpEnabled(resolver))
    awaitClose { resolver.unregisterContentObserver(observer) }
}
    .distinctUntilChanged()
