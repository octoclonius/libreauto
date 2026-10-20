package io.github.octoclonius.libreauto.privileged

import android.content.ComponentName
import android.content.Context
import android.hardware.usb.UsbManager
import android.os.Binder
import android.os.Parcel
import android.os.Process
import android.util.Log
import android.view.Surface
import java.lang.reflect.InvocationTargetException
import kotlin.system.exitProcess

class PrivilegedService(context: Context) : IPrivilegedService.Stub() {

    companion object {
        private const val TAG = "PrivilegedService"
        private const val USB_FUNCTION_NONE = 0L
    }

    private val appUid = context.applicationInfo.uid
    private val appPackage = context.packageName
    private val shellContext = ShellContext(context)
    private val association = CompanionAssociation(shellContext)
    private val lock = Any()
    private var display: VirtualCarDisplay? = null

    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        val caller = Binder.getCallingUid()
        if (!isCallerAllowed(code, caller, appUid, Process.myUid())) {
            Log.w(TAG, "Refused the call $code from uid $caller")
            throw SecurityException("The uid $caller may not make the call $code")
        }
        return super.onTransact(code, data, reply, flags)
    }

    override fun destroy() {
        synchronized(lock) { release() }
        exitProcess(0)
    }

    override fun getUid(): Int = Process.myUid()

    override fun createDisplay(
        surface: Surface,
        width: Int,
        height: Int,
        densityDpi: Int,
        home: ComponentName,
    ): Int {
        val error = displayRequestError(width, height, densityDpi, home.packageName, appPackage)
        if (error != null) throw IllegalArgumentException(error)
        synchronized(lock) {
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
            return created.id
        }
    }

    override fun releaseDisplay(displayId: Int) {
        synchronized(lock) {
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
        synchronized(lock) {
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

    override fun exitAccessoryMode() {
        try {
            UsbManager::class
                .java
                .getMethod("setCurrentFunctions", Long::class.javaPrimitiveType)
                .invoke(shellContext.getSystemService(UsbManager::class.java), USB_FUNCTION_NONE)
            Log.i(TAG, "Left USB accessory mode")
        } catch (e: Exception) {
            val cause = if (e is InvocationTargetException) e.targetException else e
            Log.e(TAG, "Leaving USB accessory mode failed", cause)
        }
    }

    private fun release() {
        val released = display ?: return
        display = null
        try {
            released.close()
        } catch (e: Exception) {
            Log.e(TAG, "Releasing the car display ${released.id} failed", e)
        }
    }
}
