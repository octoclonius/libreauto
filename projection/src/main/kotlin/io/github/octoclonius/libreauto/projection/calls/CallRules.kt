package io.github.octoclonius.libreauto.projection.calls

import android.telecom.Call
import android.telecom.TelecomManager

internal fun callStatus(state: Int): CallStatus? =
    when (state) {
        Call.STATE_RINGING,
        Call.STATE_SIMULATED_RINGING -> CallStatus.Ringing
        Call.STATE_DIALING -> CallStatus.Dialing
        Call.STATE_ACTIVE -> CallStatus.Active
        Call.STATE_HOLDING -> CallStatus.Holding
        else -> null
    }

internal fun isInCall(calls: List<PhoneCall>): Boolean = calls.isNotEmpty()

internal fun caller(
    contactName: String?,
    networkName: String?,
    number: CallerNumber?,
    numberPresentation: Int,
): Caller =
    when {
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
