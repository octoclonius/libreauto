package io.github.octoclonius.libreauto.privileged

import android.content.ComponentName
import android.content.Context
import android.hardware.usb.UsbManager
import android.os.HandlerThread
import android.os.Parcel
import android.os.Process
import android.os.UserHandle
import android.util.Log
import android.view.Surface
import io.github.octoclonius.libreauto.privileged.api.AudioInput
import io.github.octoclonius.libreauto.privileged.api.AudioOutput
import io.github.octoclonius.libreauto.privileged.api.IAudioActivityListener
import io.github.octoclonius.libreauto.privileged.api.ICarAudioSession
import io.github.octoclonius.libreauto.privileged.api.ICarDisplaySession
import io.github.octoclonius.libreauto.privileged.api.IDisplayTaskListener
import io.github.octoclonius.libreauto.privileged.api.IPhoneBluetoothService
import io.github.octoclonius.libreauto.privileged.api.IPrivilegedService
import io.github.octoclonius.libreauto.privileged.audio.CarAudioSession
import io.github.octoclonius.libreauto.privileged.audio.audioRequestError
import io.github.octoclonius.libreauto.privileged.bluetooth.PhoneBluetoothService
import io.github.octoclonius.libreauto.privileged.calls.CallAccess
import io.github.octoclonius.libreauto.privileged.calls.PhoneCalls
import io.github.octoclonius.libreauto.privileged.display.AmbientDisplay
import io.github.octoclonius.libreauto.privileged.display.CarDisplaySession
import io.github.octoclonius.libreauto.privileged.display.CompanionAssociation
import io.github.octoclonius.libreauto.privileged.display.displayRequestError
import io.github.octoclonius.libreauto.privileged.input.DisplayKeys
import io.github.octoclonius.libreauto.privileged.notifications.NotificationAccess
import io.github.octoclonius.libreauto.privileged.notifications.NotificationActions
import io.github.octoclonius.libreauto.privileged.notifications.notificationAccessRequestError
import io.github.octoclonius.libreauto.privileged.notifications.notificationVisibilityRequestError
import io.github.octoclonius.libreauto.privileged.settings.PhoneSettings
import io.github.octoclonius.libreauto.privileged.settings.settingRequestError
import kotlin.system.exitProcess

class PrivilegedService(context: Context) : IPrivilegedService.Stub() {

    companion object {
        private const val TAG = "PrivilegedService"
    }

    private val appUid = context.applicationInfo.uid
    private val appPackage = context.packageName
    private val shellContext = ShellContext(context)
    private val association = CompanionAssociation(shellContext)
    private val ambientDisplay = AmbientDisplay(shellContext)
    private val keys = DisplayKeys(shellContext)
    private val settings = PhoneSettings(shellContext)
    private val bluetoothService = PhoneBluetoothService(shellContext, settings, appUid, appPackage)
    private val callAccess = CallAccess(shellContext)
    private val phoneCalls = PhoneCalls(shellContext)
    private val notificationAccess = NotificationAccess(shellContext)
    private val notificationActions = NotificationActions()
    private val displayLock = Any()
    private val audioLock = Any()
    private var carDisplay: CarDisplaySession? = null
    private var carAudio: CarAudioSession? = null
    private val audioThread = HandlerThread("car-audio").apply { start() }

    init {
        MainlineModules.initialize()
        identityCall(TAG, "Allowing the ambient display an earlier service suppressed") {
            ambientDisplay.allow()
        }
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
                    ambientDisplay,
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

    override fun acceptRingingCall(videoState: Int) {
        identityCall(TAG, "Answering the ringing call") { phoneCalls.acceptRingingCall(videoState) }
    }

    override fun endCall() {
        identityCall(TAG, "Ending the foreground call") { phoneCalls.endCall() }
    }

    override fun allowNotificationAccess(listener: ComponentName) {
        checkedCall(
            TAG,
            "Allowing the app the phone's notifications",
            notificationAccessRequestError(listener.packageName, appPackage),
        ) {
            notificationAccess.allow(listener)
        }
    }

    override fun onNotificationClick(key: String, rank: Int, count: Int) {
        checkedCall(
            TAG,
            "Reporting a notification click",
            notificationVisibilityRequestError(rank, count),
        ) {
            notificationActions.click(key, rank, count)
        }
    }

    override fun onNotificationClear(
        pkg: String,
        user: UserHandle,
        key: String,
        isHeadsUp: Boolean,
        rank: Int,
        count: Int,
    ) {
        checkedCall(
            TAG,
            "Reporting a notification dismissal",
            notificationVisibilityRequestError(rank, count),
        ) {
            notificationActions.clear(pkg, user.userId, key, isHeadsUp, rank, count)
        }
    }

    override fun getBluetoothService(): IPhoneBluetoothService = bluetoothService

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
}
