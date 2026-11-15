package io.github.octoclonius.libreauto.car.calls

import io.github.octoclonius.libreauto.projection.calls.CallStatus
import io.github.octoclonius.libreauto.projection.calls.Caller
import io.github.octoclonius.libreauto.projection.calls.PhoneCall
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class IncomingCallTest {

    @Test
    fun aRingingCallIsIncoming() {
        val ringing = call(1, CallStatus.Ringing)
        assertEquals(ringing, incomingCall(listOf(call(0, CallStatus.Active), ringing), emptySet()))
    }

    @Test
    fun callsThatDoNotRingAreNotIncoming() {
        val calls =
            listOf(
                call(0, CallStatus.Dialing),
                call(1, CallStatus.Active),
                call(2, CallStatus.Holding),
            )
        assertNull(incomingCall(calls, emptySet()))
    }

    @Test
    fun aHiddenCallStaysHiddenWhileItRings() {
        assertNull(incomingCall(listOf(call(0, CallStatus.Ringing)), setOf(0)))
    }

    @Test
    fun anotherRingingCallShowsDespiteAHiddenOne() {
        val second = call(1, CallStatus.Ringing)
        assertEquals(second, incomingCall(listOf(call(0, CallStatus.Ringing), second), setOf(0)))
    }

    private fun call(id: Int, status: CallStatus) =
        PhoneCall(id, status, 0, Caller.Unknown, null, null)
}
