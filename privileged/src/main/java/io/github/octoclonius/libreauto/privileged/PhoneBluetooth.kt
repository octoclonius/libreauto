package io.github.octoclonius.libreauto.privileged

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import android.os.UserHandle
import android.util.Log
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@SuppressLint("MissingPermission", "HardwareIds")
internal class PhoneBluetooth(private val context: Context) {

    companion object {
        private const val TAG = "PhoneBluetooth"
        private const val PROFILE_TIMEOUT_MILLIS = 4_000L
        private val CONNECTION_POLICY_ALLOWED =
            BluetoothProfile::class.java.getField("CONNECTION_POLICY_ALLOWED").getInt(null)
        private val setConnectionPolicy =
            BluetoothHeadset::class
                .java
                .getMethod(
                    "setConnectionPolicy",
                    BluetoothDevice::class.java,
                    Int::class.javaPrimitiveType,
                )
        private val connect =
            BluetoothHeadset::class.java.getMethod("connect", BluetoothDevice::class.java)
        private val removeBond = BluetoothDevice::class.java.getMethod("removeBond")
        private val setPin = BluetoothDevice::class.java.getMethod("setPin", String::class.java)
        private val grantRuntimePermission =
            PackageManager::class
                .java
                .getMethod(
                    "grantRuntimePermission",
                    String::class.java,
                    String::class.java,
                    UserHandle::class.java,
                )
    }

    private val lock = Any()
    private var headset: BluetoothHeadset? = null

    private val adapter: BluetoothAdapter by lazy {
        BluetoothManager::class
            .java
            .getDeclaredConstructor(Context::class.java)
            .apply { isAccessible = true }
            .newInstance(context)
            .adapter
    }

    fun address(): String? = adapter.address

    fun grantPermission(packageName: String, permission: String) {
        grantRuntimePermission.invoke(
            context.packageManager,
            packageName,
            permission,
            Process.myUserHandle(),
        )
    }

    fun pair(address: String): Boolean {
        if (adapter.isDiscovering) adapter.cancelDiscovery()
        return device(address).createBond()
    }

    fun cancelPairing(address: String): Boolean = device(address).cancelBondProcess()

    fun removePairing(address: String): Boolean = removeBond.invoke(device(address)) as Boolean

    fun setPin(address: String, pin: String): Boolean =
        setPin.invoke(device(address), pin) as Boolean

    fun confirmPairing(address: String, isConfirmed: Boolean): Boolean =
        device(address).setPairingConfirmation(isConfirmed)

    fun allowHandsFree(address: String): Boolean {
        val proxy = headset() ?: return false
        return setConnectionPolicy.invoke(proxy, device(address), CONNECTION_POLICY_ALLOWED)
            as Boolean
    }

    fun connectHandsFree(address: String): Boolean {
        val proxy = headset() ?: return false
        return allowHandsFree(address) && connect.invoke(proxy, device(address)) as Boolean
    }

    private fun device(address: String): BluetoothDevice = adapter.getRemoteDevice(address)

    private fun headset(): BluetoothHeadset? {
        synchronized(lock) {
            headset?.let {
                return it
            }
        }
        val connected = CountDownLatch(1)
        val listener =
            object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                    synchronized(lock) { headset = proxy as BluetoothHeadset }
                    connected.countDown()
                }

                override fun onServiceDisconnected(profile: Int) {
                    synchronized(lock) { headset = null }
                }
            }
        if (!adapter.getProfileProxy(context, listener, BluetoothProfile.HEADSET)) {
            Log.w(TAG, "Android refused the hands-free profile proxy")
            return null
        }
        if (!connected.await(PROFILE_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
            Log.w(TAG, "No hands-free profile proxy after $PROFILE_TIMEOUT_MILLIS ms")
        }
        return synchronized(lock) { headset }
    }
}
