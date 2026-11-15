package io.github.octoclonius.libreauto.projection.calls

import android.content.Context
import android.os.OutcomeReceiver
import android.os.Process
import android.telecom.Call
import android.telecom.CallEndpoint
import android.telecom.CallEndpointException
import android.telecom.InCallService
import android.telecom.PhoneAccount
import android.telecom.TelecomManager
import android.telecom.VideoProfile
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager
import android.util.Log
import io.github.octoclonius.libreauto.projection.privileged.PrivilegedServiceConnection
import io.github.octoclonius.libreauto.projection.privileged.call
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CallRepository(
    context: Context,
    private val privileged: PrivilegedServiceConnection,
    private val externalScope: CoroutineScope,
) {

    private companion object {
        const val TAG = "CallRepository"
        const val ANSWER_VIDEO_STATE = VideoProfile.STATE_AUDIO_ONLY
        val NO_CALL_AUDIO = CallAudio(isMuted = false, endpoint = null, endpoints = emptyList())
    }

    private class PendingAnswer(val released: Call, val ringing: Call)

    private val telecom = context.getSystemService(TelecomManager::class.java)
    private val telephony = context.getSystemService(TelephonyManager::class.java)

    private val _calls = MutableStateFlow<List<PhoneCall>>(emptyList())
    val calls: StateFlow<List<PhoneCall>> = _calls.asStateFlow()

    private val _audio = MutableStateFlow(NO_CALL_AUDIO)
    val audio: StateFlow<CallAudio> = _audio.asStateFlow()

    val isInCall: StateFlow<Boolean> =
        calls.map { isInCall(it) }.stateIn(externalScope, SharingStarted.Eagerly, false)

    private val ids = LinkedHashMap<Call, Int>()
    private val postDialWaits = HashMap<Call, String>()
    private val disconnected = HashSet<Call>()
    private val endedCalls = LinkedHashMap<Int, PhoneCall>()
    private var nextId = 0
    private var service: InCallService? = null
    private var pendingAnswer: PendingAnswer? = null

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

    fun answer(id: Int) {
        onCall(id, "answer") { it.answer(ANSWER_VIDEO_STATE) }
    }

    fun answerAndRelease(id: Int) {
        val active =
            ids.keys.firstOrNull {
                it.parent == null && it.details.state == Call.STATE_ACTIVE
            }
        if (active == null) {
            answer(id)
            return
        }
        onCall(id, "answer once the active call ends") { ringing ->
            pendingAnswer = PendingAnswer(active, ringing)
            active.disconnect()
        }
    }

    fun reject(id: Int) {
        onCall(id, "reject") { it.reject(false, null) }
    }

    fun disconnect(id: Int) {
        onCall(id, "disconnect") { it.disconnect() }
    }

    fun hold(id: Int) {
        onCall(id, "hold") { it.hold() }
    }

    fun unhold(id: Int) {
        onCall(id, "unhold") { it.unhold() }
    }

    fun merge(id: Int) {
        onCall(id, "merge") { call ->
            val other = call.conferenceableCalls.firstOrNull()
            if (other != null) call.conference(other) else call.mergeConference()
        }
    }

    fun swapConference(id: Int) {
        onCall(id, "swap the conference") { it.swapConference() }
    }

    fun splitFromConference(id: Int) {
        onCall(id, "split from its conference") { it.splitFromConference() }
    }

    fun playDtmfTone(id: Int, digit: Char) {
        onCall(id, "play a keypad tone") { it.playDtmfTone(digit) }
    }

    fun stopDtmfTone(id: Int) {
        onCall(id, "stop the keypad tone") { it.stopDtmfTone() }
    }

    fun postDialContinue(id: Int, proceed: Boolean) {
        onCall(id, if (proceed) "send the waiting tones" else "drop the waiting tones") { call ->
            postDialWaits -= call
            call.postDialContinue(proceed)
            publish()
        }
    }

    fun setMuted(isMuted: Boolean) {
        val service = service
        if (service == null) {
            Log.i(TAG, "No calls; nothing to mute")
            return
        }
        Log.i(TAG, if (isMuted) "Asking Telecom to mute" else "Asking Telecom to unmute")
        service.setMuted(isMuted)
    }

    fun requestEndpointChange(endpoint: CallEndpoint) {
        val service = service
        if (service == null) {
            Log.i(TAG, "No calls; no audio to route")
            return
        }
        Log.i(TAG, "Asking Telecom to route the call audio to ${endpoint.endpointType}")
        service.requestCallEndpointChange(
            endpoint,
            service.mainExecutor,
            object : OutcomeReceiver<Void, CallEndpointException> {
                override fun onResult(result: Void?) {}

                override fun onError(error: CallEndpointException) {
                    Log.w(TAG, "Telecom didn't route the call audio", error)
                }
            },
        )
    }

    internal fun onBound(service: InCallService) {
        this.service = service
    }

    internal fun onCallAdded(call: Call) {
        ids[call] = nextId++
        recordEnd(call)
        publish()
    }

    internal fun onCallRemoved(call: Call) {
        recordEnd(call)
        disconnected -= call
        ids -= call
        postDialWaits -= call
        answerIfReleased(call)
        publish()
    }

    internal fun onCallChanged(call: Call) {
        recordEnd(call)
        answerIfReleased(call)
        publish()
    }

    internal fun onPostDialWait(call: Call, remaining: String) {
        postDialWaits[call] = remaining
        publish()
    }

    internal fun onMuteChanged(isMuted: Boolean) {
        _audio.update { it.copy(isMuted = isMuted) }
    }

    internal fun onEndpointChanged(endpoint: CallEndpoint) {
        _audio.update { it.copy(endpoint = endpoint) }
    }

    internal fun onAvailableEndpointsChanged(endpoints: List<CallEndpoint>) {
        _audio.update { it.copy(endpoints = endpoints) }
    }

    internal fun onUnbound() {
        service = null
        ids.clear()
        postDialWaits.clear()
        disconnected.clear()
        pendingAnswer = null
        _audio.value = NO_CALL_AUDIO
        publish()
    }

    private fun onCall(id: Int, action: String, block: (Call) -> Unit) {
        val call = ids.entries.firstOrNull { it.value == id }?.key
        if (call == null) {
            Log.i(TAG, "The call $id is gone; nothing to $action")
            return
        }
        Log.i(TAG, "Asking Telecom to $action: call $id")
        block(call)
    }

    private fun answerIfReleased(call: Call) {
        val pending = pendingAnswer?.takeIf { it.released == call } ?: return
        if (call in ids && call.details.state != Call.STATE_DISCONNECTED) return
        pendingAnswer = null
        val ringingId = ids[pending.ringing] ?: return
        if (callStatus(pending.ringing.details.state) != CallStatus.Ringing) return
        answer(ringingId)
    }

    private fun recordEnd(call: Call) {
        if (call.parent != null || call.details.state != Call.STATE_DISCONNECTED) return
        val id = ids[call] ?: return
        if (!disconnected.add(call)) return
        val delayMillis = disconnectedCallDelayMillis(call.details.disconnectCause.code)
        if (delayMillis == 0L) return
        endedCalls[id] = phoneCall(call, id) ?: return
        externalScope.launch {
            delay(delayMillis)
            endedCalls -= id
            publish()
        }
    }

    private fun publish() {
        val live = ids.mapNotNull { (call, id) ->
            if (call.parent == null && call !in disconnected) phoneCall(call, id) else null
        }
        val next = (live + endedCalls.values).sortedBy { it.id }
        if (next == _calls.value) return
        Log.i(TAG, "Calls: ${next.map { "${it.id} ${it.status}" }}")
        _calls.value = next
    }

    private fun phoneCall(call: Call, id: Int): PhoneCall? {
        val details = call.details
        val status = callStatus(details.state) ?: return null
        return PhoneCall(
            id,
            status,
            details.connectTimeMillis,
            caller(
                details.contactDisplayName,
                details.callerDisplayName,
                details.handle?.let { callerNumber(it.scheme, it.schemeSpecificPart) },
                details.handlePresentation,
            ),
            details.contactPhotoUri,
            callingApp(details),
            callCapabilities(
                details.callCapabilities,
                call.conferenceableCalls.any { !it.isRttActive },
            ),
            details.hasProperty(Call.Details.PROPERTY_CONFERENCE),
            call.children
                .mapNotNull { child -> ids[child]?.let { phoneCall(child, it) } }
                .filter { it.status.isLive },
            postDialWaits[call],
            if (status == CallStatus.Disconnected) {
                details.disconnectCause.label?.toString()?.takeIf { it.isNotBlank() }
            } else {
                null
            },
        )
    }

    private fun callerNumber(scheme: String?, number: String?): CallerNumber? {
        if (number.isNullOrBlank()) return null
        val country =
            countryIso(
                telephony.networkCountryIso,
                telephony.simCountryIso,
                Locale.getDefault().country,
            )
        val formatted =
            if (scheme == PhoneAccount.SCHEME_TEL) PhoneNumberUtils.formatNumber(number, country)
            else null
        return CallerNumber(number, formatted ?: number, country)
    }

    private fun callingApp(details: Call.Details): CallingApp? {
        val account = details.accountHandle
        val user = account?.userHandle ?: Process.myUserHandle()
        val isSelfManaged = details.hasProperty(Call.Details.PROPERTY_SELF_MANAGED)
        val packageName =
            if (isSelfManaged) account?.componentName?.packageName else telecom.defaultDialerPackage
        return packageName?.let { CallingApp(it, user, isSelfManaged) }
    }
}
