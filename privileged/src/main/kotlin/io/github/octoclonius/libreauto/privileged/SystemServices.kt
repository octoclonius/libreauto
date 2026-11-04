package io.github.octoclonius.libreauto.privileged

import android.app.IActivityTaskManager
import android.companion.CompanionDeviceManager
import android.companion.ICompanionDeviceManager
import android.companion.virtual.IVirtualDeviceManager
import android.companion.virtual.VirtualDeviceManager
import android.content.Context
import android.os.IBinder
import android.os.ServiceManager
import com.android.internal.statusbar.IStatusBarService

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

    fun activityTaskManager(): IActivityTaskManager =
        IActivityTaskManager.Stub.asInterface(
            service(Context::class.java.getField("ACTIVITY_TASK_SERVICE").get(null) as String)
        )

    fun statusBarService(): IStatusBarService =
        IStatusBarService.Stub.asInterface(
            service(Context::class.java.getField("STATUS_BAR_SERVICE").get(null) as String)
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
