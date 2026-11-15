package io.github.octoclonius.libreauto.projection.calls

import android.content.Context
import android.os.Process
import android.telecom.Call
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CallRepository(
    context: Context,
    private val privileged: PrivilegedServiceConnection,
    private val externalScope: CoroutineScope,
) {

    private companion object {
        const val TAG = "CallRepository"
        const val ANSWER_VIDEO_STATE = VideoProfile.STATE_AUDIO_ONLY
    }

    private val telecom = context.getSystemService(TelecomManager::class.java)
    private val telephony = context.getSystemService(TelephonyManager::class.java)

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

    fun answer(id: Int) {
        onCall(id, "answer") { it.answer(ANSWER_VIDEO_STATE) }
    }

    fun reject(id: Int) {
        onCall(id, "reject") { it.reject(false, null) }
    }

    fun disconnect(id: Int) {
        onCall(id, "disconnect") { it.disconnect() }
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

    private fun onCall(id: Int, action: String, block: (Call) -> Unit) {
        val call = ids.entries.firstOrNull { it.value == id }?.key
        if (call == null) {
            Log.i(TAG, "The call $id is gone; nothing to $action")
            return
        }
        Log.i(TAG, "Asking Telecom to $action the call $id")
        block(call)
    }

    private fun publish() {
        val next = ids.mapNotNull { (call, id) ->
            if (call.parent != null) return@mapNotNull null
            val status = callStatus(call.details.state) ?: return@mapNotNull null
            phoneCall(id, status, call.details)
        }
        if (next == _calls.value) return
        Log.i(TAG, "Calls: ${next.map { "${it.id} ${it.status}" }}")
        _calls.value = next
    }

    private fun phoneCall(id: Int, status: CallStatus, details: Call.Details): PhoneCall =
        PhoneCall(
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
        )

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
