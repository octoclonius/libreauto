package io.github.octoclonius.libreauto.session.settings

import android.app.PendingIntent
import android.os.RemoteException
import android.util.Log
import io.github.octoclonius.libreauto.privileged.PhoneSetting
import io.github.octoclonius.libreauto.session.privileged.PrivilegedServiceConnection

enum class QuickSetting {
    Wifi,
    Bluetooth,
    MobileData,
    AirplaneMode,
    DoNotDisturb,
    Location,
}

object PrivilegedSettings {

    private const val TAG = "PrivilegedSettings"

    fun set(setting: QuickSetting, isOn: Boolean) {
        val service = PrivilegedServiceConnection.service.value
        if (service == null) {
            Log.w(TAG, "Didn't change $setting while the privileged service isn't running")
            return
        }
        try {
            service.setPhoneSetting(setting.code, isOn)
        } catch (e: RemoteException) {
            Log.w(TAG, "The privileged service didn't change $setting", e)
        }
    }

    fun showPage(displayId: Int, page: PendingIntent) {
        val service = PrivilegedServiceConnection.service.value
        if (service == null) {
            Log.w(TAG, "Didn't show a settings page while the privileged service isn't running")
            return
        }
        try {
            service.showActivity(displayId, page)
        } catch (e: RemoteException) {
            Log.w(TAG, "The privileged service didn't show a settings page", e)
        }
    }

    private val QuickSetting.code: Int
        get() =
            when (this) {
                QuickSetting.Wifi -> PhoneSetting.WIFI
                QuickSetting.Bluetooth -> PhoneSetting.BLUETOOTH
                QuickSetting.MobileData -> PhoneSetting.MOBILE_DATA
                QuickSetting.AirplaneMode -> PhoneSetting.AIRPLANE_MODE
                QuickSetting.DoNotDisturb -> PhoneSetting.DO_NOT_DISTURB
                QuickSetting.Location -> PhoneSetting.LOCATION
            }
}
