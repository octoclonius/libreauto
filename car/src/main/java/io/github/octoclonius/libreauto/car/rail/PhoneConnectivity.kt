package io.github.octoclonius.libreauto.car.rail

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.telephony.CellSignalStrength
import android.telephony.ServiceState
import android.telephony.SignalStrength
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

data class Connectivity(val wifiLevel: Int?, val cellular: Cellular?)

sealed interface Cellular {
    data object AirplaneMode : Cellular

    data object NoService : Cellular

    data class Signal(val level: Int) : Cellular
}

fun phoneConnectivity(context: Context): Flow<Connectivity> = callbackFlow {
    val handler = Handler(Looper.getMainLooper())
    val connectivity = context.getSystemService(ConnectivityManager::class.java)
    val wifi = context.getSystemService(WifiManager::class.java)
    val hasCellular =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS)
    val telephony =
        if (hasCellular) context.getSystemService(TelephonyManager::class.java) else null
    val wifiLevels = HashMap<Network, Int>()
    var isInService = false
    var cellularLevel = 0

    fun update() {
        val cellular =
            when {
                telephony == null -> null
                isAirplaneModeOn(context) -> Cellular.AirplaneMode
                isInService -> Cellular.Signal(cellularLevel)
                else -> Cellular.NoService
            }
        trySend(Connectivity(wifiLevels.values.maxOrNull(), cellular))
    }

    val wifiCallback =
        object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(
                network: Network,
                capabilities: NetworkCapabilities,
            ) {
                val rssi = (capabilities.transportInfo as? WifiInfo)?.rssi
                wifiLevels[network] =
                    if (rssi == null) CellSignalStrength.SIGNAL_STRENGTH_NONE_OR_UNKNOWN
                    else
                        wifi.calculateSignalLevel(rssi) * CellSignalStrength.SIGNAL_STRENGTH_GREAT /
                            wifi.maxSignalLevel
                update()
            }

            override fun onLost(network: Network) {
                wifiLevels.remove(network)
                update()
            }
        }
    val telephonyCallback =
        object :
            TelephonyCallback(),
            TelephonyCallback.ServiceStateListener,
            TelephonyCallback.SignalStrengthsListener {
            override fun onServiceStateChanged(serviceState: ServiceState) {
                isInService = serviceState.state == ServiceState.STATE_IN_SERVICE
                update()
            }

            override fun onSignalStrengthsChanged(signalStrength: SignalStrength) {
                cellularLevel = signalStrength.level
                update()
            }
        }
    val airplaneModeReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = update()
        }

    connectivity.registerNetworkCallback(
        NetworkRequest.Builder()
            .clearCapabilities()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build(),
        wifiCallback,
        handler,
    )
    telephony?.registerTelephonyCallback(context.mainExecutor, telephonyCallback)
    context.registerReceiver(
        airplaneModeReceiver,
        IntentFilter(Intent.ACTION_AIRPLANE_MODE_CHANGED),
        null,
        handler,
        Context.RECEIVER_NOT_EXPORTED,
    )
    update()
    awaitClose {
        connectivity.unregisterNetworkCallback(wifiCallback)
        telephony?.unregisterTelephonyCallback(telephonyCallback)
        context.unregisterReceiver(airplaneModeReceiver)
    }
}

internal fun isAirplaneModeOn(context: Context): Boolean =
    Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0
