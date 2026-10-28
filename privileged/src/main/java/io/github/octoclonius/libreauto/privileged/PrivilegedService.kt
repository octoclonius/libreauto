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
    private var display: VirtualCarDisplay? = null
    private var tasks: DisplayTasks? = null
    private var audio: CarAudioPolicy? = null
    private var audioActivity: AudioActivityMonitor? = null
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
        synchronized(displayLock) { release() }
        synchronized(audioLock) { releaseAudio() }
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
            release()
            val created =
                reportingCall("Creating the car display") {
                    VirtualCarDisplay.create(
                        shellContext,
                        association,
                        surface,
                        width,
                        height,
                        densityDpi,
                        home,
                    )
                }
            display = created
            this.tasks =
                reportingCall("Watching the tasks on the car display", onFailure = ::release) {
                    DisplayTasks(shellContext, created.id, home.packageName, tasks).apply {
                        start()
                    }
                }
            return created.id
        }
    }

    override fun resizeDisplay(displayId: Int, width: Int, height: Int, densityDpi: Int) {
        synchronized(displayLock) {
            val current = display
            val error = resizeRequestError(displayId, width, height, densityDpi, current?.id)
            if (current == null || error != null) throw IllegalArgumentException(error)
            reportingCall("Resizing the car display $displayId") {
                current.resize(width, height, densityDpi)
            }
        }
    }

    override fun releaseDisplay(displayId: Int) {
        synchronized(displayLock) {
            if (display?.id == displayId) release()
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
        displayCall(
            "Sending a touch to the car display",
            { touchRequestError(eventTimeNanos, action, actionIndex, pointerIds, xs, ys) },
        ) { car ->
            val pointers = pointerIds.indices.map { TouchPointer(pointerIds[it], xs[it], ys[it]) }
            car.onTouch(eventTimeNanos, action, actionIndex, pointers)
        }
    }

    override fun sendKey(displayId: Int, keyCode: Int) {
        displayCall(
            "Sending the key $keyCode to the car display $displayId",
            { car -> keyRequestError(displayId, keyCode, car.id) },
        ) {
            keys.press(displayId, keyCode)
        }
    }

    override fun showActivity(displayId: Int, activity: PendingIntent) {
        displayCall(
            "Starting an activity on the car display $displayId",
            { car ->
                activityRequestError(
                    displayId,
                    car.id,
                    activity.isActivity,
                    activity.creatorUid,
                    appUid,
                )
            },
        ) { car ->
            car.showActivity(shellContext, activity)
        }
    }

    override fun showTask(displayId: Int, taskId: Int) {
        displayCall(
            "Showing the task $taskId on the car display $displayId",
            { car -> taskRequestError(displayId, taskId, car.id, tasks?.taskIds.orEmpty()) },
        ) {
            tasks?.show(taskId)
        }
    }

    override fun closeTask(displayId: Int, taskId: Int) {
        displayCall(
            "Closing the task $taskId on the car display $displayId",
            { car -> taskRequestError(displayId, taskId, car.id, tasks?.taskIds.orEmpty()) },
        ) {
            tasks?.close(taskId)
        }
    }

    override fun setPhoneSetting(setting: Int, isOn: Boolean) {
        checkedCall("Changing the setting $setting", settingRequestError(setting)) {
            settings.set(setting, isOn)
        }
    }

    override fun restoreTasks(displayId: Int, taskIds: IntArray, frontTaskId: Int) {
        displayCall(
            "Bringing back tasks to the car display $displayId",
            { car -> restoreRequestError(displayId, car.id, taskIds.toList(), frontTaskId) },
        ) {
            tasks?.restore(taskIds.toList(), frontTaskId)
        }
    }

    override fun startAudio(
        outputs: Array<AudioOutput>,
        input: AudioInput?,
        listener: IAudioActivityListener,
    ): Array<ParcelFileDescriptor> {
        val error = audioRequestError(outputs.toList(), input)
        if (error != null) throw IllegalArgumentException(error)
        synchronized(audioLock) {
            releaseAudio()
            return reportingCall("Diverting audio to the car", onFailure = ::releaseAudio) {
                val monitor = AudioActivityMonitor(shellContext, listener, audioThread.looper)
                audioActivity = monitor
                val policy =
                    CarAudioPolicy.register(
                        shellContext,
                        outputs.toList(),
                        input,
                        audioThread.looper,
                        monitor::onFocusChanged,
                    )
                audio = policy
                monitor.start(policy::focusStack, input != null)
                policy.pipes.toTypedArray()
            }
        }
    }

    override fun stopAudio() {
        synchronized(audioLock) { releaseAudio() }
    }

    override fun allowCallAccess() {
        identityCall("Allowing the app the phone's calls") { callAccess.allow(appUid, appPackage) }
    }

    override fun grantBluetoothPermission() {
        identityCall("Granting the app Bluetooth access") {
            bluetooth.grantPermission(appPackage, Manifest.permission.BLUETOOTH_CONNECT)
        }
    }

    override fun turnOnBluetooth(): Boolean =
        identityCall("Turning Bluetooth on") { settings.turnOnBluetooth() } == true

    override fun getBluetoothAddress(): String? =
        identityCall("Reading the phone's Bluetooth address") { bluetooth.address() }

    override fun pairBluetooth(address: String): Boolean =
        deviceCall("Pairing with", address) { bluetooth.pair(address) }

    override fun cancelBluetoothPairing(address: String): Boolean =
        deviceCall("Canceling the pairing with", address) { bluetooth.cancelPairing(address) }

    override fun removeBluetoothPairing(address: String): Boolean =
        deviceCall("Removing the pairing with", address) { bluetooth.removePairing(address) }

    override fun setBluetoothPin(address: String, pin: String): Boolean =
        checkedCall("Setting the PIN for $address", pinRequestError(address, pin)) {
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
        identityCall("Leaving USB accessory mode") {
            val none = UsbManager::class.java.getField("FUNCTION_NONE").getLong(null)
            UsbManager::class
                .java
                .getMethod("setCurrentFunctions", Long::class.javaPrimitiveType)
                .invoke(shellContext.getSystemService(UsbManager::class.java), none)
            Log.i(TAG, "Left USB accessory mode")
        }
    }

    private fun release() {
        val watched = tasks
        val released = display
        tasks = null
        display = null
        closeAll(TAG) {
            step("Moving the car display's tasks to the phone") { watched?.moveToPhone() }
            step("Stopping watching the tasks on the car display") { watched?.close() }
            step("Releasing the car display ${released?.id}") { released?.close() }
        }
    }

    private fun releaseAudio() {
        val monitor = audioActivity
        val policy = audio
        audioActivity = null
        audio = null
        closeAll(TAG) {
            step("Unregistering the car audio policy") { policy?.close() }
            step("Stopping the audio activity monitor") { monitor?.close() }
        }
    }

    private fun deviceCall(action: String, address: String, call: () -> Boolean): Boolean =
        checkedCall("$action $address", bluetoothRequestError(address), call) == true

    private fun displayCall(
        action: String,
        refusal: (VirtualCarDisplay) -> String?,
        call: (VirtualCarDisplay) -> Unit,
    ) {
        synchronized(displayLock) {
            val current = display
            if (current == null) {
                Log.w(TAG, "$action refused: there is no car display")
                return
            }
            checkedCall(action, refusal(current)) { call(current) }
        }
    }

    private fun <T> checkedCall(action: String, refusal: String?, call: () -> T): T? {
        if (refusal != null) {
            Log.w(TAG, "$action refused: $refusal")
            return null
        }
        return identityCall(action, call)
    }

    private fun <T> identityCall(action: String, call: () -> T): T? {
        val identity = Binder.clearCallingIdentity()
        return try {
            call()
        } catch (e: Exception) {
            Log.e(TAG, "$action failed", e.unwrapped())
            null
        } finally {
            Binder.restoreCallingIdentity(identity)
        }
    }

    private fun <T> reportingCall(action: String, onFailure: () -> Unit = {}, call: () -> T): T {
        val identity = Binder.clearCallingIdentity()
        try {
            return call()
        } catch (e: Exception) {
            onFailure()
            val cause = e.unwrapped()
            Log.e(TAG, "$action failed", cause)
            throw IllegalStateException(
                "$action failed: ${cause.javaClass.simpleName}: ${cause.message}"
            )
        } finally {
            Binder.restoreCallingIdentity(identity)
        }
    }
}
