package io.github.octoclonius.libreauto.privileged.calls

import android.annotation.SuppressLint
import android.content.Context
import android.telecom.TelecomManager
import android.util.Log

@SuppressLint("MissingPermission")
internal class PhoneCalls(context: Context) {

    private companion object {
        const val TAG = "PhoneCalls"
    }

    private val telecom = context.getSystemService(TelecomManager::class.java)

    @Suppress("DEPRECATION")
    fun acceptRingingCall(videoState: Int) {
        telecom.acceptRingingCall(videoState)
    }

    @Suppress("DEPRECATION")
    fun endCall() {
        if (!telecom.endCall()) Log.i(TAG, "Telecom had no call to end")
    }
}
