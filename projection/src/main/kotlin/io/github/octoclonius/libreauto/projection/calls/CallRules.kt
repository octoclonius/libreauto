package io.github.octoclonius.libreauto.projection.calls

import android.telecom.Call

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
