package io.github.octoclonius.libreauto.projection.calls

import android.telecom.Call
import android.telecom.CallEndpoint
import android.telecom.DisconnectCause
import android.telecom.TelecomManager

private const val DISCONNECTED_CALL_SHORT_TIMEOUT_MS = 200L
private const val DISCONNECTED_CALL_MEDIUM_TIMEOUT_MS = 2000L
private const val DISCONNECTED_CALL_LONG_TIMEOUT_MS = 5000L

internal fun callStatus(state: Int): CallStatus? =
    when (state) {
        Call.STATE_RINGING,
        Call.STATE_SIMULATED_RINGING -> CallStatus.Ringing
        Call.STATE_DIALING -> CallStatus.Dialing
        Call.STATE_ACTIVE -> CallStatus.Active
        Call.STATE_HOLDING -> CallStatus.Holding
        Call.STATE_DISCONNECTING -> CallStatus.Disconnecting
        Call.STATE_DISCONNECTED -> CallStatus.Disconnected
        else -> null
    }

internal fun audioOutputType(endpointType: Int): AudioOutputType? =
    when (endpointType) {
        CallEndpoint.TYPE_BLUETOOTH -> AudioOutputType.Bluetooth
        CallEndpoint.TYPE_SPEAKER -> AudioOutputType.Speaker
        CallEndpoint.TYPE_WIRED_HEADSET -> AudioOutputType.WiredHeadset
        CallEndpoint.TYPE_EARPIECE -> AudioOutputType.Earpiece
        else -> null
    }

internal fun isInCall(calls: List<PhoneCall>): Boolean = calls.any { it.status.isLive }

internal fun disconnectedCallDelayMillis(cause: Int): Long =
    when (cause) {
        DisconnectCause.LOCAL -> DISCONNECTED_CALL_SHORT_TIMEOUT_MS
        DisconnectCause.REMOTE,
        DisconnectCause.ERROR -> DISCONNECTED_CALL_MEDIUM_TIMEOUT_MS
        DisconnectCause.REJECTED,
        DisconnectCause.MISSED,
        DisconnectCause.CANCELED -> 0L
        else -> DISCONNECTED_CALL_LONG_TIMEOUT_MS
    }

internal fun callCapabilities(capabilities: Int, hasConferenceableCall: Boolean): Int =
    if (hasConferenceableCall) capabilities or Call.Details.CAPABILITY_MERGE_CONFERENCE
    else capabilities

internal fun caller(
    voicemailName: String?,
    contactName: String?,
    networkName: String?,
    number: CallerNumber?,
    numberPresentation: Int,
): Caller =
    when {
        !voicemailName.isNullOrBlank() -> Caller.Voicemail(voicemailName)
        numberPresentation == TelecomManager.PRESENTATION_RESTRICTED -> Caller.Private
        numberPresentation == TelecomManager.PRESENTATION_PAYPHONE -> Caller.Payphone
        numberPresentation != TelecomManager.PRESENTATION_ALLOWED -> Caller.Unknown
        !contactName.isNullOrBlank() -> Caller.Contact(contactName, number)
        !networkName.isNullOrBlank() -> Caller.Named(networkName, number)
        number != null -> Caller.Number(number)
        else -> Caller.Unknown
    }

internal fun countryIso(network: String?, sim: String?, locale: String): String =
    listOf(network, sim).firstOrNull { !it.isNullOrBlank() }?.uppercase() ?: locale
