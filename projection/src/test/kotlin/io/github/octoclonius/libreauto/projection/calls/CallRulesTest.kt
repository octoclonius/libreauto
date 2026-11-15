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

    private val capabilityHold = 0x1
    private val capabilityMergeConference = 0x4
    private val capabilityMute = 0x40

    private val presentationAllowed = 1
    private val presentationRestricted = 2
    private val presentationUnknown = 3
    private val presentationPayphone = 4
    private val presentationUnavailable = 5

    private val number = CallerNumber("5551234567", "(555) 123-4567", "US")

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
        assertTrue(isInCall(listOf(call(CallStatus.Ringing))))
        assertTrue(isInCall(listOf(call(CallStatus.Holding))))
    }

    @Test
    fun aConferenceableCallCanMerge() {
        assertEquals(
            capabilityHold or capabilityMute or capabilityMergeConference,
            callCapabilities(capabilityHold or capabilityMute, hasConferenceableCall = true),
        )
    }

    @Test
    fun withoutAConferenceableCallTheCapabilitiesAreTelecoms() {
        assertEquals(
            capabilityHold or capabilityMute,
            callCapabilities(capabilityHold or capabilityMute, hasConferenceableCall = false),
        )
        assertEquals(
            capabilityMergeConference,
            callCapabilities(capabilityMergeConference, hasConferenceableCall = false),
        )
    }

    @Test
    fun theContactsNameComesFirst() {
        assertEquals(
            Caller.Contact("Mom", number),
            caller("Mom", "MOBILE CALLER", number, presentationAllowed),
        )
    }

    @Test
    fun theNetworksNameStandsInForAMissingContact() {
        assertEquals(
            Caller.Named("MOBILE CALLER", number),
            caller(null, "MOBILE CALLER", number, presentationAllowed),
        )
        assertEquals(
            Caller.Named("MOBILE CALLER", number),
            caller(" ", "MOBILE CALLER", number, presentationAllowed),
        )
    }

    @Test
    fun theNumberStandsInForAMissingName() {
        assertEquals(Caller.Number(number), caller(null, null, number, presentationAllowed))
    }

    @Test
    fun nothingToShowIsUnknown() {
        assertEquals(Caller.Unknown, caller(null, "", null, presentationAllowed))
    }

    @Test
    fun aHiddenNumberIsNamedByItsPresentation() {
        assertEquals(Caller.Private, caller(null, null, null, presentationRestricted))
        assertEquals(Caller.Payphone, caller(null, null, null, presentationPayphone))
        assertEquals(Caller.Unknown, caller(null, null, null, presentationUnknown))
        assertEquals(Caller.Unknown, caller(null, null, null, presentationUnavailable))
    }

    @Test
    fun aHiddenNumberWinsOverANameSentAnyway() {
        assertEquals(Caller.Private, caller("Mom", "MOBILE CALLER", null, presentationRestricted))
    }

    @Test
    fun theNetworksCountryComesFirst() {
        assertEquals("US", countryIso("us", "ca", "DE"))
    }

    @Test
    fun theSimsCountryStandsInWithoutANetwork() {
        assertEquals("CA", countryIso("", "ca", "DE"))
    }

    @Test
    fun theLocalesCountryIsTheLastResort() {
        assertEquals("DE", countryIso(null, "", "DE"))
    }

    private fun call(status: CallStatus) =
        PhoneCall(0, status, 0, Caller.Unknown, null, null, 0, false, emptyList(), null)
}
