package io.github.octoclonius.libreauto.session.calls

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.telecom.Call
import android.telecom.TelecomManager
import android.util.Log
import io.github.octoclonius.libreauto.session.privileged.PrivilegedServiceConnection
import io.github.octoclonius.libreauto.session.privileged.call
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PhoneCallState(val isRinging: Boolean, val isInCall: Boolean) {
    companion object {
        val IDLE = PhoneCallState(isRinging = false, isInCall = false)
    }
}

class CallRepository(
    context: Context,
    private val privileged: PrivilegedServiceConnection,
    private val externalScope: CoroutineScope,
) {

    private companion object {
        const val TAG = "CallRepository"

        val RINGING_STATES = setOf(Call.STATE_RINGING, Call.STATE_SIMULATED_RINGING)
        val ACTIVE_ORDER =
            listOf(
                Call.STATE_ACTIVE,
                Call.STATE_DIALING,
                Call.STATE_PULLING_CALL,
                Call.STATE_HOLDING,
            )
    }

    private val _state = MutableStateFlow(PhoneCallState.IDLE)
    val state: StateFlow<PhoneCallState> = _state.asStateFlow()

    private val telecom = context.getSystemService(TelecomManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val calls = mutableListOf<Call>()

    fun start() {
        externalScope.launch(Dispatchers.IO) {
            privileged.service.collect { service ->
                service?.call("allow the phone's calls") { it.allowCallAccess() }
            }
        }
    }

    fun answer() {
        main.post {
            val ringing = topLevelCalls().firstOrNull { it.details.state in RINGING_STATES }
            if (ringing == null) {
                Log.i(TAG, "No ringing call to answer")
                return@post
            }
            Log.i(TAG, "Answering the call of ${callingPackage(ringing)}")
            ringing.answer(ringing.details.videoState)
        }
    }

    fun end() {
        main.post {
            val topLevel = topLevelCalls()
            val ringing = topLevel.firstOrNull { it.details.state in RINGING_STATES }
            if (ringing != null) {
                Log.i(TAG, "Rejecting the call of ${callingPackage(ringing)}")
                ringing.reject(false, null)
                return@post
            }
            val current = firstActive(topLevel)
            if (current == null) {
                Log.i(TAG, "No call to end")
                return@post
            }
            Log.i(TAG, "Ending the call of ${callingPackage(current)}")
            current.disconnect()
        }
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
        val states = topLevelCalls()
        val next =
            PhoneCallState(
                isRinging = states.any { it.details.state in RINGING_STATES },
                isInCall =
                    states.any {
                        it.details.state in RINGING_STATES || it.details.state in ACTIVE_ORDER
                    },
            )
        if (next == _state.value) return
        Log.i(TAG, "Phone call state: $next")
        _state.value = next
    }

    private fun topLevelCalls(): List<Call> = calls.filter { it.parent == null }

    private fun firstActive(calls: List<Call>): Call? = ACTIVE_ORDER.firstNotNullOfOrNull { state ->
        calls.firstOrNull { it.details.state == state }
    }

    private fun callingPackage(call: Call): String? =
        if (call.details.hasProperty(Call.Details.PROPERTY_SELF_MANAGED)) {
            call.details.accountHandle?.componentName?.packageName
        } else {
            telecom.defaultDialerPackage
        }
}
