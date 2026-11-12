package io.github.octoclonius.libreauto.projection.calls

import android.telecom.Call
import android.telecom.VideoProfile
import android.util.Log
import io.github.octoclonius.libreauto.projection.privileged.PrivilegedServiceConnection
import io.github.octoclonius.libreauto.projection.privileged.call
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CallRepository(
    private val privileged: PrivilegedServiceConnection,
    private val externalScope: CoroutineScope,
) {

    private companion object {
        const val TAG = "CallRepository"
        const val ANSWER_VIDEO_STATE = VideoProfile.STATE_AUDIO_ONLY
    }

    private val _isInCall = MutableStateFlow(false)
    val isInCall: StateFlow<Boolean> = _isInCall.asStateFlow()

    private val calls = mutableListOf<Call>()

    fun start() {
        externalScope.launch(Dispatchers.IO) {
            privileged.service.collect { service ->
                service?.call("allow the phone's calls") { it.allowCallAccess() }
            }
        }
    }

    fun acceptRingingCall() {
        privileged.call("answer the ringing call") { it.acceptRingingCall(ANSWER_VIDEO_STATE) }
    }

    fun endCall() {
        privileged.call("end the foreground call") { it.endCall() }
    }

    internal fun onCallAdded(call: Call) {
        calls += call
        publish()
    }

    internal fun onCallRemoved(call: Call) {
        calls -= call
        publish()
    }

    internal fun onCallChanged() {
        publish()
    }

    internal fun onUnbound() {
        calls.clear()
        publish()
    }

    private fun publish() {
        val next = isInCall(calls.filter { it.parent == null }.map { it.details.state })
        if (next == _isInCall.value) return
        Log.i(TAG, if (next) "In a call" else "Not in a call")
        _isInCall.value = next
    }
}
