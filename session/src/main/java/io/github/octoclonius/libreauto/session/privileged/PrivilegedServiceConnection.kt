package io.github.octoclonius.libreauto.session.privileged

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.RemoteException
import android.util.Log
import io.github.octoclonius.libreauto.privileged.IPrivilegedService
import io.github.octoclonius.libreauto.session.describe
import io.github.octoclonius.libreauto.session.privileged.PrivilegedServiceState.Failed
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import rikka.shizuku.Shizuku

object PrivilegedServiceConnection {

    private const val TAG = "PrivilegedServiceConnection"
    private const val PROCESS_NAME_SUFFIX = "privileged"
    private const val PERMISSION_REQUEST_CODE = 1

    private val _state = MutableStateFlow<PrivilegedServiceState>(PrivilegedServiceState.Stopped)
    val state: StateFlow<PrivilegedServiceState> = _state.asStateFlow()

    private val _service = MutableStateFlow<IPrivilegedService?>(null)
    internal val service: StateFlow<IPrivilegedService?> = _service.asStateFlow()

    private lateinit var args: Shizuku.UserServiceArgs
    private var status = PrivilegedServiceStatus()
    private var connection: ServiceConnection? = null
    private var boundService: IPrivilegedService? = null

    internal lateinit var carHome: ComponentName
        private set

    internal lateinit var carHomeIntent: PendingIntent
        private set

    fun start(context: Context, service: ComponentName, carHome: ComponentName) {
        check(!::args.isInitialized) { "Already started" }
        this.carHome = carHome
        carHomeIntent =
            PendingIntent.getActivity(
                context,
                0,
                Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .setComponent(carHome)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE,
            )
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        args =
            Shizuku.UserServiceArgs(service)
                .daemon(false)
                .processNameSuffix(PROCESS_NAME_SUFFIX)
                .debuggable(context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0)
                .version(packageInfo.longVersionCode.toInt())
        Shizuku.addBinderReceivedListenerSticky { refresh() }
        Shizuku.addBinderDeadListener { refresh() }
        Shizuku.addRequestPermissionResultListener { requestCode, _ ->
            if (requestCode == PERMISSION_REQUEST_CODE) refresh()
        }
        refresh()
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
