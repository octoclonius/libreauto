package io.github.octoclonius.libreauto.projection.bluetooth

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.util.Log
import io.github.octoclonius.libreauto.projection.channel.CarChannel
import io.github.octoclonius.libreauto.projection.channel.ChannelSender
import io.github.octoclonius.libreauto.projection.channel.isChannelOpened
import io.github.octoclonius.libreauto.projection.permissions.PermissionsRepository
import io.github.octoclonius.libreauto.projection.privileged.PrivilegedServiceConnection
import io.github.octoclonius.libreauto.protocol.ChannelMessage
import io.github.octoclonius.libreauto.protocol.bluetooth.BluetoothMessageType
import io.github.octoclonius.libreauto.protocol.bluetooth.CarBluetooth
import io.github.octoclonius.libreauto.protocol.bluetooth.authenticationResult
import io.github.octoclonius.libreauto.protocol.bluetooth.isAuthenticationResultSent
import io.github.octoclonius.libreauto.protocol.bluetooth.pairingRequest
import io.github.octoclonius.libreauto.protocol.bluetooth.parseAuthenticationData
import io.github.octoclonius.libreauto.protocol.bluetooth.parsePairingResponse
import io.github.octoclonius.libreauto.protocol.control.MessageType
import io.github.octoclonius.libreauto.protocol.control.ProtocolVersion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

internal class BluetoothChannel(
    private val context: Context,
    private val sender: ChannelSender,
    private val car: CarBluetooth,
    private val version: ProtocolVersion,
    private val privileged: PrivilegedServiceConnection,
    private val permissions: PermissionsRepository,
    private val scope: CoroutineScope,
) : CarChannel {

    companion object {
        private const val TAG = "BluetoothChannel"
    }

    private var handsFreeJob: Job? = null
    private var handsFree: CarHandsFree? = null

    override val channel: Int
        get() = sender.channel

    override fun open() {
        Log.i(TAG, "Opening the Bluetooth channel $channel")
        sender.sendOpenRequest()
    }

    override fun onMessage(message: ChannelMessage): Boolean {
        when (message.type) {
            MessageType.CHANNEL_OPEN_RESPONSE -> onOpenResponse(message)
            BluetoothMessageType.PAIRING_RESPONSE ->
                handsFree?.onPairingResponse(parsePairingResponse(message.body))
            BluetoothMessageType.AUTHENTICATION_DATA ->
                handsFree?.onAuthenticationData(parseAuthenticationData(message.body))
            else -> return false
        }
        return true
    }

    override fun close() {
        handsFreeJob?.cancel()
        handsFreeJob = null
        handsFree?.close()
        handsFree = null
    }

    private fun onOpenResponse(message: ChannelMessage) {
        if (!isChannelOpened(TAG, message, "Bluetooth") || handsFreeJob != null) return
        val address = car.carAddress.uppercase()
        if (!BluetoothAdapter.checkBluetoothAddress(address)) {
            Log.w(TAG, "The car's Bluetooth address \"${car.carAddress}\" isn't one")
            return
        }
        handsFreeJob = scope.launch {
            if (!permissions.state.value.isNearbyDevicesAllowed) {
                Log.i(TAG, "Hands-free waits until LibreAuto may connect to nearby devices")
            }
            permissions.state.first { it.isNearbyDevicesAllowed }
            startHandsFree(address)
        }
    }

    private fun startHandsFree(address: String) {
        handsFree =
            CarHandsFree(
                    context,
                    PrivilegedBluetooth(privileged),
                    scope,
                    address,
                    car.pairingMethod,
                    { phoneAddress, method ->
                        sender.send(
                            BluetoothMessageType.PAIRING_REQUEST,
                            pairingRequest(phoneAddress, method),
                        )
                    },
                    ::sendAuthenticationResult,
                )
                .also(CarHandsFree::start)
    }

    private fun sendAuthenticationResult(status: Int) {
        if (!isAuthenticationResultSent(version)) {
            Log.i(TAG, "Not sending the authentication result to a car on protocol $version")
            return
        }
        sender.send(BluetoothMessageType.AUTHENTICATION_RESULT, authenticationResult(status))
    }
}
