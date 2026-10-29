package io.github.octoclonius.libreauto.car.rail

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.text.format.DateFormat
import java.util.Date
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

fun phoneClock(context: Context): Flow<String> = callbackFlow {
    val receiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                trySend(clockText(DateFormat.getTimeFormat(context), Date()))
            }
        }
    val filter =
        IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
    context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
    trySend(clockText(DateFormat.getTimeFormat(context), Date()))
    awaitClose { context.unregisterReceiver(receiver) }
}
