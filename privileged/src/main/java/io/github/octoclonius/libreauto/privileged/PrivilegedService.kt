package io.github.octoclonius.libreauto.privileged

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.hardware.usb.UsbManager
import android.os.HandlerThread
import android.os.Parcel
import android.os.Process
import android.util.Log
import android.view.Surface
import kotlin.system.exitProcess

class PrivilegedService(context: Context) : IPrivilegedService.Stub() {

    companion object {
        private const val TAG = "PrivilegedService"
    }

    private val appUid = context.applicationInfo.uid
    private val appPackage = context.packageName
    private val shellContext = ShellContext(context)
    private val association = CompanionAssociation(shellContext)
    private val keys = DisplayKeys(shellContext)
    private val settings = PhoneSettings(shellContext)
    private val bluetooth = PhoneBluetooth(shellContext)
    private val callAccess = CallAccess(shellContext)
    private val displayLock = Any()
    private val audioLock = Any()
    private var carDisplay: CarDisplaySession? = null
    private var carAudio: CarAudioSession? = null
    private val audioThread = HandlerThread("car-audio").apply { start() }

    init {
        MainlineModules.initialize()
    }

    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        checkCaller(TAG, code) { isCallerAllowed(code, it, appUid, Process.myUid()) }
        return super.onTransact(code, data, reply, flags)
    }

    override fun destroy() {
        synchronized(displayLock) { releaseCarDisplay() }
        synchronized(audioLock) { releaseCarAudio() }
        exitProcess(0)
    }

    override fun getUid(): Int = Process.myUid()

    override fun createDisplay(
        surface: Surface,
        width: Int,
        height: Int,
        densityDpi: Int,
        home: ComponentName,
        tasks: IDisplayTaskListener,
    ): ICarDisplaySession {
        val error = displayRequestError(width, height, densityDpi, home.packageName, appPackage)
        if (error != null) throw IllegalArgumentException(error)
        synchronized(displayLock) {
            releaseCarDisplay()
            val created =
                CarDisplaySession.create(
                    shellContext,
                    association,
                    keys,
                    appUid,
                    surface,
                    width,
                    height,
                    densityDpi,
                    home,
                    tasks,
                )
            carDisplay = created
            return created
        }
    }

    override fun startAudio(
        outputs: Array<AudioOutput>,
        input: AudioInput?,
        listener: IAudioActivityListener,
    ): ICarAudioSession {
        val error = audioRequestError(outputs.toList(), input)
        if (error != null) throw IllegalArgumentException(error)
        synchronized(audioLock) {
            releaseCarAudio()
            val started =
                CarAudioSession(
                    shellContext,
                    outputs.toList(),
                    input,
                    listener,
                    audioThread.looper,
                    appUid,
                )
            started.start()
            carAudio = started
            return started
        }
    }

    override fun setPhoneSetting(setting: Int, isOn: Boolean) {
        checkedCall(TAG, "Changing the setting $setting", settingRequestError(setting)) {
            settings.set(setting, isOn)
        }
    }

    override fun allowCallAccess() {
        identityCall(TAG, "Allowing the app the phone's calls") {
            callAccess.allow(appUid, appPackage)
        }
    }

    override fun grantBluetoothPermission() {
        identityCall(TAG, "Granting the app Bluetooth access") {
            bluetooth.grantPermission(appPackage, Manifest.permission.BLUETOOTH_CONNECT)
        }
    }

    override fun turnOnBluetooth(): Boolean =
        identityCall(TAG, "Turning Bluetooth on") { settings.turnOnBluetooth() } == true

    override fun getBluetoothAddress(): String? =
        identityCall(TAG, "Reading the phone's Bluetooth address") { bluetooth.address() }

    override fun pairBluetooth(address: String): Boolean =
        deviceCall("Pairing with", address) { bluetooth.pair(address) }

    override fun cancelBluetoothPairing(address: String): Boolean =
        deviceCall("Canceling the pairing with", address) { bluetooth.cancelPairing(address) }

    override fun removeBluetoothPairing(address: String): Boolean =
        deviceCall("Removing the pairing with", address) { bluetooth.removePairing(address) }

    override fun setBluetoothPin(address: String, pin: String): Boolean =
        checkedCall(TAG, "Setting the PIN for $address", pinRequestError(address, pin)) {
            bluetooth.setPin(address, pin)
        } == true

    override fun confirmBluetoothPairing(address: String, isConfirmed: Boolean): Boolean =
        deviceCall("Answering the pairing with", address) {
            bluetooth.confirmPairing(address, isConfirmed)
        }

    override fun allowHandsFree(address: String): Boolean =
        deviceCall("Allowing hands-free for", address) { bluetooth.allowHandsFree(address) }

    override fun connectHandsFree(address: String): Boolean =
        deviceCall("Connecting hands-free to", address) { bluetooth.connectHandsFree(address) }

    override fun exitAccessoryMode() {
        identityCall(TAG, "Leaving USB accessory mode") {
            val none = UsbManager::class.java.getField("FUNCTION_NONE").getLong(null)
            UsbManager::class
                .java
                .getMethod("setCurrentFunctions", Long::class.javaPrimitiveType)
                .invoke(shellContext.getSystemService(UsbManager::class.java), none)
            Log.i(TAG, "Left USB accessory mode")
        }
    }

    private fun releaseCarDisplay() {
        carDisplay?.close()
        carDisplay = null
    }

    private fun releaseCarAudio() {
        carAudio?.close()
        carAudio = null
    }

    private fun deviceCall(action: String, address: String, call: () -> Boolean): Boolean =
        checkedCall(TAG, "$action $address", bluetoothRequestError(address), call) == true
}
