package io.github.octoclonius.libreauto.projection.calls

import org.junit.jupiter.api.Assertions.assertFalse
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
    fun noCallsIsNotInACall() {
        assertFalse(isInCall(emptyList()))
    }

    @Test
    fun aRingingCallIsInACall() {
        assertTrue(isInCall(listOf(stateRinging)))
        assertTrue(isInCall(listOf(stateSimulatedRinging)))
    }

    @Test
    fun dialingActiveAndHeldCallsAreInACall() {
        assertTrue(isInCall(listOf(stateDialing)))
        assertTrue(isInCall(listOf(stateActive)))
        assertTrue(isInCall(listOf(stateHolding)))
    }

    @Test
    fun callsTelecomDoesNotCountAreNotInACall() {
        assertFalse(
            isInCall(
                listOf(
                    stateNew,
                    stateSelectPhoneAccount,
                    stateConnecting,
                    stateAudioProcessing,
                    stateDisconnecting,
                    stateDisconnected,
                )
            )
        )
    }

    @Test
    fun oneCallInACallIsEnough() {
        assertTrue(isInCall(listOf(stateDisconnected, stateHolding)))
    }
}
