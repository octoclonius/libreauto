package io.github.octoclonius.libreauto.car.rail

import io.github.octoclonius.libreauto.projection.calls.CallStatus
import io.github.octoclonius.libreauto.projection.calls.Caller
import io.github.octoclonius.libreauto.projection.calls.PhoneCall
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CallIndicatorTest {

    @Test
    fun noCallIsNotUnderWay() {
        assertFalse(isCallUnderWay(emptyList()))
    }

    @Test
    fun aRingingCallIsNotUnderWayYet() {
        assertFalse(isCallUnderWay(listOf(call(0, CallStatus.Ringing))))
    }

    @Test
    fun dialingActiveAndHeldCallsAreUnderWay() {
        for (status in listOf(CallStatus.Dialing, CallStatus.Active, CallStatus.Holding)) {
            assertTrue(isCallUnderWay(listOf(call(0, status))))
        }
    }

    @Test
    fun aCallUnderWayShowsWhileAnotherRings() {
        assertTrue(isCallUnderWay(listOf(call(0, CallStatus.Active), call(1, CallStatus.Ringing))))
    }

    private fun call(id: Int, status: CallStatus) =
        PhoneCall(id, status, 0, Caller.Unknown, null, null, 0, false, emptyList(), null)
}
