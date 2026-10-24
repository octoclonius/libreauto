package io.github.octoclonius.libreauto.privileged

import android.app.PendingIntent
import android.companion.virtual.VirtualDeviceManager
import android.companion.virtual.VirtualDeviceParams
import android.content.ComponentName
import android.content.Context
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.hardware.display.VirtualDisplayConfig
import android.util.Log
import android.view.Surface
import java.io.Closeable
import java.util.concurrent.Executor
import java.util.function.IntConsumer

internal class VirtualCarDisplay
private constructor(
    private val association: CompanionAssociation,
    private val associationId: Int,
    private val device: AutoCloseable,
    private val display: VirtualDisplay,
    private var touchscreen: VirtualCarTouchscreen?,
) : Closeable {

    companion object {
        private const val TAG = "VirtualCarDisplay"
        private const val NAME = "LibreAuto"

        fun create(
            context: Context,
            association: CompanionAssociation,
            surface: Surface,
            width: Int,
            height: Int,
            densityDpi: Int,
            home: ComponentName,
        ): VirtualCarDisplay {
            val associationId = association.acquire()
            val device =
                try {
                    createDevice(context, associationId, home)
                } catch (e: Exception) {
                    association.release(associationId)
                    throw e
                }
            val display =
                try {
                    createDisplay(device, surface, width, height, densityDpi)
                } catch (e: Exception) {
                    device.close()
                    association.release(associationId)
                    throw e
                }
            val touchscreen =
                try {
                    VirtualCarTouchscreen.create(device, display.display.displayId, width, height)
                } catch (e: Exception) {
                    display.release()
                    device.close()
                    association.release(associationId)
                    throw e
                }
            Log.i(
                TAG,
                "Created the display ${display.display.displayId} with a touchscreen: " +
                    "${width}x$height at $densityDpi dpi, home $home",
            )
            return VirtualCarDisplay(association, associationId, device, display, touchscreen)
        }

        private fun createDevice(
            context: Context,
            associationId: Int,
            home: ComponentName,
        ): AutoCloseable {
            val params =
                VirtualDeviceParams.Builder()
                    .setName(NAME)
                    .setLockState(VirtualDeviceParams.LOCK_STATE_ALWAYS_UNLOCKED)
                    .setHomeComponent(home)
                    .build()
            return VirtualDeviceManager::class
                .java
                .getMethod(
                    "createVirtualDevice",
                    Int::class.javaPrimitiveType,
                    VirtualDeviceParams::class.java,
                )
                .invoke(SystemServices.virtualDeviceManager(context), associationId, params)
                as AutoCloseable
        }

        private fun createDisplay(
            device: AutoCloseable,
            surface: Surface,
            width: Int,
            height: Int,
            densityDpi: Int,
        ): VirtualDisplay {
            val trusted =
                DisplayManager::class.java.getField("VIRTUAL_DISPLAY_FLAG_TRUSTED").getInt(null)
            val builder =
                VirtualDisplayConfig.Builder(NAME, width, height, densityDpi)
                    .setFlags(
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC or
                            DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY or
                            trusted
                    )
                    .setSurface(surface)
            builder.javaClass
                .getMethod("setHomeSupported", Boolean::class.javaPrimitiveType)
                .invoke(builder, true)
            builder.javaClass
                .getMethod("setIgnoreActivitySizeRestrictions", Boolean::class.javaPrimitiveType)
                .invoke(builder, true)
            return device.javaClass
                .getMethod(
                    "createVirtualDisplay",
                    VirtualDisplayConfig::class.java,
                    Executor::class.java,
                    VirtualDisplay.Callback::class.java,
                )
                .invoke(device, builder.build(), null, null) as VirtualDisplay?
                ?: error("The virtual device created no display")
        }
    }

    val id: Int = display.display.displayId

    fun showHome(home: PendingIntent) {
        val success = VirtualDeviceManager::class.java.getField("LAUNCH_SUCCESS").getInt(null)
        device.javaClass
            .getMethod(
                "launchPendingIntent",
                Int::class.javaPrimitiveType,
                PendingIntent::class.java,
                Executor::class.java,
                IntConsumer::class.java,
            )
            .invoke(
                device,
                id,
                home,
                Executor(Runnable::run),
                IntConsumer { onHomeLaunched(it, success) },
            )
    }

    private fun onHomeLaunched(result: Int, success: Int) {
        if (result == success) {
            Log.i(TAG, "Showed the home on the display $id")
        } else {
            Log.w(TAG, "The home didn't launch on the display $id: result $result")
        }
    }

    fun resize(width: Int, height: Int, densityDpi: Int) {
        touchscreen?.close()
        touchscreen = null
        display.resize(width, height, densityDpi)
        touchscreen = VirtualCarTouchscreen.create(device, id, width, height)
        Log.i(TAG, "Resized the display $id to ${width}x$height at $densityDpi dpi")
    }

    fun onTouch(eventTimeNanos: Long, action: Int, actionIndex: Int, pointers: List<TouchPointer>) {
        val current = touchscreen
        if (current == null) {
            Log.w(TAG, "Dropped a touch: the display $id has no touchscreen")
            return
        }
        current.onTouch(eventTimeNanos, action, actionIndex, pointers)
    }

    override fun close() {
        try {
            try {
                try {
                    touchscreen?.close()
                } finally {
                    display.release()
                }
            } finally {
                device.close()
            }
        } finally {
            association.release(associationId)
        }
        Log.i(TAG, "Released the display $id")
    }
}
