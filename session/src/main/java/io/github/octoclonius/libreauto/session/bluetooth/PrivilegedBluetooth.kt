package io.github.octoclonius.libreauto.session.bluetooth

import io.github.octoclonius.libreauto.privileged.api.IPhoneBluetoothService
import io.github.octoclonius.libreauto.session.privileged.PrivilegedServiceConnection
import io.github.octoclonius.libreauto.session.privileged.call

internal class PrivilegedBluetooth(private val connection: PrivilegedServiceConnection) {

    fun grantPermission() {
        call("grant the app Bluetooth access") { it.grantPermission() }
    }

    fun turnOn(): Boolean = call("turn Bluetooth on") { it.turnOn() } == true

    fun address(): String? = call("read the phone's Bluetooth address") { it.address }

    fun pair(address: String): Boolean = call("pair with the car") { it.pair(address) } == true

    fun cancelPairing(address: String): Boolean =
        call("cancel the pairing with the car") { it.cancelPairing(address) } == true

    fun removePairing(address: String): Boolean =
        call("remove the pairing with the car") { it.removePairing(address) } == true

    fun setPin(address: String, pin: String): Boolean =
        call("set the car's PIN") { it.setPin(address, pin) } == true

    fun confirmPairing(address: String, isConfirmed: Boolean): Boolean =
        call("answer the pairing") { it.confirmPairing(address, isConfirmed) } == true

    fun allowHandsFree(address: String): Boolean =
        call("allow hands-free for the car") { it.allowHandsFree(address) } == true

    fun connectHandsFree(address: String): Boolean =
        call("connect hands-free to the car") { it.connectHandsFree(address) } == true

    private fun <T> call(action: String, block: (IPhoneBluetoothService) -> T): T? =
        connection.call("reach the phone's Bluetooth") { it.bluetoothService }?.call(action, block)
}
