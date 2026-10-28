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
import java.lang.reflect.InvocationTargetException
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
                try {
                    VirtualCarDisplay.create(
                        shellContext,
                        association,
                        surface,
                        width,
                        height,
                        densityDpi,
                        home,
                    )
                } catch (e: Exception) {
                    val cause = if (e is InvocationTargetException) e.targetException else e
                    Log.e(TAG, "Creating the car display failed", cause)
                    throw IllegalStateException(
                        "Creating the car display failed: ${cause.javaClass.simpleName}: " +
                            cause.message
                    )
                }
            display = created
            try {
                this.tasks =
                    DisplayTasks(shellContext, created.id, home.packageName, tasks).apply {
                        start()
                    }
            } catch (e: Exception) {
                release()
                val cause = if (e is InvocationTargetException) e.targetException else e
                Log.e(TAG, "Watching the tasks on the car display failed", cause)
                throw IllegalStateException(
                    "Watching the tasks on the car display failed: " +
                        "${cause.javaClass.simpleName}: ${cause.message}"
                )
            }
            return created.id
        }
    }

    override fun resizeDisplay(displayId: Int, width: Int, height: Int, densityDpi: Int) {
        synchronized(displayLock) {
            val current = display
            val error = resizeRequestError(displayId, width, height, densityDpi, current?.id)
            if (current == null || error != null) throw IllegalArgumentException(error)
            try {
                current.resize(width, height, densityDpi)
            } catch (e: Exception) {
                val cause = if (e is InvocationTargetException) e.targetException else e
                Log.e(TAG, "Resizing the car display $displayId failed", cause)
                throw IllegalStateException(
                    "Resizing the car display failed: ${cause.javaClass.simpleName}: " +
                        cause.message
                )
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
        val error = touchRequestError(eventTimeNanos, action, actionIndex, pointerIds, xs, ys)
        if (error != null) {
            Log.w(TAG, "Dropped a touch: $error")
            return
        }
        val pointers = pointerIds.indices.map { TouchPointer(pointerIds[it], xs[it], ys[it]) }
        synchronized(displayLock) {
            val current = display
            if (current == null) {
                Log.w(TAG, "Dropped a touch without a car display")
                return
            }
            try {
                current.onTouch(eventTimeNanos, action, actionIndex, pointers)
            } catch (e: Exception) {
                val cause = if (e is InvocationTargetException) e.targetException else e
                Log.e(TAG, "Sending a touch to the car display ${current.id} failed", cause)
            }
        }
    }

    override fun sendKey(displayId: Int, keyCode: Int) {
        synchronized(displayLock) {
            val error = keyRequestError(displayId, keyCode, display?.id)
            if (error != null) {
                Log.w(TAG, "Dropped a key: $error")
                return
            }
            try {
                keys.press(displayId, keyCode)
            } catch (e: Exception) {
                val cause = if (e is InvocationTargetException) e.targetException else e
                Log.e(TAG, "Sending the key $keyCode to the car display $displayId failed", cause)
            }
        }
    }

    override fun showActivity(displayId: Int, activity: PendingIntent) {
        synchronized(displayLock) {
            val current = display
            val error =
                activityRequestError(
                    displayId,
                    current?.id,
                    activity.isActivity,
                    activity.creatorUid,
                    appUid,
                )
            if (current == null || error != null) {
                Log.w(TAG, "Didn't start an activity: $error")
                return
            }
            try {
                current.showActivity(shellContext, activity)
            } catch (e: Exception) {
                val cause = if (e is InvocationTargetException) e.targetException else e
                Log.e(TAG, "Starting an activity on the car display $displayId failed", cause)
            }
        }
    }

    override fun showTask(displayId: Int, taskId: Int) {
        synchronized(displayLock) {
            val current = tasks
            val error = taskRequestError(displayId, taskId, display?.id, current?.taskIds.orEmpty())
            if (current == null || error != null) {
                Log.w(TAG, "Didn't show a task: $error")
                return
            }
            try {
                current.show(taskId)
            } catch (e: Exception) {
                val cause = if (e is InvocationTargetException) e.targetException else e
                Log.e(TAG, "Showing the task $taskId on the car display $displayId failed", cause)
            }
        }
    }

    override fun closeTask(displayId: Int, taskId: Int) {
        synchronized(displayLock) {
            val current = tasks
            val error = taskRequestError(displayId, taskId, display?.id, current?.taskIds.orEmpty())
            if (current == null || error != null) {
                Log.w(TAG, "Didn't close a task: $error")
                return
            }
            try {
                current.close(taskId)
            } catch (e: Exception) {
                val cause = if (e is InvocationTargetException) e.targetException else e
                Log.e(TAG, "Closing the task $taskId on the car display $displayId failed", cause)
            }
        }
    }

    override fun setPhoneSetting(setting: Int, isOn: Boolean) {
        val error = settingRequestError(setting)
        if (error != null) {
            Log.w(TAG, "Didn't change a setting: $error")
            return
        }
        val identity = Binder.clearCallingIdentity()
        try {
            settings.set(setting, isOn)
        } catch (e: Exception) {
            val cause = if (e is InvocationTargetException) e.targetException else e
            Log.e(TAG, "Changing the setting $setting failed", cause)
        } finally {
            Binder.restoreCallingIdentity(identity)
        }
    }

    override fun restoreTasks(displayId: Int, taskIds: IntArray, frontTaskId: Int) {
        synchronized(displayLock) {
            val current = tasks
            val error = restoreRequestError(displayId, display?.id, taskIds.toList(), frontTaskId)
            if (current == null || error != null) {
                Log.w(TAG, "Didn't bring back tasks: $error")
                return
            }
            try {
                current.restore(taskIds.toList(), frontTaskId)
            } catch (e: Exception) {
                val cause = if (e is InvocationTargetException) e.targetException else e
                Log.e(TAG, "Bringing back tasks to the car display $displayId failed", cause)
            }
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
            val identity = Binder.clearCallingIdentity()
            try {
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
                return policy.pipes.toTypedArray()
            } catch (e: Exception) {
                releaseAudio()
                val cause = if (e is InvocationTargetException) e.targetException else e
                Log.e(TAG, "Diverting audio to the car failed", cause)
                throw IllegalStateException(
                    "Diverting audio to the car failed: ${cause.javaClass.simpleName}: " +
                        cause.message
                )
            } finally {
                Binder.restoreCallingIdentity(identity)
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

    override fun setBluetoothPin(address: String, pin: String): Boolean {
        val error = pinRequestError(address, pin)
        if (error != null) {
            Log.w(TAG, "Didn't set a Bluetooth PIN: $error")
            return false
        }
        return identityCall("Setting the PIN for $address") { bluetooth.setPin(address, pin) } ==
            true
    }

    override fun confirmBluetoothPairing(address: String, isConfirmed: Boolean): Boolean =
        deviceCall("Answering the pairing with", address) {
            bluetooth.confirmPairing(address, isConfirmed)
        }

    override fun allowHandsFree(address: String): Boolean =
        deviceCall("Allowing hands-free for", address) { bluetooth.allowHandsFree(address) }

    override fun connectHandsFree(address: String): Boolean =
        deviceCall("Connecting hands-free to", address) { bluetooth.connectHandsFree(address) }

    override fun exitAccessoryMode() {
        try {
            val none = UsbManager::class.java.getField("FUNCTION_NONE").getLong(null)
            UsbManager::class
                .java
                .getMethod("setCurrentFunctions", Long::class.javaPrimitiveType)
                .invoke(shellContext.getSystemService(UsbManager::class.java), none)
            Log.i(TAG, "Left USB accessory mode")
        } catch (e: Exception) {
            val cause = if (e is InvocationTargetException) e.targetException else e
            Log.e(TAG, "Leaving USB accessory mode failed", cause)
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

    private fun deviceCall(action: String, address: String, call: () -> Boolean): Boolean {
        val error = bluetoothRequestError(address)
        if (error != null) {
            Log.w(TAG, "$action a Bluetooth device refused: $error")
            return false
        }
        return identityCall("$action $address", call) == true
    }

    private fun <T> identityCall(action: String, call: () -> T): T? {
        val identity = Binder.clearCallingIdentity()
        return try {
            call()
        } catch (e: Exception) {
            val cause = if (e is InvocationTargetException) e.targetException else e
            Log.e(TAG, "$action failed", cause)
            null
        } finally {
            Binder.restoreCallingIdentity(identity)
        }
    }
}
