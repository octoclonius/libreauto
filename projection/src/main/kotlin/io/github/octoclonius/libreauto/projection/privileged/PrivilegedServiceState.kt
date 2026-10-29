package io.github.octoclonius.libreauto.projection.privileged

sealed interface PrivilegedServiceState {
    data object Stopped : PrivilegedServiceState

    data object PermissionNeeded : PrivilegedServiceState

    data object Connecting : PrivilegedServiceState

    data object Running : PrivilegedServiceState

    data class Failed(val reason: Reason) : PrivilegedServiceState {
        sealed interface Reason {
            data object PermissionDenied : Reason

            data class ShizukuOutdated(val version: Int) : Reason

            data object ServiceStopped : Reason

            data class Error(val message: String) : Reason
        }
    }
}
