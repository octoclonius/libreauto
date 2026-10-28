package io.github.octoclonius.libreauto.privileged

import android.content.AttributionSource
import android.content.Context
import android.content.ContextWrapper
import android.os.Process

internal class ShellContext(base: Context) : ContextWrapper(base) {

    private val shellPackage: String =
        base.packageManager.getPackagesForUid(Process.myUid())?.firstOrNull()
            ?: error("No package has the uid ${Process.myUid()}")

    private val attribution: AttributionSource =
        AttributionSource.Builder(Process.myUid()).setPackageName(shellPackage).build()

    override fun getPackageName(): String = shellPackage

    override fun getOpPackageName(): String = shellPackage

    override fun getAttributionSource(): AttributionSource = attribution

    override fun getApplicationContext(): Context = this

    override fun createDeviceContext(deviceId: Int): Context =
        ShellContext(super.createDeviceContext(deviceId))
}
