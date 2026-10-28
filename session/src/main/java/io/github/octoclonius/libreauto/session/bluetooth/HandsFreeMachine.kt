package io.github.octoclonius.libreauto.session.bluetooth

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile

internal enum class HandsFreeState(val timeoutMillis: Long) {
    Enabling(4_000),
    WaitingForProfile(4_000),
    RequestingPairing(120_000),
    Checking(60_000),
    Preparing(10_000),
    Pairing(60_000),
    ConnectingBluetooth(60_000),
    Connecting(60_000),
    Connected(3_000),
    Monitoring(10_000),
    CancelingPairing(0),
    Unpaired(0),
}

internal enum class HandsFreeEvent {
    Enabled,
    EnablingFailed,
    Disabled,
    ProfileReady,
    CarCannotBePaired,
    CarDelayingPairing,
    CarReadyForPairing,
    Paired,
    Unpaired,
    Connected,
    Disconnected,
    Timeout,
}

internal enum class HandsFreeAction {
    CheckEnabled,
    RequestProfile,
    RequestPairing,
    Pair,
    ConnectBluetooth,
    AllowHandsFree,
    CancelPairing,
}

internal data class PhoneBluetoothState(
    val isProfileReady: Boolean,
    val bondState: Int,
    val handsFreeState: Int,
)

internal data class HandsFreeStep(val state: HandsFreeState, val delayMillis: Long)

internal sealed interface HandsFreeEntry {

    data class SwitchTo(val state: HandsFreeState) : HandsFreeEntry

    data class Send(val event: HandsFreeEvent) : HandsFreeEntry

    data class Act(val action: HandsFreeAction, val delayMillis: Long = 0) : HandsFreeEntry

    data object Wait : HandsFreeEntry
}

private data class Transition(
    val next: HandsFreeState,
    val delayMillis: Long = 0,
    val maxCount: Int = 0,
    val nextAfterMax: HandsFreeState = next,
)

internal class HandsFreeMachine {

    private companion object {
        const val ENABLED_CHECK_DELAY_MILLIS = 1_000L
        const val ENABLING_RETRY_MILLIS = 10_000L
        const val PAIRING_REFUSED_RETRY_MILLIS = 1_000L
        const val PAIRING_DELAYED_RETRY_MILLIS = 120_000L
        const val RECHECK_DELAY_MILLIS = 5_000L
        const val PROFILE_ATTEMPTS = 3
        const val CHECK_ATTEMPTS = 2
        const val CONNECT_ATTEMPTS = 2
        const val CANCEL_ATTEMPTS = 2
        const val IMMEDIATE_CANCELS = 2
        const val CANCEL_BACKOFF_MILLIS = 1_000L
    }

    var state: HandsFreeState? = null
        private set

    var isCarPaired = false
        private set

    private var counted: Pair<HandsFreeState, HandsFreeEvent>? = null
    private var count = 0
    private var cancels = 0
    private var hasReachedMonitoring = false

    fun onEvent(event: HandsFreeEvent): HandsFreeStep? {
        val current = state ?: return null
        if (event == HandsFreeEvent.Unpaired && hasReachedMonitoring) {
            counted = null
            return HandsFreeStep(HandsFreeState.Unpaired, 0)
        }
        val transition = transition(current, event) ?: return null
        val key = current to event
        if (key != counted) {
            counted = key
            count = 0
        }
        count++
        return if (transition.maxCount == 0 || count <= transition.maxCount) {
            HandsFreeStep(transition.next, transition.delayMillis)
        } else {
            HandsFreeStep(transition.nextAfterMax, 0)
        }
    }

    fun onSwitch() {
        counted = null
    }

    fun onPairingResponse(isAlreadyPaired: Boolean) {
        isCarPaired = isAlreadyPaired
    }

