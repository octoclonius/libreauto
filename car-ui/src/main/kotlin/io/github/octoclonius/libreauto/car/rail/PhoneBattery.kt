package io.github.octoclonius.libreauto.car.rail

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

private const val PERCENT = 100

private const val CHARGING_POLICY_ADAPTIVE_LONGLIFE = 4

data class BatteryState(
    val percent: Int,
    val isPluggedIn: Boolean,
    val isChargingPaused: Boolean,
    val isPowerSaveMode: Boolean,
)

fun phoneBattery(context: Context): Flow<BatteryState> = callbackFlow {
    val power = context.getSystemService(PowerManager::class.java)
    var battery: Intent? = null
    fun update() {
        val intent = battery ?: return
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, PERCENT)
        val isPluggedIn = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        val isCharging =
            intent.getIntExtra(BatteryManager.EXTRA_STATUS, 0) ==
                BatteryManager.BATTERY_STATUS_CHARGING
        trySend(
            BatteryState(
                percent = level * PERCENT / scale.coerceAtLeast(1),
                isPluggedIn = isPluggedIn,
                isChargingPaused =
                    isPluggedIn &&
                        !isCharging &&
                        intent.getIntExtra(BatteryManager.EXTRA_CHARGING_STATUS, 0) ==
                            CHARGING_POLICY_ADAPTIVE_LONGLIFE,
                isPowerSaveMode = power.isPowerSaveMode,
            )
        )
    }
    val receiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == Intent.ACTION_BATTERY_CHANGED) battery = intent
                update()
            }
        }
    context.registerReceiver(
        receiver,
        IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        },
        Context.RECEIVER_NOT_EXPORTED,
    )
    awaitClose { context.unregisterReceiver(receiver) }
}
