package io.github.octoclonius.libreauto.session.calls

import android.telecom.Call
import android.telecom.InCallService
import android.util.Log

class CallService : InCallService() {

    private companion object {
        const val TAG = "CallService"
    }

    private val callback =
        object : Call.Callback() {
            override fun onStateChanged(call: Call, state: Int) {
                PhoneCalls.onCallChanged()
            }

            override fun onDetailsChanged(call: Call, details: Call.Details) {
                PhoneCalls.onCallChanged()
            }
        }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Telecom bound the call service")
    }

    override fun onDestroy() {
        Log.i(TAG, "Telecom unbound the call service")
        PhoneCalls.onUnbound()
        super.onDestroy()
    }

    override fun onCallAdded(call: Call) {
        call.registerCallback(callback)
        PhoneCalls.onCallAdded(call)
    }

    override fun onCallRemoved(call: Call) {
        call.unregisterCallback(callback)
        PhoneCalls.onCallRemoved(call)
    }
}
