package io.github.octoclonius.libreauto.phone.privileged

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.octoclonius.libreauto.phone.R
import io.github.octoclonius.libreauto.phone.ui.StatusRow
import io.github.octoclonius.libreauto.projection.privileged.PrivilegedServiceState
import io.github.octoclonius.libreauto.projection.privileged.PrivilegedServiceState.Failed

@Composable
fun PrivilegedServiceRow(
    state: PrivilegedServiceState,
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    StatusRow(
        title = stringResource(R.string.shizuku_title),
        status =
            when (state) {
                PrivilegedServiceState.Stopped -> stringResource(R.string.shizuku_stopped)
                PrivilegedServiceState.PermissionNeeded ->
                    stringResource(R.string.shizuku_permission_needed)
                PrivilegedServiceState.Connecting -> stringResource(R.string.shizuku_connecting)
                PrivilegedServiceState.Running -> stringResource(R.string.shizuku_running)
                is Failed ->
                    when (val reason = state.reason) {
                        Failed.Reason.PermissionDenied ->
                            stringResource(R.string.shizuku_failed_permission_denied)
                        is Failed.Reason.ShizukuOutdated ->
                            stringResource(R.string.shizuku_failed_outdated, reason.version)
                        Failed.Reason.ServiceStopped ->
                            stringResource(R.string.shizuku_failed_service_stopped)
                        is Failed.Reason.Error ->
                            stringResource(R.string.shizuku_failed_error, reason.message)
                    }
            },
        modifier = modifier,
        onClick =
            if (state == PrivilegedServiceState.PermissionNeeded) onRequestPermission else null,
    )
}
