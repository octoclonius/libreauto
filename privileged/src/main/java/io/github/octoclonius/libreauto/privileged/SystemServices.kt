package io.github.octoclonius.libreauto.privileged

import android.companion.CompanionDeviceManager
import android.companion.ICompanionDeviceManager
import android.companion.virtual.IVirtualDeviceManager
import android.companion.virtual.VirtualDeviceManager
import android.content.Context
import android.os.IBinder
import android.os.ServiceManager

internal object SystemServices {

    fun companionDeviceManager(context: Context): CompanionDeviceManager =
        construct(
            CompanionDeviceManager::class.java,
            ICompanionDeviceManager::class.java,
            ICompanionDeviceManager.Stub.asInterface(service(Context.COMPANION_DEVICE_SERVICE)),
            context,
        )

    fun virtualDeviceManager(context: Context): VirtualDeviceManager =
        construct(
            VirtualDeviceManager::class.java,
            IVirtualDeviceManager::class.java,
            IVirtualDeviceManager.Stub.asInterface(service(Context.VIRTUAL_DEVICE_SERVICE)),
            context,
        )

    private fun <T> construct(
        type: Class<T>,
        serviceType: Class<*>,
        service: Any,
        context: Context,
    ): T =
        type
            .getDeclaredConstructor(serviceType, Context::class.java)
            .apply { isAccessible = true }
            .newInstance(service, context)

    private fun service(name: String): IBinder =
        ServiceManager.getService(name) ?: error("No $name service")
}
