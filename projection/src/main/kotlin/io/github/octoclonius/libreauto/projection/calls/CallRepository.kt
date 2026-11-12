package io.github.octoclonius.libreauto.projection.calls

import android.telecom.Call
import android.telecom.VideoProfile
import android.util.Log
import io.github.octoclonius.libreauto.projection.privileged.PrivilegedServiceConnection
import io.github.octoclonius.libreauto.projection.privileged.call
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CallRepository(
    private val privileged: PrivilegedServiceConnection,
    private val externalScope: CoroutineScope,
) {

    private companion object {
        const val TAG = "CallRepository"
        const val ANSWER_VIDEO_STATE = VideoProfile.STATE_AUDIO_ONLY
    }

    private val _calls = MutableStateFlow<List<PhoneCall>>(emptyList())
    val calls: StateFlow<List<PhoneCall>> = _calls.asStateFlow()

    val isInCall: StateFlow<Boolean> =
        calls.map { isInCall(it) }.stateIn(externalScope, SharingStarted.Eagerly, false)

    private val ids = LinkedHashMap<Call, Int>()
    private var nextId = 0

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
        ids[call] = nextId++
        publish()
    }

    internal fun onCallRemoved(call: Call) {
        ids -= call
        publish()
    }

    internal fun onCallChanged() {
        publish()
    }

    internal fun onUnbound() {
        ids.clear()
        publish()
    }

    private fun publish() {
        val next = ids.mapNotNull { (call, id) ->
            if (call.parent != null) return@mapNotNull null
            callStatus(call.details.state)?.let { PhoneCall(id, it) }
        }
        if (next == _calls.value) return
        Log.i(TAG, "Calls: $next")
        _calls.value = next
    }
}
