package io.github.octoclonius.libreauto.car.calls

import io.github.octoclonius.libreauto.projection.calls.CallStatus
import io.github.octoclonius.libreauto.projection.calls.Caller
import io.github.octoclonius.libreauto.projection.calls.PhoneCall
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CallToShowTest {

    @Test
    fun noCallsShowNothing() {
        assertNull(callToShow(emptyList()))
    }

    @Test
    fun endingCallsAreNotShown() {
        assertNull(
            callToShow(listOf(call(0, CallStatus.Disconnecting), call(1, CallStatus.Disconnected)))
        )
    }

    @Test
    fun aRingingCallComesFirst() {
        val ringing = call(2, CallStatus.Ringing)
        val calls =
            listOf(
                call(0, CallStatus.Active),
                call(1, CallStatus.Dialing),
                ringing,
                call(3, CallStatus.Holding),
            )
        assertEquals(ringing, callToShow(calls))
    }

    @Test
    fun aDialingCallComesBeforeAnActiveOne() {
        val dialing = call(1, CallStatus.Dialing)
        assertEquals(dialing, callToShow(listOf(call(0, CallStatus.Active), dialing)))
    }

    @Test
    fun anActiveCallComesBeforeAHeldOne() {
        val active = call(1, CallStatus.Active)
        assertEquals(active, callToShow(listOf(call(0, CallStatus.Holding), active)))
    }

    @Test
    fun aHeldCallIsShownWhenItIsTheOnlyOne() {
        val held = call(0, CallStatus.Holding)
        assertEquals(held, callToShow(listOf(held)))
    }

    @Test
    fun theFirstOfTwoCallsInTheSameStateIsShown() {
        val first = call(0, CallStatus.Holding)
        assertEquals(first, callToShow(listOf(first, call(1, CallStatus.Holding))))
    }

    @Test
    fun theCallInThePopUpIsLeftOutOfTheList() {
        val ringing = call(1, CallStatus.Ringing)
        assertNull(listedCall(listOf(call(0, CallStatus.Active), ringing), ringing))
    }

    @Test
    fun theListShowsItsCallWhenThePopUpShowsNone() {
        val ringing = call(1, CallStatus.Ringing)
        assertEquals(ringing, listedCall(listOf(call(0, CallStatus.Active), ringing), null))
    }

    @Test
    fun theListShowsItsCallWhenThePopUpShowsAnother() {
        val active = call(0, CallStatus.Active)
        assertEquals(active, listedCall(listOf(active), call(1, CallStatus.Ringing)))
    }

    private fun call(id: Int, status: CallStatus) =
        PhoneCall(id, status, 0, Caller.Unknown, null, null, 0, false, emptyList(), null, null)
}
