package io.github.octoclonius.libreauto.projection.calls

import android.telecom.Call

private val IN_CALL_STATES =
    setOf(
        Call.STATE_RINGING,
        Call.STATE_SIMULATED_RINGING,
        Call.STATE_DIALING,
        Call.STATE_ACTIVE,
        Call.STATE_HOLDING,
    )

internal fun isInCall(topLevelStates: List<Int>): Boolean = topLevelStates.any {
    it in IN_CALL_STATES
}
