package io.github.octoclonius.libreauto.privileged.settings

import android.Manifest
import android.app.INotificationManager
import android.app.NotificationManager
import android.bluetooth.IBluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.wifi.IWifiManager
import android.os.Process
import android.os.ServiceManager
import android.os.UserHandle
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log
import com.android.internal.telephony.ISub
import com.android.internal.telephony.ITelephony
import io.github.octoclonius.libreauto.privileged.api.PhoneSetting

internal class PhoneSettings(private val context: Context, private val appPackage: String) {

    companion object {
        private const val TAG = "PhoneSettings"
        private const val BLUETOOTH_MANAGER_SERVICE = "bluetooth_manager"
        private const val SUBSCRIPTION_SERVICE = "isub"
    }

    fun set(setting: Int, isOn: Boolean) {
        val isDone =
            when (setting) {
                PhoneSetting.WIFI -> wifi().setWifiEnabled(context.packageName, isOn)
                PhoneSetting.BLUETOOTH -> setBluetooth(isOn)
                PhoneSetting.MOBILE_DATA -> setMobileData(isOn)
                PhoneSetting.AIRPLANE_MODE -> setAirplaneMode(isOn)
                PhoneSetting.DO_NOT_DISTURB -> setDoNotDisturb(isOn)
                PhoneSetting.LOCATION -> setLocation(isOn)
                else -> false
            }
        if (isDone) {
            Log.i(TAG, "Turned the setting $setting ${if (isOn) "on" else "off"}")
        } else {
            Log.w(TAG, "Couldn't turn the setting $setting ${if (isOn) "on" else "off"}")
        }
    }

    fun turnOnBluetooth(): Boolean = setBluetooth(true)

    private fun setBluetooth(isOn: Boolean): Boolean {
        if (!isAppAllowedNearbyDevices()) {
            Log.w(
                TAG,
                "LibreAuto may not connect to nearby devices, so it may not turn Bluetooth on or off",
            )
            return false
        }
        val bluetooth = IBluetoothManager.Stub.asInterface(service(BLUETOOTH_MANAGER_SERVICE))
        val source = context.attributionSource
        return if (isOn) bluetooth.enable(source) else bluetooth.disable(source, true)
    }

    private fun isAppAllowedNearbyDevices(): Boolean =
        context.packageManager.checkPermission(Manifest.permission.BLUETOOTH_CONNECT, appPackage) ==
            PackageManager.PERMISSION_GRANTED

    private fun setMobileData(isOn: Boolean): Boolean {
        val subscription = ISub.Stub.asInterface(service(SUBSCRIPTION_SERVICE)).defaultDataSubId
        if (!SubscriptionManager.isValidSubscriptionId(subscription)) return false
        ITelephony.Stub.asInterface(service(Context.TELEPHONY_SERVICE))
            .setDataEnabledForReason(
                subscription,
                TelephonyManager.DATA_ENABLED_REASON_USER,
                isOn,
                context.packageName,
            )
        return true
    }

    private fun setAirplaneMode(isOn: Boolean): Boolean {
        ConnectivityManager::class
            .java
            .getMethod("setAirplaneMode", Boolean::class.javaPrimitiveType)
            .invoke(context.getSystemService(ConnectivityManager::class.java), isOn)
        return true
    }

    private fun setDoNotDisturb(isOn: Boolean): Boolean {
        INotificationManager.Stub.asInterface(service(Context.NOTIFICATION_SERVICE))
            .setInterruptionFilter(
                context.packageName,
                if (isOn) {
                    NotificationManager.INTERRUPTION_FILTER_PRIORITY
                } else {
                    NotificationManager.INTERRUPTION_FILTER_ALL
                },
                false,
            )
        return true
    }

    private fun setLocation(isOn: Boolean): Boolean {
        LocationManager::class
            .java
            .getMethod(
                "setLocationEnabledForUser",
                Boolean::class.javaPrimitiveType,
                UserHandle::class.java,
            )
            .invoke(
                context.getSystemService(LocationManager::class.java),
                isOn,
                Process.myUserHandle(),
            )
        return true
    }

    private fun wifi(): IWifiManager = IWifiManager.Stub.asInterface(service(Context.WIFI_SERVICE))

    private fun service(name: String) = ServiceManager.getService(name) ?: error("No $name service")
}
