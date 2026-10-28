package io.github.octoclonius.libreauto.session.bluetooth

import io.github.octoclonius.libreauto.session.bluetooth.HandsFreeEntry.Act
import io.github.octoclonius.libreauto.session.bluetooth.HandsFreeEntry.Send
import io.github.octoclonius.libreauto.session.bluetooth.HandsFreeEntry.SwitchTo
import io.github.octoclonius.libreauto.session.bluetooth.HandsFreeEntry.Wait
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HandsFreeMachineTest {

    private val bondNone = 10
    private val bondBonding = 11
    private val bondBonded = 12
    private val disconnected = 0
    private val connecting = 1
    private val connected = 2

    private val machine = HandsFreeMachine()

    @Test
    fun enablingChecksBluetoothAfterASecond() {
        assertEquals(
            Act(HandsFreeAction.CheckEnabled, 1_000),
            machine.enter(HandsFreeState.Enabling, phone()),
        )
    }

    @Test
    fun bluetoothTurningOnWaitsForTheProfile() {
        machine.enter(HandsFreeState.Enabling, phone())
        assertEquals(
            step(HandsFreeState.WaitingForProfile),
            machine.onEvent(HandsFreeEvent.Enabled),
        )
    }

    @Test
    fun aFailedEnableIsRetriedAfter10Seconds() {
        machine.enter(HandsFreeState.Enabling, phone())
        assertEquals(
            step(HandsFreeState.Enabling, 10_000),
            machine.onEvent(HandsFreeEvent.EnablingFailed),
        )
    }

    @Test
    fun aReadyProfileGoesOnToThePairingRequest() {
        assertEquals(
            Send(HandsFreeEvent.ProfileReady),
            machine.enter(HandsFreeState.WaitingForProfile, phone(isProfileReady = true)),
        )
        assertEquals(
            step(HandsFreeState.RequestingPairing),
            machine.onEvent(HandsFreeEvent.ProfileReady),
        )
    }

    @Test
    fun theProfileIsAwaitedThreeTimesThenBluetoothIsEnabledAgain() {
        assertEquals(
            Act(HandsFreeAction.RequestProfile),
            machine.enter(HandsFreeState.WaitingForProfile, phone()),
        )
        repeat(3) {
            assertEquals(
                step(HandsFreeState.WaitingForProfile),
                machine.onEvent(HandsFreeEvent.Timeout),
            )
        }
        assertEquals(step(HandsFreeState.Enabling), machine.onEvent(HandsFreeEvent.Timeout))
    }

    @Test
    fun thePairingRequestIsSentUnlessAPairingIsUnderWay() {
        assertEquals(
            Act(HandsFreeAction.RequestPairing),
            machine.enter(HandsFreeState.RequestingPairing, phone(isProfileReady = true)),
        )
        assertEquals(
            SwitchTo(HandsFreeState.CancelingPairing),
            machine.enter(
                HandsFreeState.RequestingPairing,
                phone(isProfileReady = true, bond = bondBonding),
            ),
        )
    }

    @Test
    fun theCarsAnswersDecideWhenToAskAgain() {
        machine.enter(HandsFreeState.RequestingPairing, phone(isProfileReady = true))
        assertEquals(
            step(HandsFreeState.RequestingPairing, 1_000),
            machine.onEvent(HandsFreeEvent.CarCannotBePaired),
        )
        assertEquals(
            step(HandsFreeState.RequestingPairing, 120_000),
            machine.onEvent(HandsFreeEvent.CarDelayingPairing),
        )
        assertEquals(
            step(HandsFreeState.Checking),
            machine.onEvent(HandsFreeEvent.CarReadyForPairing),
        )
    }

    @Test
    fun checkingFindsAConnectedCar() {
        assertEquals(
            SwitchTo(HandsFreeState.Connected),
            machine.enter(HandsFreeState.Checking, phone(true, bondBonded, connected)),
        )
    }

    @Test
    fun checkingWaitsForAConnectionUnderWayToACarThatIsPaired() {
        machine.onPairingResponse(isAlreadyPaired = true)
        assertEquals(
            Wait,
            machine.enter(HandsFreeState.Checking, phone(true, bondBonded, connecting)),
        )
    }

    @Test
    fun checkingPreparesOtherwise() {
        assertEquals(
            SwitchTo(HandsFreeState.Preparing),
            machine.enter(HandsFreeState.Checking, phone(true, bondBonded, connecting)),
        )
        assertEquals(
            SwitchTo(HandsFreeState.Preparing),
            machine.enter(HandsFreeState.Checking, phone(true, bondBonded, disconnected)),
        )
    }

    @Test
    fun withoutTheProfileBluetoothIsEnabledAgain() {
        assertEquals(
            SwitchTo(HandsFreeState.Enabling),
            machine.enter(HandsFreeState.Checking, phone(false, bondBonded, connected)),
        )
    }

    @Test
    fun preparingPairsAnUnpairedPhone() {
        assertEquals(
            SwitchTo(HandsFreeState.Pairing),
            machine.enter(HandsFreeState.Preparing, phone(true, bondNone)),
        )
        assertEquals(Act(HandsFreeAction.Pair), machine.enter(HandsFreeState.Pairing, phone()))
    }

    @Test
    fun preparingConnectsHandsFreeWhenBothSidesArePaired() {
        machine.onPairingResponse(isAlreadyPaired = true)
        assertEquals(
            SwitchTo(HandsFreeState.Connecting),
            machine.enter(HandsFreeState.Preparing, phone(true, bondBonded)),
        )
        assertEquals(
            Act(HandsFreeAction.AllowHandsFree),
            machine.enter(HandsFreeState.Connecting, phone(true, bondBonded)),
        )
    }

    @Test
    fun preparingConnectsBluetoothWhenOnlyThePhoneIsPaired() {
        assertEquals(
            SwitchTo(HandsFreeState.ConnectingBluetooth),
            machine.enter(HandsFreeState.Preparing, phone(true, bondBonded)),
        )
        assertEquals(
            Act(HandsFreeAction.ConnectBluetooth),
            machine.enter(HandsFreeState.ConnectingBluetooth, phone(true, bondBonded)),
        )
    }

    @Test
    fun aPairingDoneConnectsHandsFree() {
        machine.enter(HandsFreeState.Pairing, phone())
        assertEquals(step(HandsFreeState.Connecting), machine.onEvent(HandsFreeEvent.Paired))
    }

    @Test
    fun aRefusedPairingIsCanceledAndTheCarAskedAgain() {
        machine.enter(HandsFreeState.Pairing, phone())
        assertEquals(
            step(HandsFreeState.CancelingPairing),
            machine.onEvent(HandsFreeEvent.Unpaired),
        )
    }

    @Test
    fun connectingAnUnpairedPhoneAsksTheCarAgain() {
        assertEquals(
            SwitchTo(HandsFreeState.RequestingPairing),
            machine.enter(HandsFreeState.Connecting, phone(true, bondNone)),
        )
    }

    @Test
    fun connectingCountsTheCarAsPaired() {
        machine.enter(HandsFreeState.Connecting, phone(true, bondBonded))
        assertTrue(machine.isCarPaired)
        machine.enter(HandsFreeState.RequestingPairing, phone(true))
        assertFalse(machine.isCarPaired)
    }

    @Test
    fun connectingIsTriedTwiceMoreThenThePairingCanceled() {
        machine.enter(HandsFreeState.Connecting, phone(true, bondBonded))
        repeat(2) {
            assertEquals(step(HandsFreeState.Connecting), machine.onEvent(HandsFreeEvent.Timeout))
        }
        assertEquals(step(HandsFreeState.CancelingPairing), machine.onEvent(HandsFreeEvent.Timeout))
    }

    @Test
    fun aDifferentTransitionStartsTheCountAgain() {
        machine.enter(HandsFreeState.Connecting, phone(true, bondBonded))
        machine.onEvent(HandsFreeEvent.Timeout)
        machine.onEvent(HandsFreeEvent.Timeout)
        machine.onEvent(HandsFreeEvent.Disabled)
        machine.enter(HandsFreeState.Connecting, phone(true, bondBonded))
        assertEquals(step(HandsFreeState.Connecting), machine.onEvent(HandsFreeEvent.Timeout))
    }

    @Test
    fun aSwitchStartsTheCountAgain() {
        machine.enter(HandsFreeState.Connecting, phone(true, bondBonded))
        machine.onEvent(HandsFreeEvent.Timeout)
        machine.onEvent(HandsFreeEvent.Timeout)
        machine.onSwitch()
        assertEquals(step(HandsFreeState.Connecting), machine.onEvent(HandsFreeEvent.Timeout))
    }

    @Test
    fun aConnectedCarIsMonitored() {
        machine.enter(HandsFreeState.Connected, phone(true, bondBonded, connected))
        assertEquals(step(HandsFreeState.Monitoring), machine.onEvent(HandsFreeEvent.Timeout))
        assertEquals(
            Wait,
            machine.enter(HandsFreeState.Monitoring, phone(true, bondBonded, connected)),
        )
        assertEquals(step(HandsFreeState.Monitoring), machine.onEvent(HandsFreeEvent.Timeout))
        assertEquals(
            SwitchTo(HandsFreeState.Checking),
            machine.enter(HandsFreeState.Monitoring, phone(true, bondBonded, disconnected)),
        )
    }

    @Test
    fun aDisconnectionIsCheckedAfter5Seconds() {
        machine.enter(HandsFreeState.Connected, phone(true, bondBonded, connected))
        assertEquals(
            step(HandsFreeState.Checking, 5_000),
            machine.onEvent(HandsFreeEvent.Disconnected),
        )
    }

    @Test
    fun anUnpairingBeforeMonitoringAsksTheCarAgain() {
        machine.enter(HandsFreeState.Connected, phone(true, bondBonded, connected))
        assertEquals(
            step(HandsFreeState.RequestingPairing),
            machine.onEvent(HandsFreeEvent.Unpaired),
        )
    }

    @Test
    fun anUnpairingAfterMonitoringStopsForTheSession() {
        machine.enter(HandsFreeState.Monitoring, phone(true, bondBonded, connected))
        machine.enter(HandsFreeState.Connecting, phone(true, bondBonded))
        assertEquals(step(HandsFreeState.Unpaired), machine.onEvent(HandsFreeEvent.Unpaired))
        assertEquals(Wait, machine.enter(HandsFreeState.Unpaired, phone()))
        assertNull(machine.onEvent(HandsFreeEvent.Disabled))
        assertEquals(step(HandsFreeState.RequestingPairing), machine.onEvent(HandsFreeEvent.Paired))
    }

    @Test
    fun bluetoothTurningOffEnablesItAgain() {
        machine.enter(HandsFreeState.Connected, phone(true, bondBonded, connected))
        assertEquals(step(HandsFreeState.Enabling), machine.onEvent(HandsFreeEvent.Disabled))
        machine.enter(HandsFreeState.Enabling, phone())
        assertEquals(
            step(HandsFreeState.Enabling, 10_000),
            machine.onEvent(HandsFreeEvent.Disabled),
        )
    }

    @Test
    fun cancelingBacksOffAfterTwoImmediateTries() {
        val delays =
            (1..5).map {
                (machine.enter(HandsFreeState.CancelingPairing, phone()) as Act).delayMillis
            }
        assertEquals(listOf(0L, 0L, 1_000L, 2_000L, 4_000L), delays)
    }

    @Test
    fun aConnectionResetsTheBackOff() {
        repeat(3) { machine.enter(HandsFreeState.CancelingPairing, phone()) }
        machine.enter(HandsFreeState.Connected, phone(true, bondBonded, connected))
        assertEquals(
            Act(HandsFreeAction.CancelPairing, 0),
            machine.enter(HandsFreeState.CancelingPairing, phone()),
        )
    }

    @Test
    fun cancelingIsTimedTwiceMoreThenTheCarAskedAgain() {
        machine.enter(HandsFreeState.CancelingPairing, phone())
        repeat(2) {
            assertEquals(
                step(HandsFreeState.CancelingPairing),
                machine.onEvent(HandsFreeEvent.Timeout),
            )
        }
        assertEquals(
            step(HandsFreeState.RequestingPairing),
            machine.onEvent(HandsFreeEvent.Timeout),
        )
    }

    @Test
    fun nothingHappensBeforeAState() {
        assertNull(machine.onEvent(HandsFreeEvent.Enabled))
    }

    @Test
    fun theStatesTimeoutsAreAndroidAutos() {
        assertEquals(4_000, HandsFreeState.Enabling.timeoutMillis)
        assertEquals(120_000, HandsFreeState.RequestingPairing.timeoutMillis)
        assertEquals(10_000, HandsFreeState.Preparing.timeoutMillis)
        assertEquals(3_000, HandsFreeState.Connected.timeoutMillis)
        assertEquals(0, HandsFreeState.CancelingPairing.timeoutMillis)
    }

    private fun step(state: HandsFreeState, delayMillis: Long = 0) =
        HandsFreeStep(state, delayMillis)

    private fun phone(
        isProfileReady: Boolean = false,
        bond: Int = bondNone,
        handsFree: Int = disconnected,
    ) = PhoneBluetoothState(isProfileReady, bond, handsFree)
}
