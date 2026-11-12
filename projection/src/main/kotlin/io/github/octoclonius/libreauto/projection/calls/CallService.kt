package io.github.octoclonius.libreauto.projection.calls

import android.telecom.Call
import android.telecom.InCallService
import android.util.Log
import io.github.octoclonius.libreauto.projection.projectionContainer

class CallService : InCallService() {

    private companion object {
        const val TAG = "CallService"
    }

    private val calls: CallRepository
        get() = projectionContainer.callRepository

    private val callback =
        object : Call.Callback() {
            override fun onStateChanged(call: Call, state: Int) {
                calls.onCallChanged()
            }

            override fun onDetailsChanged(call: Call, details: Call.Details) {
                calls.onCallChanged()
            }

            override fun onParentChanged(call: Call, parent: Call?) {
                calls.onCallChanged()
            }
        }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Telecom bound the call service")
    }

    override fun onDestroy() {
        Log.i(TAG, "Telecom unbound the call service")
        calls.onUnbound()
        super.onDestroy()
    }

    override fun onCallAdded(call: Call) {
        call.registerCallback(callback)
        calls.onCallAdded(call)
    }

    override fun onCallRemoved(call: Call) {
        call.unregisterCallback(callback)
        calls.onCallRemoved(call)
    }
}
