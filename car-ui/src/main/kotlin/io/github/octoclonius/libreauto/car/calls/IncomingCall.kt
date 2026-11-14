package io.github.octoclonius.libreauto.car.calls

import io.github.octoclonius.libreauto.projection.calls.CallStatus
import io.github.octoclonius.libreauto.projection.calls.PhoneCall

internal fun incomingCall(calls: List<PhoneCall>, hiddenIds: Set<Int>): PhoneCall? =
    calls.firstOrNull {
        it.status == CallStatus.Ringing && it.id !in hiddenIds
    }
