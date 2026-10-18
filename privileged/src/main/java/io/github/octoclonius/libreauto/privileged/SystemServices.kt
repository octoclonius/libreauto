package io.github.octoclonius.libreauto.privileged

import android.content.Context
import android.os.IBinder

internal object SystemServices {

    fun <T> manager(
        type: Class<T>,
        interfaceName: String,
        serviceName: String,
        context: Context,
    ): T =
        type
            .getDeclaredConstructor(Class.forName(interfaceName), Context::class.java)
            .apply { isAccessible = true }
            .newInstance(service(interfaceName, serviceName), context)

    private fun service(interfaceName: String, serviceName: String): Any {
        val binder =
            Class.forName("android.os.ServiceManager")
                .getMethod("getService", String::class.java)
                .invoke(null, serviceName) as IBinder? ?: error("No $serviceName service")
        return Class.forName("$interfaceName\$Stub")
            .getMethod("asInterface", IBinder::class.java)
            .invoke(null, binder)!!
    }
}
