package io.github.octoclonius.libreauto.projection.calls

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CallRulesTest {

    private val stateNew = 0
    private val stateDialing = 1
    private val stateRinging = 2
    private val stateHolding = 3
    private val stateActive = 4
    private val stateDisconnected = 7
    private val stateSelectPhoneAccount = 8
    private val stateConnecting = 9
    private val stateDisconnecting = 10
    private val stateAudioProcessing = 12
    private val stateSimulatedRinging = 13

    @Test
    fun ringingCallsRing() {
        assertEquals(CallStatus.Ringing, callStatus(stateRinging))
        assertEquals(CallStatus.Ringing, callStatus(stateSimulatedRinging))
    }

    @Test
    fun dialingActiveAndHeldCallsKeepTheirStatus() {
        assertEquals(CallStatus.Dialing, callStatus(stateDialing))
        assertEquals(CallStatus.Active, callStatus(stateActive))
        assertEquals(CallStatus.Holding, callStatus(stateHolding))
    }

    @Test
    fun callsTelecomDoesNotCountAreNotListed() {
        for (state in
            listOf(
                stateNew,
                stateSelectPhoneAccount,
                stateConnecting,
                stateAudioProcessing,
                stateDisconnecting,
                stateDisconnected,
            )) {
            assertNull(callStatus(state))
        }
    }

    @Test
    fun noCallsIsNotInACall() {
        assertFalse(isInCall(emptyList()))
    }

    @Test
    fun anyListedCallIsInACall() {
        assertTrue(isInCall(listOf(PhoneCall(0, CallStatus.Ringing))))
        assertTrue(isInCall(listOf(PhoneCall(0, CallStatus.Holding))))
    }
}
