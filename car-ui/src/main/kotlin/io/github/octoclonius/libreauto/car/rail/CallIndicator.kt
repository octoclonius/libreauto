package io.github.octoclonius.libreauto.car.rail

import io.github.octoclonius.libreauto.projection.calls.CallStatus
import io.github.octoclonius.libreauto.projection.calls.PhoneCall

internal fun isCallUnderWay(calls: List<PhoneCall>): Boolean = calls.any {
    it.status != CallStatus.Ringing
}
