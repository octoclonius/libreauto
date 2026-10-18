package io.github.octoclonius.libreauto.session.usb

import android.app.Activity
import android.hardware.usb.UsbAccessory
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.util.Log
import io.github.octoclonius.libreauto.session.SessionService

class UsbAccessoryActivity : Activity() {

    companion object {
        private const val TAG = "UsbAccessoryActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val accessory =
            intent?.getParcelableExtra(UsbManager.EXTRA_ACCESSORY, UsbAccessory::class.java)
        if (accessory == null) {
            Log.w(TAG, "Launched without an accessory")
        } else {
            SessionService.start(this, accessory)
        }
        finish()
    }
}