    fun enter(next: HandsFreeState, phone: PhoneBluetoothState): HandsFreeEntry {
        state = next
        val isBonding = phone.bondState == BluetoothDevice.BOND_BONDING
        val isBonded = phone.bondState == BluetoothDevice.BOND_BONDED
        val isConnected = phone.handsFreeState == BluetoothProfile.STATE_CONNECTED
        val isConnecting = phone.handsFreeState == BluetoothProfile.STATE_CONNECTING
        val needsProfile =
            next in
                setOf(
                    HandsFreeState.Checking,
                    HandsFreeState.ConnectingBluetooth,
                    HandsFreeState.Connecting,
                    HandsFreeState.Monitoring,
                )
        if (needsProfile && !phone.isProfileReady) {
            return HandsFreeEntry.SwitchTo(HandsFreeState.Enabling)
        }
        return when (next) {
            HandsFreeState.Enabling ->
                HandsFreeEntry.Act(HandsFreeAction.CheckEnabled, ENABLED_CHECK_DELAY_MILLIS)
            HandsFreeState.WaitingForProfile ->
                if (phone.isProfileReady) HandsFreeEntry.Send(HandsFreeEvent.ProfileReady)
                else HandsFreeEntry.Act(HandsFreeAction.RequestProfile)
            HandsFreeState.RequestingPairing -> {
                isCarPaired = false
                if (isBonding) HandsFreeEntry.SwitchTo(HandsFreeState.CancelingPairing)
                else HandsFreeEntry.Act(HandsFreeAction.RequestPairing)
            }
            HandsFreeState.Checking ->
                when {
                    isConnected -> HandsFreeEntry.SwitchTo(HandsFreeState.Connected)
                    isConnecting && isBonded && isCarPaired -> HandsFreeEntry.Wait
                    else -> HandsFreeEntry.SwitchTo(HandsFreeState.Preparing)
                }
            HandsFreeState.Preparing ->
                when {
                    isBonding -> HandsFreeEntry.SwitchTo(HandsFreeState.CancelingPairing)
                    !isBonded -> HandsFreeEntry.SwitchTo(HandsFreeState.Pairing)
                    isCarPaired -> HandsFreeEntry.SwitchTo(HandsFreeState.Connecting)
                    else -> HandsFreeEntry.SwitchTo(HandsFreeState.ConnectingBluetooth)
                }
            HandsFreeState.Pairing -> HandsFreeEntry.Act(HandsFreeAction.Pair)
            HandsFreeState.ConnectingBluetooth ->
                HandsFreeEntry.Act(HandsFreeAction.ConnectBluetooth)
            HandsFreeState.Connecting -> {
                if (!isBonded) return HandsFreeEntry.SwitchTo(HandsFreeState.RequestingPairing)
                isCarPaired = true
                when {
                    isConnected -> HandsFreeEntry.SwitchTo(HandsFreeState.Connected)
                    isConnecting -> HandsFreeEntry.Wait
                    else -> HandsFreeEntry.Act(HandsFreeAction.AllowHandsFree)
                }
            }
            HandsFreeState.Connected -> {
                isCarPaired = true
                cancels = 0
                HandsFreeEntry.Wait
            }
            HandsFreeState.Monitoring -> {
                hasReachedMonitoring = true
                if (isConnected) HandsFreeEntry.Wait
                else HandsFreeEntry.SwitchTo(HandsFreeState.Checking)
            }
            HandsFreeState.CancelingPairing -> {
                cancels++
                val delay =
                    if (cancels <= IMMEDIATE_CANCELS) 0
                    else CANCEL_BACKOFF_MILLIS shl (cancels - IMMEDIATE_CANCELS - 1)
                HandsFreeEntry.Act(HandsFreeAction.CancelPairing, delay)
            }
            HandsFreeState.Unpaired -> HandsFreeEntry.Wait
        }
    }

