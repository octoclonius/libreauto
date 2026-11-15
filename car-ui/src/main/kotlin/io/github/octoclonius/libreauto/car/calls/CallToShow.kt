package io.github.octoclonius.libreauto.car.calls

import io.github.octoclonius.libreauto.projection.calls.CallStatus
import io.github.octoclonius.libreauto.projection.calls.PhoneCall

private val CALL_TO_SHOW_ORDER =
    listOf(CallStatus.Ringing, CallStatus.Dialing, CallStatus.Active, CallStatus.Holding)

internal fun callToShow(calls: List<PhoneCall>): PhoneCall? =
    CALL_TO_SHOW_ORDER.firstNotNullOfOrNull { status ->
        calls.firstOrNull { it.status == status }
    }

internal fun listedCall(calls: List<PhoneCall>, popUpCall: PhoneCall?): PhoneCall? =
    callToShow(calls)?.takeUnless { it.id == popUpCall?.id }
