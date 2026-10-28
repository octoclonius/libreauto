package io.github.octoclonius.libreauto.privileged

import android.Manifest
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.hardware.usb.UsbManager
import android.os.Binder
import android.os.HandlerThread
import android.os.Parcel
import android.os.ParcelFileDescriptor
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
        val caller = Binder.getCallingUid()
        if (!isCallerAllowed(code, caller, appUid, Process.myUid())) {
            Log.w(TAG, "Refused the call $code from uid $caller")
            throw SecurityException("The uid $caller may not make the call $code")
        }
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
    ): Int {
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
            return created.id
        }
    }

    override fun resizeDisplay(displayId: Int, width: Int, height: Int, densityDpi: Int) {
        val current =
            synchronized(displayLock) { carDisplay }
                ?: throw IllegalArgumentException("There is no car display")
        current.resize(displayId, width, height, densityDpi)
    }

    override fun releaseDisplay(displayId: Int) {
        synchronized(displayLock) {
            if (carDisplay?.id == displayId) releaseCarDisplay()
        }
    }

    override fun sendTouch(
        eventTimeNanos: Long,
        action: Int,
        actionIndex: Int,
        pointerIds: IntArray,
        xs: IntArray,
        ys: IntArray,
    ) {
        displayCall { it.sendTouch(eventTimeNanos, action, actionIndex, pointerIds, xs, ys) }
    }

    override fun sendKey(displayId: Int, keyCode: Int) {
        displayCall { it.sendKey(displayId, keyCode) }
    }

    override fun showActivity(displayId: Int, activity: PendingIntent) {
        displayCall { it.showActivity(displayId, activity) }
    }

    override fun showTask(displayId: Int, taskId: Int) {
        displayCall { it.showTask(displayId, taskId) }
    }

    override fun closeTask(displayId: Int, taskId: Int) {
        displayCall { it.closeTask(displayId, taskId) }
    }

    override fun setPhoneSetting(setting: Int, isOn: Boolean) {
        checkedCall(TAG, "Changing the setting $setting", settingRequestError(setting)) {
            settings.set(setting, isOn)
        }
    }

    override fun restoreTasks(displayId: Int, taskIds: IntArray, frontTaskId: Int) {
        displayCall { it.restoreTasks(displayId, taskIds, frontTaskId) }
    }

    override fun startAudio(
        outputs: Array<AudioOutput>,
        input: AudioInput?,
        listener: IAudioActivityListener,
    ): Array<ParcelFileDescriptor> {
        val error = audioRequestError(outputs.toList(), input)
        if (error != null) throw IllegalArgumentException(error)
        synchronized(audioLock) {
            releaseCarAudio()
            val started =
                CarAudioSession(shellContext, outputs.toList(), input, listener, audioThread.looper)
            val pipes = started.start()
            carAudio = started
            return pipes.toTypedArray()
        }
    }

    override fun stopAudio() {
        synchronized(audioLock) { releaseCarAudio() }
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

    private fun displayCall(call: (CarDisplaySession) -> Unit) {
        val current = synchronized(displayLock) { carDisplay }
        if (current == null) {
            Log.w(TAG, "Refused a call to the car display: there is none")
            return
        }
        call(current)
    }
}
