package io.github.octoclonius.libreauto.car.rail

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

internal const val PERCENT = 100

data class BatteryState(val percent: Int, val isPluggedIn: Boolean)

fun phoneBattery(context: Context): Flow<BatteryState> = callbackFlow {
    val receiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, PERCENT)
                trySend(
                    BatteryState(
                        percent = level * PERCENT / scale.coerceAtLeast(1),
                        isPluggedIn = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0,
                    )
                )
            }
        }
    context.registerReceiver(
        receiver,
        IntentFilter(Intent.ACTION_BATTERY_CHANGED),
        Context.RECEIVER_NOT_EXPORTED,
    )
    awaitClose { context.unregisterReceiver(receiver) }
}
