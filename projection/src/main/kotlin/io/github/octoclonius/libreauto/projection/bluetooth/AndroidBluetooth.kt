package io.github.octoclonius.libreauto.projection.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import io.github.octoclonius.libreauto.projection.closeAll
import java.io.Closeable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal class AndroidBluetooth(
    private val context: Context,
    private val carAddress: String,
    private val scope: CoroutineScope,
    private val listener: Listener,
) : Closeable {

    interface Listener {
        fun onEnabled()

        fun onDisabled()

        fun onProfileReady()

        fun onBondState(state: Int)

        fun onHandsFreeState(state: Int)

        fun onPairingRequest(variant: Int, key: Int)

        fun onKeyMissing()
    }

    companion object {
        private const val TAG = "AndroidBluetooth"
        private const val PAIRING_PRIORITY = 999
    }

    private val adapter: BluetoothAdapter? =
        context.getSystemService(BluetoothManager::class.java)?.adapter
    private var headset: BluetoothHeadset? = null
    private var isProfileRequested = false
    private var isRegistered = false

    val isEnabled: Boolean
        get() = adapter?.isEnabled == true

    private val stateReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    BluetoothAdapter.ACTION_STATE_CHANGED -> {
                        val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1)
                        scope.launch { onAdapterState(state) }
                    }
                    BluetoothDevice.ACTION_BOND_STATE_CHANGED ->
                        if (isCar(intent)) {
                            val state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1)
                            scope.launch { listener.onBondState(state) }
                        }
                    BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED ->
                        if (isCar(intent)) {
                            val state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1)
                            scope.launch { listener.onHandsFreeState(state) }
                        }
                }
            }
        }

    private val pairingReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (!isCar(intent)) return
                when (intent.action) {
                    BluetoothDevice.ACTION_PAIRING_REQUEST -> {
                        val variant =
                            intent.getIntExtra(BluetoothDevice.EXTRA_PAIRING_VARIANT, Int.MIN_VALUE)
                        if (variant == Int.MIN_VALUE) {
                            Log.w(TAG, "A pairing request from the car without a variant")
                            return
                        }
                        val key =
                            intent.getIntExtra(BluetoothDevice.EXTRA_PAIRING_KEY, Int.MIN_VALUE)
                        scope.launch { listener.onPairingRequest(variant, key) }
                    }
                    BluetoothDevice.ACTION_KEY_MISSING -> scope.launch { listener.onKeyMissing() }
                    else -> return
                }
                if (isOrderedBroadcast) abortBroadcast()
            }
        }

    fun start() {
        context.registerReceiver(
            stateReceiver,
            IntentFilter().apply {
                addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
                addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
                addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
            },
            Context.RECEIVER_EXPORTED,
        )
        context.registerReceiver(
            pairingReceiver,
            IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_PAIRING_REQUEST)
                addAction(BluetoothDevice.ACTION_KEY_MISSING)
                priority = PAIRING_PRIORITY
            },
            Context.RECEIVER_EXPORTED,
        )
        isRegistered = true
    }

    fun state(): PhoneBluetoothState {
        val device = adapter?.getRemoteDevice(carAddress)
        val proxy = headset
        return try {
            PhoneBluetoothState(
                isProfileReady = proxy != null,
                bondState = device?.bondState ?: BluetoothDevice.BOND_NONE,
                handsFreeState =
                    if (proxy != null && device != null) proxy.getConnectionState(device)
                    else BluetoothProfile.STATE_DISCONNECTED,
            )
        } catch (e: SecurityException) {
            Log.w(TAG, "LibreAuto may not read the car's Bluetooth state", e)
            PhoneBluetoothState(
                isProfileReady = false,
                bondState = BluetoothDevice.BOND_NONE,
                handsFreeState = BluetoothProfile.STATE_DISCONNECTED,
            )
        }
    }

    fun requestProfile() {
        if (headset != null || isProfileRequested) return
        val current = adapter ?: return
        val requested =
            current.getProfileProxy(
                context,
                object : BluetoothProfile.ServiceListener {
                    override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                        scope.launch {
                            isProfileRequested = false
                            if (!isRegistered) {
                                current.closeProfileProxy(BluetoothProfile.HEADSET, proxy)
                                return@launch
                            }
                            headset = proxy as BluetoothHeadset
                            listener.onProfileReady()
                        }
                    }

                    override fun onServiceDisconnected(profile: Int) {
                        scope.launch { headset = null }
                    }
                },
                BluetoothProfile.HEADSET,
            )
        isProfileRequested = requested
        if (!requested) Log.w(TAG, "Android refused the hands-free profile proxy")
    }

    fun closeProfile() {
        headset?.let { adapter?.closeProfileProxy(BluetoothProfile.HEADSET, it) }
        headset = null
    }

    override fun close() {
        closeAll(TAG) {
            if (isRegistered) {
                step("Unregistering the Bluetooth state receiver") {
                    context.unregisterReceiver(stateReceiver)
                }
                step("Unregistering the pairing receiver") {
                    context.unregisterReceiver(pairingReceiver)
                }
                isRegistered = false
            }
            step("Closing the hands-free profile") { closeProfile() }
        }
    }

    private fun onAdapterState(state: Int) {
        when (state) {
            BluetoothAdapter.STATE_ON -> listener.onEnabled()
            BluetoothAdapter.STATE_OFF -> {
                closeProfile()
                listener.onDisabled()
            }
        }
    }

    private fun isCar(intent: Intent): Boolean =
        intent
            .getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
            ?.address
            .equals(carAddress, ignoreCase = true)
}
