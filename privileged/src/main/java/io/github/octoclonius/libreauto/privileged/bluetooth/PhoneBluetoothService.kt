package io.github.octoclonius.libreauto.privileged.bluetooth

import android.Manifest
import android.content.Context
import android.os.Parcel
import android.os.Process
import io.github.octoclonius.libreauto.privileged.api.IPhoneBluetoothService
import io.github.octoclonius.libreauto.privileged.checkCaller
import io.github.octoclonius.libreauto.privileged.checkedCall
import io.github.octoclonius.libreauto.privileged.identityCall
import io.github.octoclonius.libreauto.privileged.isAppCallAllowed
import io.github.octoclonius.libreauto.privileged.settings.PhoneSettings

internal class PhoneBluetoothService(
    context: Context,
    private val settings: PhoneSettings,
    private val appUid: Int,
    private val appPackage: String,
) : IPhoneBluetoothService.Stub() {

    private companion object {
        const val TAG = "PhoneBluetoothService"
    }

    private val bluetooth = PhoneBluetooth(context)

    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        checkCaller(TAG, code) { isAppCallAllowed(code, it, appUid, Process.myUid()) }
        return super.onTransact(code, data, reply, flags)
    }

    override fun grantPermission() {
        identityCall(TAG, "Granting the app Bluetooth access") {
            bluetooth.grantPermission(appPackage, Manifest.permission.BLUETOOTH_CONNECT)
        }
    }

    override fun turnOn(): Boolean =
        identityCall(TAG, "Turning Bluetooth on") { settings.turnOnBluetooth() } == true

    override fun getAddress(): String? =
        identityCall(TAG, "Reading the phone's Bluetooth address") { bluetooth.address() }

    override fun pair(address: String): Boolean =
        deviceCall("Pairing with", address) { bluetooth.pair(address) }

    override fun cancelPairing(address: String): Boolean =
        deviceCall("Canceling the pairing with", address) { bluetooth.cancelPairing(address) }

    override fun removePairing(address: String): Boolean =
        deviceCall("Removing the pairing with", address) { bluetooth.removePairing(address) }

    override fun setPin(address: String, pin: String): Boolean =
        checkedCall(TAG, "Setting the PIN for $address", pinRequestError(address, pin)) {
            bluetooth.setPin(address, pin)
        } == true

    override fun confirmPairing(address: String, isConfirmed: Boolean): Boolean =
        deviceCall("Answering the pairing with", address) {
            bluetooth.confirmPairing(address, isConfirmed)
        }

    override fun allowHandsFree(address: String): Boolean =
        deviceCall("Allowing hands-free for", address) { bluetooth.allowHandsFree(address) }

    override fun connectHandsFree(address: String): Boolean =
        deviceCall("Connecting hands-free to", address) { bluetooth.connectHandsFree(address) }

    private fun deviceCall(action: String, address: String, call: () -> Boolean): Boolean =
        checkedCall(TAG, "$action $address", bluetoothRequestError(address), call) == true
}
