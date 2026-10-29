package io.github.octoclonius.libreauto.projection.privileged

import io.github.octoclonius.libreauto.projection.privileged.PrivilegedServiceState.Failed

internal const val MIN_SHIZUKU_VERSION = 13

internal data class ShizukuStatus(
    val isRunning: Boolean = false,
    val version: Int = 0,
    val isPermissionGranted: Boolean = false,
    val isPermissionDenied: Boolean = false,
    val error: String? = null,
) {
    val isReady: Boolean
        get() = isRunning && error == null && version >= MIN_SHIZUKU_VERSION && isPermissionGranted
}

internal sealed interface ServiceBinding {
    data object Unbound : ServiceBinding

    data object Binding : ServiceBinding

    data object Bound : ServiceBinding

    data class Failed(val reason: PrivilegedServiceState.Failed.Reason) : ServiceBinding
}

internal data class PrivilegedServiceStatus(
    val shizuku: ShizukuStatus = ShizukuStatus(),
    val binding: ServiceBinding = ServiceBinding.Unbound,
) {
    val state: PrivilegedServiceState
        get() =
            when {
                !shizuku.isRunning -> PrivilegedServiceState.Stopped
                shizuku.error != null -> Failed(Failed.Reason.Error(shizuku.error))
                shizuku.version < MIN_SHIZUKU_VERSION ->
                    Failed(Failed.Reason.ShizukuOutdated(shizuku.version))
                !shizuku.isPermissionGranted ->
                    if (shizuku.isPermissionDenied) Failed(Failed.Reason.PermissionDenied)
                    else PrivilegedServiceState.PermissionNeeded
                else ->
                    when (binding) {
                        ServiceBinding.Unbound,
                        ServiceBinding.Binding -> PrivilegedServiceState.Connecting
                        ServiceBinding.Bound -> PrivilegedServiceState.Running
                        is ServiceBinding.Failed -> Failed(binding.reason)
                    }
            }

    val shouldBind: Boolean
        get() = shizuku.isReady && binding == ServiceBinding.Unbound

    fun withShizuku(shizuku: ShizukuStatus): PrivilegedServiceStatus =
        PrivilegedServiceStatus(shizuku, if (shizuku.isReady) binding else ServiceBinding.Unbound)

    fun bindingStarted(): PrivilegedServiceStatus =
        if (shouldBind) copy(binding = ServiceBinding.Binding) else this

    fun connected(): PrivilegedServiceStatus =
        if (binding == ServiceBinding.Binding) copy(binding = ServiceBinding.Bound) else this

    fun failed(reason: Failed.Reason): PrivilegedServiceStatus =
        if (binding == ServiceBinding.Binding || binding == ServiceBinding.Bound) {
            copy(binding = ServiceBinding.Failed(reason))
        } else {
            this
        }

    fun retried(): PrivilegedServiceStatus =
        if (binding is ServiceBinding.Failed) copy(binding = ServiceBinding.Unbound) else this
}
