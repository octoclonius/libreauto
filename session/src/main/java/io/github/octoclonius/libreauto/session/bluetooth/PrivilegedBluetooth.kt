package io.github.octoclonius.libreauto.session.bluetooth

import io.github.octoclonius.libreauto.privileged.IPrivilegedService
import io.github.octoclonius.libreauto.session.privileged.PrivilegedServiceConnection

internal class PrivilegedBluetooth(private val connection: PrivilegedServiceConnection) {

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

    private fun <T> call(action: String, block: (IPrivilegedService) -> T): T? =
        connection.call(action, block)
}
