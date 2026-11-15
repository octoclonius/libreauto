package io.github.octoclonius.libreauto.car.calls

import io.github.octoclonius.libreauto.projection.calls.CallStatus
import io.github.octoclonius.libreauto.projection.calls.Caller
import io.github.octoclonius.libreauto.projection.calls.PhoneCall
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CallScreenRulesTest {

    private val capabilityHold = 0x1
    private val capabilitySupportHold = 0x2
    private val capabilityMergeConference = 0x4
    private val capabilitySwapConference = 0x8
    private val capabilityMute = 0x40
    private val canHold = capabilityHold or capabilitySupportHold

    @Test
    fun noCallsShowNoScreen() {
        assertNull(callScreenCalls(emptyList()))
    }

    @Test
    fun aRingingCallAloneShowsNoScreen() {
        assertNull(callScreenCalls(listOf(call(0, CallStatus.Ringing))))
    }

    @Test
    fun aDialingCallComesFirst() {
        val dialing = call(1, CallStatus.Dialing)
        val calls = listOf(call(0, CallStatus.Active), dialing, call(2, CallStatus.Holding))
        assertEquals(dialing, callScreenCalls(calls)?.primary)
    }

    @Test
    fun anActiveCallComesBeforeEndingAndHeldCalls() {
        val active = call(2, CallStatus.Active)
        val calls =
            listOf(
                call(0, CallStatus.Holding),
                call(1, CallStatus.Disconnected),
                active,
                call(3, CallStatus.Ringing),
            )
        assertEquals(active, callScreenCalls(calls)?.primary)
    }

    @Test
    fun anEndingCallComesBeforeAHeldOne() {
        val disconnecting = call(1, CallStatus.Disconnecting)
        val disconnected = call(2, CallStatus.Disconnected)
        assertEquals(
            disconnecting,
            callScreenCalls(listOf(call(0, CallStatus.Holding), disconnected, disconnecting))
                ?.primary,
        )
        assertEquals(
            disconnected,
            callScreenCalls(listOf(call(0, CallStatus.Holding), disconnected))?.primary,
        )
    }

    @Test
    fun aHeldCallAloneIsShown() {
        val held = call(0, CallStatus.Holding)
        assertEquals(CallScreenCalls(held, null), callScreenCalls(listOf(held)))
    }

    @Test
    fun theSecondCallIsTheOtherActiveOrHeldOne() {
        val active = call(0, CallStatus.Active)
        val held = call(1, CallStatus.Holding)
        assertEquals(
            CallScreenCalls(active, held),
            callScreenCalls(listOf(held, active, call(2, CallStatus.Ringing))),
        )
        val dialing = call(3, CallStatus.Dialing)
        assertEquals(CallScreenCalls(dialing, active), callScreenCalls(listOf(active, dialing)))
    }

    @Test
    fun anEndedCallIsNeverTheSecondCall() {
        val active = call(0, CallStatus.Active)
        assertEquals(
            CallScreenCalls(active, null),
            callScreenCalls(listOf(active, call(1, CallStatus.Disconnected))),
        )
    }

    @Test
    fun muteShowsOnlyWhenTheCallCanBeMuted() {
        assertNull(buttons(call(0, CallStatus.Active, canHold)).mute)
        assertEquals(
            CallButton(CallAction.SetMuted(true), isEnabled = true),
            buttons(call(0, CallStatus.Active, capabilityMute)).mute,
        )
    }

    @Test
    fun muteIsCheckedWhileMutedAndUnmutesWhenPressed() {
        assertEquals(
            CallButton(CallAction.SetMuted(false), isEnabled = true, isChecked = true),
            buttons(call(0, CallStatus.Active, capabilityMute), isMuted = true).mute,
        )
    }

    @Test
    fun holdNeedsBothHoldCapabilities() {
        assertNull(buttons(call(0, CallStatus.Active, capabilitySupportHold)).holdOrMerge)
        assertNull(buttons(call(0, CallStatus.Active, capabilityHold)).holdOrMerge)
        assertEquals(
            CallButton(CallAction.Hold(0), isEnabled = true),
            buttons(call(0, CallStatus.Active, canHold)).holdOrMerge,
        )
    }

    @Test
    fun aHeldCallShowsHoldCheckedAndResumesWhenPressed() {
        assertEquals(
            CallButton(CallAction.Unhold(0), isEnabled = true, isChecked = true),
            buttons(call(0, CallStatus.Holding, canHold)).holdOrMerge,
        )
    }

    @Test
    fun mergeTakesHoldsSpot() {
        assertEquals(
            CallButton(CallAction.Merge(0), isEnabled = true),
            buttons(call(0, CallStatus.Active, canHold or capabilityMergeConference)).holdOrMerge,
        )
    }

    @Test
    fun aConferenceThatSwapsShowsSwapInsteadOfHold() {
        val buttons = buttons(call(0, CallStatus.Active, canHold or capabilitySwapConference))
        assertNull(buttons.holdOrMerge)
        assertEquals(CallButton(CallAction.SwapConference(0), isEnabled = true), buttons.swap)
    }

    @Test
    fun swapResumesTheHeldCallWhileTheShownCallIsActive() {
        val held = call(1, CallStatus.Holding)
        assertEquals(
            CallButton(CallAction.Unhold(1), isEnabled = true),
            buttons(call(0, CallStatus.Active), held).swap,
        )
    }

    @Test
    fun swapIsDisabledWhileTheShownCallIsNotActive() {
        val swap = buttons(call(0, CallStatus.Dialing), call(1, CallStatus.Holding)).swap
        assertEquals(CallButton(CallAction.Unhold(1), isEnabled = false), swap)
    }

    @Test
    fun noSecondCallMeansNoSwap() {
        assertNull(buttons(call(0, CallStatus.Active, canHold)).swap)
    }

    @Test
    fun anEndingCallDisablesEverythingButEnd() {
        for (status in listOf(CallStatus.Disconnecting, CallStatus.Disconnected)) {
            val buttons =
                buttons(
                    call(0, status, capabilityMute or canHold),
                    call(1, CallStatus.Holding),
                )
            assertFalse(buttons.mute!!.isEnabled)
            assertFalse(buttons.holdOrMerge!!.isEnabled)
            assertFalse(buttons.swap!!.isEnabled)
            assertEquals(CallAction.Disconnect(0), buttons.end)
        }
    }

    @Test
    fun endHangsUpTheShownCall() {
        assertEquals(CallAction.Disconnect(3), buttons(call(3, CallStatus.Active)).end)
        assertTrue(buttons(call(3, CallStatus.Active, capabilityMute)).mute!!.isEnabled)
    }

    private fun buttons(
        primary: PhoneCall,
        secondary: PhoneCall? = null,
        isMuted: Boolean = false,
    ): CallScreenButtons = callScreenButtons(CallScreenCalls(primary, secondary), isMuted)

    private fun call(id: Int, status: CallStatus, capabilities: Int = 0) =
        PhoneCall(
            id,
            status,
            0,
            Caller.Unknown,
            null,
            null,
            capabilities,
            false,
            emptyList(),
            null,
            null,
        )
}
