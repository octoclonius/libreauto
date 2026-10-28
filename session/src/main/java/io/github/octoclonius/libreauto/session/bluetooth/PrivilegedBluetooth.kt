package io.github.octoclonius.libreauto.session.bluetooth

import android.os.RemoteException
import android.util.Log
import io.github.octoclonius.libreauto.privileged.IPrivilegedService
import io.github.octoclonius.libreauto.session.privileged.PrivilegedServiceConnection

internal object PrivilegedBluetooth {

    private const val TAG = "PrivilegedBluetooth"

    fun grantPermission() {
        call("grant the app Bluetooth access") { it.grantBluetoothPermission() }
    }

    fun turnOn(): Boolean = call("turn Bluetooth on") { it.turnOnBluetooth() } == true

    fun address(): String? = call("read the phone's Bluetooth address") { it.bluetoothAddress }

    fun pair(address: String): Boolean =
        call("pair with the car") { it.pairBluetooth(address) } == true

    fun cancelPairing(address: String): Boolean =
        call("cancel the pairing with the car") { it.cancelBluetoothPairing(address) } == true

    fun removePairing(address: String): Boolean =
        call("remove the pairing with the car") { it.removeBluetoothPairing(address) } == true

    fun setPin(address: String, pin: String): Boolean =
        call("set the car's PIN") { it.setBluetoothPin(address, pin) } == true

    fun confirmPairing(address: String, isConfirmed: Boolean): Boolean =
        call("answer the pairing") { it.confirmBluetoothPairing(address, isConfirmed) } == true

    fun allowHandsFree(address: String): Boolean =
        call("allow hands-free for the car") { it.allowHandsFree(address) } == true

    fun connectHandsFree(address: String): Boolean =
        call("connect hands-free to the car") { it.connectHandsFree(address) } == true

    private fun <T> call(action: String, call: (IPrivilegedService) -> T): T? {
        val service = PrivilegedServiceConnection.service.value
        if (service == null) {
            Log.w(TAG, "Couldn't $action while the privileged service isn't running")
            return null
        }
        return try {
            call(service)
        } catch (e: RemoteException) {
            Log.w(TAG, "The privileged service didn't $action", e)
            null
        }
    }
}
