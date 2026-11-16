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
            assertFalse(buttons.keypad.isEnabled)
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

    @Test
    fun aRingingCallOpensItsRingingViewAndOthersOpenTheCall() {
        assertEquals(CallScreenEntry.Ringing(1), callScreenEntry(call(1, CallStatus.Ringing)))
        assertEquals(CallScreenEntry.InCall, callScreenEntry(call(1, CallStatus.Active)))
    }

    @Test
    fun aRingingViewStacksOnTopOfTheCall() {
        val calls = listOf(call(0, CallStatus.Active), call(1, CallStatus.Ringing))
        val stack = pushedCallScreen(listOf(CallScreenEntry.InCall), ringing(1), calls)
        assertEquals(listOf(CallScreenEntry.InCall, ringing(1)), stack)
    }

    @Test
    fun ringingViewsStackWithoutLimit() {
        val calls =
            listOf(
                call(0, CallStatus.Active),
                call(1, CallStatus.Ringing),
                call(2, CallStatus.Ringing),
            )
        val stack =
            pushedCallScreen(
                pushedCallScreen(listOf(CallScreenEntry.InCall), ringing(1), calls),
                ringing(2),
                calls,
            )
        assertEquals(listOf(CallScreenEntry.InCall, ringing(1), ringing(2)), stack)
    }

    @Test
    fun openingWhatIsAlreadyShownChangesNothing() {
        val calls = listOf(call(1, CallStatus.Ringing))
        assertEquals(listOf(ringing(1)), pushedCallScreen(listOf(ringing(1)), ringing(1), calls))
    }

    @Test
    fun nothingOpensWithoutItsCall() {
        assertEquals(
            emptyList<CallScreenEntry>(),
            pushedCallScreen(emptyList(), ringing(1), emptyList()),
        )
        assertEquals(
            emptyList<CallScreenEntry>(),
            pushedCallScreen(
                emptyList(),
                CallScreenEntry.InCall,
                listOf(call(1, CallStatus.Ringing)),
            ),
        )
    }

    @Test
    fun anAnsweredCallTurnsIntoTheCallScreenOnce() {
        val calls = listOf(call(0, CallStatus.Holding), call(1, CallStatus.Active))
        assertEquals(
            listOf(CallScreenEntry.InCall),
            reconciledCallScreen(listOf(CallScreenEntry.InCall, ringing(1)), calls),
        )
        assertEquals(
            listOf(CallScreenEntry.InCall),
            reconciledCallScreen(listOf(ringing(1)), calls),
        )
    }

    @Test
    fun aDeclinedOrMissedCallLeavesTheStack() {
        val calls = listOf(call(0, CallStatus.Active))
        assertEquals(
            listOf(CallScreenEntry.InCall),
            reconciledCallScreen(listOf(CallScreenEntry.InCall, ringing(1)), calls),
        )
        assertEquals(
            emptyList<CallScreenEntry>(),
            reconciledCallScreen(listOf(ringing(1)), listOf(call(1, CallStatus.Disconnecting))),
        )
    }

    @Test
    fun theCallScreenLeavesTheStackOnceNoCallIsLeftToShow() {
        assertEquals(
            listOf(ringing(1)),
            reconciledCallScreen(
                listOf(CallScreenEntry.InCall, ringing(1)),
                listOf(call(1, CallStatus.Ringing)),
            ),
        )
    }

    @Test
    fun theRingingViewOffersToEndTheActiveCall() {
        val ringing = call(1, CallStatus.Ringing)
        assertEquals(
            CallScreenView.Ringing(ringing, canAnswerAndRelease = true),
            callScreenView(ringing(1), listOf(call(0, CallStatus.Active), ringing), Keypad()),
        )
    }

    @Test
    fun aHeldCallIsNotEndedToAnswer() {
        val ringing = call(1, CallStatus.Ringing)
        assertEquals(
            CallScreenView.Ringing(ringing, canAnswerAndRelease = false),
            callScreenView(ringing(1), listOf(call(0, CallStatus.Holding), ringing), Keypad()),
        )
    }

    @Test
    fun theCallScreenShowsTheCallsInOrder() {
        val active = call(0, CallStatus.Active)
        val calls = listOf(active, call(1, CallStatus.Ringing))
        assertEquals(
            CallScreenView.InCall(CallScreenCalls(active, null), keypad = null),
            callScreenView(CallScreenEntry.InCall, calls, Keypad()),
        )
        assertNull(callScreenView(null, calls, Keypad()))
    }

    @Test
    fun theKeypadButtonOpensTheKeypad() {
        assertEquals(
            CallButton(CallAction.SetKeypadShown(true), isEnabled = true),
            buttons(call(0, CallStatus.Holding)).keypad,
        )
    }

    @Test
    fun theKeypadButtonIsCheckedWhileTheKeypadIsShownAndClosesIt() {
        assertEquals(
            CallButton(CallAction.SetKeypadShown(false), isEnabled = true, isChecked = true),
            buttons(call(0, CallStatus.Active), isKeypadShown = true).keypad,
        )
    }

    @Test
    fun anOpenKeypadReplacesTheCallerWithItsDigits() {
        val active = call(0, CallStatus.Active)
        assertEquals(
            CallScreenView.InCall(CallScreenCalls(active, null), CallScreenKeypad("12", 0)),
            callScreenView(CallScreenEntry.InCall, listOf(active), Keypad(true, "12")),
        )
    }

    @Test
    fun aClosedKeypadIsNotShown() {
        val view = callScreenView(inCall, listOf(call(0, CallStatus.Active)), Keypad(false, "12"))
        assertNull((view as CallScreenView.InCall).keypad)
    }

    @Test
    fun theKeypadIsNotShownOnTheRingingView() {
        val calls = listOf(call(0, CallStatus.Active), call(1, CallStatus.Ringing))
        assertEquals(
            CallScreenView.Ringing(calls[1], canAnswerAndRelease = true),
            callScreenView(ringing(1), calls, Keypad(isShown = true)),
        )
    }

    @Test
    fun anEndingCallShowsItsEndInsteadOfTheKeypad() {
        for (status in listOf(CallStatus.Disconnecting, CallStatus.Disconnected)) {
            val view =
                callScreenView(
                    inCall,
                    listOf(call(0, status), call(1, CallStatus.Holding)),
                    Keypad(isShown = true),
                )
            assertNull((view as CallScreenView.InCall).keypad)
        }
    }

    @Test
    fun tonesGoToTheDialingCallFirst() {
        val calls = listOf(call(0, CallStatus.Active), call(1, CallStatus.Dialing))
        assertEquals(1, shownKeypad(calls)?.toneCallId)
    }

    @Test
    fun tonesGoToTheActiveCallWhenNoneIsDialing() {
        val calls =
            listOf(
                call(0, CallStatus.Holding),
                call(1, CallStatus.Active),
                call(2, CallStatus.Ringing),
            )
        assertEquals(1, shownKeypad(calls)?.toneCallId)
    }

    @Test
    fun aHeldCallAloneGetsNoTones() {
        val keypad = shownKeypad(listOf(call(0, CallStatus.Holding)))
        assertEquals(CallScreenKeypad("", toneCallId = null), keypad)
    }

    @Test
    fun theKeypadClosesWhenACallStartsRinging() {
        val previous = listOf(call(0, CallStatus.Active))
        val calls = previous + call(1, CallStatus.Ringing)
        assertEquals(Keypad(false, "12"), reconciledKeypad(Keypad(true, "12"), previous, calls))
    }

    @Test
    fun theKeypadStaysOpenWhileTheSameCallKeepsRinging() {
        val calls = listOf(call(0, CallStatus.Active), call(1, CallStatus.Ringing))
        assertEquals(Keypad(true, "12"), reconciledKeypad(Keypad(true, "12"), calls, calls))
    }

    @Test
    fun theKeypadKeepsItsDigitsAcrossCallChanges() {
        val previous = listOf(call(0, CallStatus.Active), call(1, CallStatus.Holding))
        val calls = listOf(call(0, CallStatus.Disconnected), call(1, CallStatus.Active))
        assertEquals(Keypad(true, "12"), reconciledKeypad(Keypad(true, "12"), previous, calls))
    }

    @Test
    fun theKeypadResetsOnceNoCallIsLeft() {
        val previous = listOf(call(0, CallStatus.Disconnected))
        assertEquals(Keypad(), reconciledKeypad(Keypad(true, "12"), previous, emptyList()))
    }

    @Test
    fun noCallsDrawNothing() {
        assertEquals(CallScreenSurfaces(), callScreenSurfaces(null, null, emptyList()))
    }

    @Test
    fun theOngoingSurfaceDrawsTheCallScreensCall() {
        val calls = listOf(call(0, CallStatus.Holding), call(1, CallStatus.Dialing))
        assertEquals(1, callScreenSurfaces(null, null, calls).ongoingCallId)
    }

    @Test
    fun theRingingSurfaceDrawsTheOpenRingingCallFirst() {
        val calls = ringingCalls(1, 2, 3)
        assertEquals(3, callScreenSurfaces(ringing(3), calls[1], calls).ringingCallId)
    }

    @Test
    fun theRingingSurfaceOtherwiseDrawsThePopUpsCall() {
        val calls = ringingCalls(1, 2)
        assertEquals(2, callScreenSurfaces(inCall, calls[1], calls).ringingCallId)
    }

    @Test
    fun theRingingSurfaceOtherwiseDrawsTheFirstRingingCall() {
        val calls = listOf(call(0, CallStatus.Active)) + ringingCalls(1, 2)
        assertEquals(1, callScreenSurfaces(null, null, calls).ringingCallId)
        assertEquals(1, callScreenSurfaces(null, call(5, CallStatus.Active), calls).ringingCallId)
    }

    @Test
    fun theSurfaceShownFollowsTheCallScreensTop() {
        val calls = listOf(call(0, CallStatus.Active)) + ringingCalls(1)
        assertNull(callScreenSurfaces(null, null, calls).shown)
        assertEquals(
            CallScreenSurface.Ongoing,
            callScreenSurfaces(inCall, null, calls).shown,
        )
        assertEquals(
            CallScreenSurface.Ringing,
            callScreenSurfaces(ringing(1), null, calls).shown,
        )
    }

    private val inCall = CallScreenEntry.InCall

    private fun ringingCalls(vararg ids: Int) = ids.map { call(it, CallStatus.Ringing) }

    private fun ringing(id: Int) = CallScreenEntry.Ringing(id)

    private fun shownKeypad(calls: List<PhoneCall>): CallScreenKeypad? =
        (callScreenView(inCall, calls, Keypad(isShown = true)) as CallScreenView.InCall).keypad

    private fun buttons(
        primary: PhoneCall,
        secondary: PhoneCall? = null,
        isMuted: Boolean = false,
        isKeypadShown: Boolean = false,
    ): CallScreenButtons =
        callScreenButtons(CallScreenCalls(primary, secondary), isMuted, isKeypadShown)

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
