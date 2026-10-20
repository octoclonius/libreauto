package io.github.octoclonius.libreauto.privileged

import android.companion.virtual.VirtualDeviceManager
import android.content.ComponentName
import android.content.Context
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.hardware.display.VirtualDisplayConfig
import android.util.Log
import android.view.Surface
import java.io.Closeable
import java.util.concurrent.Executor

internal class VirtualCarDisplay
private constructor(
    private val association: CompanionAssociation,
    private val associationId: Int,
    private val device: AutoCloseable,
    private val display: VirtualDisplay,
    private val touchscreen: VirtualCarTouchscreen,
) : Closeable {

    companion object {
        private const val TAG = "VirtualCarDisplay"
        private const val NAME = "LibreAuto"
        private const val LOCK_STATE_ALWAYS_UNLOCKED = 1
        private const val VIRTUAL_DISPLAY_FLAG_TRUSTED = 1 shl 10
        private const val PARAMS = "android.companion.virtual.VirtualDeviceParams"

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
            val manager =
                SystemServices.manager(
                    VirtualDeviceManager::class.java,
                    "android.companion.virtual.IVirtualDeviceManager",
                    Context.VIRTUAL_DEVICE_SERVICE,
                    context,
                )
            val builder = Class.forName("$PARAMS\$Builder").getConstructor().newInstance()
            builder.javaClass.getMethod("setName", String::class.java).invoke(builder, NAME)
            builder.javaClass
                .getMethod("setLockState", Int::class.javaPrimitiveType)
                .invoke(builder, LOCK_STATE_ALWAYS_UNLOCKED)
            builder.javaClass
                .getMethod("setHomeComponent", ComponentName::class.java)
                .invoke(builder, home)
            val params = builder.javaClass.getMethod("build").invoke(builder)
            return VirtualDeviceManager::class
                .java
                .getMethod(
                    "createVirtualDevice",
                    Int::class.javaPrimitiveType,
                    Class.forName(PARAMS),
                )
                .invoke(manager, associationId, params) as AutoCloseable
        }

        private fun createDisplay(
            device: AutoCloseable,
            surface: Surface,
            width: Int,
            height: Int,
            densityDpi: Int,
        ): VirtualDisplay {
            val builder =
                VirtualDisplayConfig.Builder(NAME, width, height, densityDpi)
                    .setFlags(
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY or
                            VIRTUAL_DISPLAY_FLAG_TRUSTED
                    )
                    .setSurface(surface)
            builder.javaClass
                .getMethod("setHomeSupported", Boolean::class.javaPrimitiveType)
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

    fun onTouch(action: Int, actionIndex: Int, pointers: List<TouchPointer>) {
        touchscreen.onTouch(action, actionIndex, pointers)
    }

    override fun close() {
        try {
            try {
                try {
                    touchscreen.close()
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