    private fun transition(state: HandsFreeState, event: HandsFreeEvent): Transition? {
        if (event == HandsFreeEvent.Disabled) {
            return when (state) {
                HandsFreeState.Enabling ->
                    Transition(HandsFreeState.Enabling, ENABLING_RETRY_MILLIS)
                HandsFreeState.Unpaired -> null
                else -> Transition(HandsFreeState.Enabling)
            }
        }
        return when (state) {
            HandsFreeState.Enabling ->
                when (event) {
                    HandsFreeEvent.Timeout -> Transition(HandsFreeState.Enabling)
                    HandsFreeEvent.EnablingFailed ->
                        Transition(HandsFreeState.Enabling, ENABLING_RETRY_MILLIS)
                    HandsFreeEvent.Enabled -> Transition(HandsFreeState.WaitingForProfile)
                    else -> null
                }
            HandsFreeState.WaitingForProfile ->
                when (event) {
                    HandsFreeEvent.Timeout ->
                        Transition(
                            HandsFreeState.WaitingForProfile,
                            maxCount = PROFILE_ATTEMPTS,
                            nextAfterMax = HandsFreeState.Enabling,
                        )
                    HandsFreeEvent.ProfileReady -> Transition(HandsFreeState.RequestingPairing)
                    else -> null
                }
            HandsFreeState.RequestingPairing ->
                when (event) {
                    HandsFreeEvent.Timeout -> Transition(HandsFreeState.RequestingPairing)
                    HandsFreeEvent.CarCannotBePaired ->
                        Transition(HandsFreeState.RequestingPairing, PAIRING_REFUSED_RETRY_MILLIS)
                    HandsFreeEvent.CarDelayingPairing ->
                        Transition(HandsFreeState.RequestingPairing, PAIRING_DELAYED_RETRY_MILLIS)
                    HandsFreeEvent.CarReadyForPairing -> Transition(HandsFreeState.Checking)
                    else -> null
                }
            HandsFreeState.Checking ->
                when (event) {
                    HandsFreeEvent.Timeout ->
                        Transition(
                            HandsFreeState.Checking,
                            maxCount = CHECK_ATTEMPTS,
                            nextAfterMax = HandsFreeState.CancelingPairing,
                        )
                    HandsFreeEvent.Disconnected -> Transition(HandsFreeState.Preparing)
                    HandsFreeEvent.Connected -> Transition(HandsFreeState.Connected)
                    else -> null
                }
            HandsFreeState.Preparing ->
                when (event) {
                    HandsFreeEvent.Timeout -> Transition(HandsFreeState.CancelingPairing)
                    HandsFreeEvent.Paired -> Transition(HandsFreeState.Connecting)
                    HandsFreeEvent.Connected -> Transition(HandsFreeState.Connected)
                    HandsFreeEvent.Unpaired -> Transition(HandsFreeState.Pairing)
                    else -> null
                }
            HandsFreeState.Pairing ->
                when (event) {
                    HandsFreeEvent.Timeout,
                    HandsFreeEvent.Unpaired -> Transition(HandsFreeState.CancelingPairing)
                    HandsFreeEvent.Paired -> Transition(HandsFreeState.Connecting)
                    HandsFreeEvent.Connected -> Transition(HandsFreeState.Connected)
                    else -> null
                }
            HandsFreeState.ConnectingBluetooth ->
                when (event) {
                    HandsFreeEvent.Timeout -> Transition(HandsFreeState.CancelingPairing)
                    HandsFreeEvent.Paired -> Transition(HandsFreeState.Connecting)
                    HandsFreeEvent.Connected -> Transition(HandsFreeState.Connected)
                    else -> null
                }
            HandsFreeState.Connecting ->
                when (event) {
                    HandsFreeEvent.Timeout ->
                        Transition(
                            HandsFreeState.Connecting,
                            maxCount = CONNECT_ATTEMPTS,
                            nextAfterMax = HandsFreeState.CancelingPairing,
                        )
                    HandsFreeEvent.Connected -> Transition(HandsFreeState.Connected)
                    HandsFreeEvent.Unpaired -> Transition(HandsFreeState.RequestingPairing)
                    else -> null
                }
            HandsFreeState.Connected ->
                when (event) {
                    HandsFreeEvent.Timeout -> Transition(HandsFreeState.Monitoring)
                    HandsFreeEvent.Disconnected ->
                        Transition(HandsFreeState.Checking, RECHECK_DELAY_MILLIS)
                    HandsFreeEvent.Unpaired -> Transition(HandsFreeState.RequestingPairing)
                    else -> null
                }
            HandsFreeState.Monitoring ->
                when (event) {
                    HandsFreeEvent.Timeout -> Transition(HandsFreeState.Monitoring)
                    HandsFreeEvent.Disconnected ->
                        Transition(HandsFreeState.Checking, RECHECK_DELAY_MILLIS)
                    else -> null
                }
            HandsFreeState.CancelingPairing ->
                when (event) {
                    HandsFreeEvent.Timeout,
                    HandsFreeEvent.Paired ->
                        Transition(
                            HandsFreeState.CancelingPairing,
                            maxCount = CANCEL_ATTEMPTS,
                            nextAfterMax = HandsFreeState.RequestingPairing,
                        )
                    HandsFreeEvent.Connected -> Transition(HandsFreeState.Connected)
                    HandsFreeEvent.Unpaired -> Transition(HandsFreeState.RequestingPairing)
                    else -> null
                }
            HandsFreeState.Unpaired ->
                when (event) {
                    HandsFreeEvent.Paired -> Transition(HandsFreeState.RequestingPairing)
                    else -> null
                }
        }
    }
}
