package io.github.octoclonius.libreauto.session.bluetooth

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.util.Log
import io.github.octoclonius.libreauto.protocol.MessageStatus
import io.github.octoclonius.libreauto.protocol.bluetooth.PairingResponse
import java.io.Closeable
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.launch

internal class CarHandsFree(
    context: Context,
    private val privileged: PrivilegedBluetooth,
    private val scope: CoroutineScope,
    private val carAddress: String,
    private val pairingMethod: Int,
    private val sendPairingRequest: (phoneAddress: String, pairingMethod: Int) -> Unit,
    private val sendAuthenticationResult: (status: Int) -> Unit,
) : Closeable {

    companion object {
        private const val TAG = "CarHandsFree"
        private const val CANCEL_TIMEOUT_MILLIS = 6_000L
        private const val PASSKEY_DIGITS = 6
        private val CAR_DATA_VARIANTS =
            setOf(
                BluetoothDevice.PAIRING_VARIANT_PIN,
                BluetoothDevice.PAIRING_VARIANT_PASSKEY_CONFIRMATION,
            )
    }

    private val machine = HandsFreeMachine()
    private val timeouts = Job(scope.coroutineContext.job)
    private val scheduled = Job(scope.coroutineContext.job)
    private var isClosed = false
    private var isPairingRequestSent = false
    private var pairingVariant: Int? = null
    private var pairingKey = Int.MIN_VALUE
    private var authenticationData: String? = null

    private val phone =
        AndroidBluetooth(
            context,
            carAddress,
            scope,
            object : AndroidBluetooth.Listener {
                override fun onEnabled() = onEvent(HandsFreeEvent.Enabled)

                override fun onDisabled() = onEvent(HandsFreeEvent.Disabled)

                override fun onProfileReady() = onEvent(HandsFreeEvent.ProfileReady)

                override fun onBondState(state: Int) {
                    when (state) {
                        BluetoothDevice.BOND_BONDED -> onEvent(HandsFreeEvent.Paired)
                        BluetoothDevice.BOND_NONE -> onEvent(HandsFreeEvent.Unpaired)
                    }
                }

                override fun onHandsFreeState(state: Int) {
                    when (state) {
                        BluetoothProfile.STATE_CONNECTED -> onEvent(HandsFreeEvent.Connected)
                        BluetoothProfile.STATE_DISCONNECTED -> onEvent(HandsFreeEvent.Disconnected)
                    }
                }

                override fun onPairingRequest(variant: Int, key: Int) {
                    Log.i(TAG, "Android asks to authenticate the pairing with variant $variant")
                    pairingVariant = variant
                    pairingKey = key
                    authenticateIfReady()
                }

                override fun onKeyMissing() {
                    Log.i(TAG, "The car lost the pairing's key; removing the pairing")
                    privileged.removePairing(carAddress)
                }
            },
        )

    fun start() {
        if (isClosed) return
        Log.i(TAG, "Connecting hands-free to the car $carAddress, pairing method $pairingMethod")
        privileged.grantPermission()
        phone.start()
        enter(HandsFreeState.Enabling)
    }

    fun onPairingResponse(response: PairingResponse) {
        if (isClosed) return
        if (!isPairingRequestSent) {
            Log.w(TAG, "A pairing response without a pairing request")
            return
        }
        Log.i(
            TAG,
            "The car answers the pairing request: status ${response.status}, " +
                "already paired ${response.isAlreadyPaired}",
        )
        when (response.status) {
            MessageStatus.SUCCESS -> {
                machine.onPairingResponse(response.isAlreadyPaired)
                onEvent(HandsFreeEvent.CarReadyForPairing)
            }
            MessageStatus.BLUETOOTH_PAIRING_DELAYED -> onEvent(HandsFreeEvent.CarDelayingPairing)
            else -> Log.w(TAG, "A pairing response with status ${response.status}")
        }
    }

    fun onAuthenticationData(data: String?) {
        if (isClosed) return
        if (data == null) {
            Log.w(TAG, "Authentication data from the car without data")
            return
        }
        Log.i(TAG, "The car sent its authentication data")
        authenticationData = data
        authenticateIfReady()
    }

    override fun close() {
        if (isClosed) return
        isClosed = true
        timeouts.cancel()
        scheduled.cancel()
        phone.close()
        Log.i(TAG, "Stopped connecting hands-free in ${machine.state}")
    }

    private fun onEvent(event: HandsFreeEvent) {
        if (isClosed) return
        val from = machine.state
        val step = machine.onEvent(event) ?: return
        Log.i(TAG, "$event in $from: ${step.state} after ${step.delayMillis} ms")
        cancelScheduled()
        if (step.delayMillis == 0L) {
            enter(step.state)
        } else {
            schedule(scheduled, step.delayMillis) { enter(step.state) }
        }
    }

    private fun switchTo(state: HandsFreeState) {
        Log.i(TAG, "Switching from ${machine.state} to $state")
        machine.onSwitch()
        cancelScheduled()
        enter(state)
    }

    private fun cancelScheduled() {
        scheduled.cancelChildren()
        timeouts.cancelChildren()
    }

    private fun schedule(group: Job, delayMillis: Long, block: () -> Unit) {
        scope.launch(group) {
            delay(delayMillis)
            block()
        }
    }

    private fun enter(state: HandsFreeState) {
        if (isClosed) return
        timeouts.cancelChildren()
        if (state.timeoutMillis > 0) {
            schedule(timeouts, state.timeoutMillis) { onEvent(HandsFreeEvent.Timeout) }
        }
        val phoneState = phone.state()
        Log.i(TAG, "Entering $state with $phoneState")
        when (val entry = machine.enter(state, phoneState)) {
            is HandsFreeEntry.SwitchTo -> switchTo(entry.state)
            is HandsFreeEntry.Send -> onEvent(entry.event)
            is HandsFreeEntry.Act ->
                if (entry.delayMillis == 0L) {
                    perform(entry.action)
                } else {
                    schedule(scheduled, entry.delayMillis) { perform(entry.action) }
                }
            HandsFreeEntry.Wait -> {}
        }
    }

    private fun perform(action: HandsFreeAction) {
        if (isClosed) return
        when (action) {
            HandsFreeAction.CheckEnabled ->
                if (phone.isEnabled) {
                    switchTo(HandsFreeState.WaitingForProfile)
                } else {
                    phone.closeProfile()
                    if (!privileged.turnOn()) onEvent(HandsFreeEvent.EnablingFailed)
                }
            HandsFreeAction.RequestProfile -> phone.requestProfile()
            HandsFreeAction.RequestPairing -> requestPairing()
            HandsFreeAction.Pair ->
                if (!privileged.pair(carAddress)) {
                    Log.w(TAG, "Android didn't start pairing with the car")
                    switchTo(HandsFreeState.CancelingPairing)
                }
            HandsFreeAction.ConnectBluetooth ->
                if (!privileged.connectHandsFree(carAddress)) {
                    Log.w(TAG, "Allowing and connecting hands-free failed")
                }
            HandsFreeAction.AllowHandsFree ->
                if (!privileged.allowHandsFree(carAddress)) {
                    Log.w(TAG, "Allowing hands-free failed")
                }
            HandsFreeAction.CancelPairing -> cancelPairing()
        }
    }

    private fun requestPairing() {
        clearAuthentication()
        val phoneAddress = privileged.address()
        if (phoneAddress == null) {
            Log.w(TAG, "The phone's Bluetooth address is unknown")
            onEvent(HandsFreeEvent.CarCannotBePaired)
            return
        }
        Log.i(TAG, "Asking the car to get ready for pairing with method $pairingMethod")
        isPairingRequestSent = true
        sendPairingRequest(phoneAddress, pairingMethod)
    }

    private fun cancelPairing() {
        if (phone.state().bondState != BluetoothDevice.BOND_BONDING) {
            switchTo(HandsFreeState.RequestingPairing)
            return
        }
        Log.i(TAG, "Canceling the pairing under way")
        privileged.cancelPairing(carAddress)
        schedule(scheduled, CANCEL_TIMEOUT_MILLIS) { onEvent(HandsFreeEvent.Timeout) }
    }

    private fun authenticateIfReady() {
        val variant = pairingVariant ?: return
        val data = authenticationData
        if (variant in CAR_DATA_VARIANTS && data == null) return
        val (isDone, status) =
            when (variant) {
                BluetoothDevice.PAIRING_VARIANT_PIN ->
                    privileged.setPin(carAddress, checkNotNull(data)) to
                        MessageStatus.SUCCESS
                BluetoothDevice.PAIRING_VARIANT_PASSKEY_CONFIRMATION -> {
                    val passkey = String.format(Locale.US, "%0${PASSKEY_DIGITS}d", pairingKey)
                    if (passkey == data) {
                        privileged.confirmPairing(carAddress, true) to
                            MessageStatus.SUCCESS
                    } else {
                        Log.w(TAG, "The car's passkey doesn't match Android's")
                        privileged.confirmPairing(carAddress, false)
                        false to MessageStatus.BLUETOOTH_AUTH_DATA_MISMATCH
                    }
                }
                else -> {
                    Log.w(TAG, "Pairing variant $variant can't be authenticated")
                    false to MessageStatus.BLUETOOTH_INVALID_PAIRING_METHOD
                }
            }
        Log.i(
            TAG,
            if (isDone) "Authenticated the pairing" else "The pairing's authentication failed",
        )
        clearAuthentication()
        if (!isDone) privileged.cancelPairing(carAddress)
        sendAuthenticationResult(status)
    }

    private fun clearAuthentication() {
        pairingVariant = null
        pairingKey = Int.MIN_VALUE
        authenticationData = null
    }
}
