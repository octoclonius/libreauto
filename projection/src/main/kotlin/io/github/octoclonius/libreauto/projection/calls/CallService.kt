package io.github.octoclonius.libreauto.projection.calls

import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.CallEndpoint
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
                calls.onCallChanged(call)
            }

            override fun onDetailsChanged(call: Call, details: Call.Details) {
                calls.onCallChanged(call)
            }

            override fun onParentChanged(call: Call, parent: Call?) {
                calls.onCallChanged(call)
            }

            override fun onChildrenChanged(call: Call, children: List<Call>) {
                calls.onCallChanged(call)
            }

            override fun onConferenceableCallsChanged(
                call: Call,
                conferenceableCalls: List<Call>,
            ) {
                calls.onCallChanged(call)
            }

            override fun onPostDialWait(call: Call, remainingPostDialSequence: String) {
                calls.onPostDialWait(call, remainingPostDialSequence)
            }
        }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Telecom bound the call service")
        calls.onBound(this)
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

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onCallAudioStateChanged(audioState: CallAudioState) {
        calls.onMuteChanged(audioState.isMuted)
    }

    override fun onCallEndpointChanged(callEndpoint: CallEndpoint) {
        calls.onEndpointChanged(callEndpoint)
    }

    override fun onAvailableCallEndpointsChanged(availableEndpoints: List<CallEndpoint>) {
        calls.onAvailableEndpointsChanged(availableEndpoints)
    }
}
