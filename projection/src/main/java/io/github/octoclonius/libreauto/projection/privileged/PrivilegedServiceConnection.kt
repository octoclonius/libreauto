package io.github.octoclonius.libreauto.projection.privileged

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.IInterface
import android.os.RemoteException
import android.util.Log
import io.github.octoclonius.libreauto.privileged.api.IPrivilegedService
import io.github.octoclonius.libreauto.projection.describe
import io.github.octoclonius.libreauto.projection.privileged.PrivilegedServiceState.Failed
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import rikka.shizuku.Shizuku

private const val TAG = "PrivilegedServiceConnection"

internal fun <B : IInterface, T> B.call(action: String, block: (B) -> T): T? =
    try {
        block(this)
    } catch (e: RemoteException) {
        Log.w(TAG, "The privileged service didn't $action", e)
        null
    } catch (e: RuntimeException) {
        Log.w(TAG, "The privileged service refused to $action", e)
        null
    }

class PrivilegedServiceConnection(context: Context, service: ComponentName) {

    private companion object {
        const val PROCESS_NAME_SUFFIX = "privileged"
        const val PERMISSION_REQUEST_CODE = 1
    }

    private val _state = MutableStateFlow<PrivilegedServiceState>(PrivilegedServiceState.Stopped)
    val state: StateFlow<PrivilegedServiceState> = _state.asStateFlow()

    private val _service = MutableStateFlow<IPrivilegedService?>(null)
    internal val service: StateFlow<IPrivilegedService?> = _service.asStateFlow()

    private val args =
        Shizuku.UserServiceArgs(service)
            .daemon(false)
            .processNameSuffix(PROCESS_NAME_SUFFIX)
            .debuggable(context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0)
            .version(versionCode(context))
    private var status = PrivilegedServiceStatus()
    private var connection: ServiceConnection? = null
    private var boundService: IPrivilegedService? = null

    fun start() {
        Shizuku.addBinderReceivedListenerSticky { refresh() }
        Shizuku.addBinderDeadListener { refresh() }
        Shizuku.addRequestPermissionResultListener { requestCode, _ ->
            if (requestCode == PERMISSION_REQUEST_CODE) refresh()
        }
        refresh()
    }

    internal fun <T> call(action: String, block: (IPrivilegedService) -> T): T? {
        val current = service.value
        if (current == null) {
            Log.w(TAG, "Couldn't $action while the privileged service isn't running")
            return null
        }
        return current.call(action, block)
    }

    fun refresh() {
        update(status.withShizuku(shizukuStatus()))
    }

    fun retry() {
        update(status.retried().withShizuku(shizukuStatus()))
    }

    fun requestPermission() {
        if (status.state != PrivilegedServiceState.PermissionNeeded) return
        try {
            Shizuku.requestPermission(PERMISSION_REQUEST_CODE)
        } catch (e: RuntimeException) {
            Log.w(TAG, "Can't request Shizuku's permission", e)
            refresh()
        }
    }

    private fun update(next: PrivilegedServiceStatus) {
        status = next
        if (next.binding == ServiceBinding.Unbound || next.binding is ServiceBinding.Failed) {
            connection = null
        }
        if (next.binding != ServiceBinding.Bound) boundService = null
        if (next.shouldBind) bind() else _state.value = next.state
        _service.value = if (status.state == PrivilegedServiceState.Running) boundService else null
    }

    private fun bind() {
        val bound =
            object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, binder: IBinder?) =
                    onConnected(this, binder)

                override fun onServiceDisconnected(name: ComponentName?) {
                    if (connection === this) Log.w(TAG, "The privileged service stopped")
                    fail(this, Failed.Reason.ServiceStopped)
                }
            }
        connection = bound
        status = status.bindingStarted()
        _state.value = status.state
        try {
            Shizuku.bindUserService(args, bound)
            Log.i(TAG, "Binding the privileged service")
        } catch (e: RuntimeException) {
            Log.e(TAG, "Can't bind the privileged service", e)
            fail(bound, Failed.Reason.Error(describe(e)))
        }
    }

    private fun onConnected(bound: ServiceConnection, binder: IBinder?) {
        if (connection !== bound) return
        if (binder == null || !binder.pingBinder()) {
            Log.w(TAG, "The privileged service connected without a living binder")
            fail(bound, Failed.Reason.ServiceStopped)
            return
        }
        val service = IPrivilegedService.Stub.asInterface(binder)
        val uid =
            try {
                service.uid
            } catch (e: RemoteException) {
                Log.w(TAG, "The privileged service didn't answer", e)
                fail(bound, Failed.Reason.Error(describe(e)))
                return
            }
        Log.i(TAG, "Connected to the privileged service running as uid $uid")
        boundService = service
        update(status.connected())
    }

    private fun fail(failed: ServiceConnection, reason: Failed.Reason) {
        if (connection !== failed) return
        try {
            Shizuku.unbindUserService(args, failed, true)
        } catch (e: RuntimeException) {
            Log.w(TAG, "Can't remove the privileged service", e)
        }
        update(status.failed(reason))
    }

    private fun shizukuStatus(): ShizukuStatus {
        if (!Shizuku.pingBinder()) return ShizukuStatus()
        return try {
            val version = Shizuku.getVersion()
            if (version < MIN_SHIZUKU_VERSION) {
                ShizukuStatus(isRunning = true, version = version)
            } else {
                ShizukuStatus(
                    isRunning = true,
                    version = version,
                    isPermissionGranted =
                        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED,
                    isPermissionDenied = Shizuku.shouldShowRequestPermissionRationale(),
                )
            }
        } catch (e: RuntimeException) {
            Log.w(TAG, "Can't ask Shizuku", e)
            ShizukuStatus(isRunning = true, error = describe(e))
        }
    }
}

private fun versionCode(context: Context): Int =
    context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode.toInt()
