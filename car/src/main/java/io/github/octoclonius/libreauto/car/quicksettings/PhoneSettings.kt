package io.github.octoclonius.libreauto.car.quicksettings

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.telephony.SubscriptionManager
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import io.github.octoclonius.libreauto.car.rail.isAirplaneModeOn
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

private const val BLUETOOTH_ON = 1

data class PhoneSettingsState(
    val isWifiOn: Boolean,
    val isBluetoothOn: Boolean,
    val isMobileDataOn: Boolean?,
    val isAirplaneModeOn: Boolean,
    val isDoNotDisturbOn: Boolean,
    val isLocationOn: Boolean,
)

fun phoneSettings(context: Context): Flow<PhoneSettingsState> =
    callbackFlow {
            val handler = Handler(Looper.getMainLooper())
            val wifi = context.getSystemService(WifiManager::class.java)
            val notifications = context.getSystemService(NotificationManager::class.java)
            val location = context.getSystemService(LocationManager::class.java)
            val subscription = SubscriptionManager.getDefaultDataSubscriptionId()
            val telephony =
                if (
                    context.packageManager.hasSystemFeature(
                        PackageManager.FEATURE_TELEPHONY_DATA
                    ) && SubscriptionManager.isValidSubscriptionId(subscription)
                ) {
                    context
                        .getSystemService(TelephonyManager::class.java)
                        .createForSubscriptionId(subscription)
                } else {
                    null
                }

            fun update() {
                trySend(
                    PhoneSettingsState(
                        isWifiOn =
                            wifi.wifiState == WifiManager.WIFI_STATE_ENABLED ||
                                wifi.wifiState == WifiManager.WIFI_STATE_ENABLING,
                        isBluetoothOn =
                            Settings.Global.getInt(
                                context.contentResolver,
                                Settings.Global.BLUETOOTH_ON,
                                0,
                            ) == BLUETOOTH_ON,
                        isMobileDataOn = telephony?.isDataEnabled,
                        isAirplaneModeOn = isAirplaneModeOn(context),
                        isDoNotDisturbOn =
                            notifications.currentInterruptionFilter !=
                                NotificationManager.INTERRUPTION_FILTER_ALL &&
                                notifications.currentInterruptionFilter !=
                                    NotificationManager.INTERRUPTION_FILTER_UNKNOWN,
                        isLocationOn = location.isLocationEnabled,
                    )
                )
            }

            val receiver =
                object : BroadcastReceiver() {
                    override fun onReceive(context: Context, intent: Intent) = update()
                }
            val bluetoothObserver =
                object : ContentObserver(handler) {
                    override fun onChange(selfChange: Boolean) = update()
                }
            val telephonyCallback =
                object : TelephonyCallback(), TelephonyCallback.UserMobileDataStateListener {
                    override fun onUserMobileDataStateChanged(enabled: Boolean) = update()
                }
            context.registerReceiver(
                receiver,
                IntentFilter().apply {
                    addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
                    addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED)
                    addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
                    addAction(LocationManager.MODE_CHANGED_ACTION)
                },
                null,
                handler,
                Context.RECEIVER_NOT_EXPORTED,
            )
            context.contentResolver.registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.BLUETOOTH_ON),
                false,
                bluetoothObserver,
            )
            telephony?.registerTelephonyCallback(context.mainExecutor, telephonyCallback)
            update()
            awaitClose {
                context.unregisterReceiver(receiver)
                context.contentResolver.unregisterContentObserver(bluetoothObserver)
                telephony?.unregisterTelephonyCallback(telephonyCallback)
            }
        }
        .distinctUntilChanged()
