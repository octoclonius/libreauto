package io.github.octoclonius.libreauto.phone.permissions

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.octoclonius.libreauto.phone.R
import io.github.octoclonius.libreauto.phone.designsystem.component.PageScaffold
import io.github.octoclonius.libreauto.projection.permissions.PermissionsState

@Composable
fun PermissionsScreen(
    uiState: PermissionsState,
    notificationListener: ComponentName,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
) {
    val context = LocalContext.current
    LifecycleResumeEffect(Unit) {
        onRefresh()
        onPauseOrDispose {}
    }
    PageScaffold(title = stringResource(R.string.permissions_title), onBack = onBack) { innerPadding
        ->
        Column(Modifier.fillMaxSize().padding(innerPadding).verticalScroll(rememberScrollState())) {
            PermissionRow(
                title = stringResource(R.string.notification_access_title),
                summary = stringResource(R.string.notification_access_summary),
                isAllowed = uiState.isNotificationAccessAllowed,
                onClick = { context.startActivity(notificationAccessPage(notificationListener)) },
            )
            RuntimePermissionRow(
                permission = Manifest.permission.BLUETOOTH_CONNECT,
                title = stringResource(R.string.nearby_devices_title),
                summary = stringResource(R.string.nearby_devices_summary),
                isAllowed = uiState.isNearbyDevicesAllowed,
                onResult = onRefresh,
            )
            RuntimePermissionRow(
                permission = Manifest.permission.READ_CONTACTS,
                title = stringResource(R.string.contacts_title),
                summary = stringResource(R.string.contacts_summary),
                isAllowed = uiState.isContactsAllowed,
                onResult = onRefresh,
            )
            RuntimePermissionRow(
                permission = Manifest.permission.READ_PHONE_STATE,
                title = stringResource(R.string.phone_title),
                summary = stringResource(R.string.phone_summary),
                isAllowed = uiState.isPhoneAllowed,
                onResult = onRefresh,
            )
        }
    }
}

@Composable
private fun RuntimePermissionRow(
    permission: String,
    title: String,
    summary: String,
    isAllowed: Boolean,
    onResult: () -> Unit,
) {
    val context = LocalContext.current
    val activity = checkNotNull(LocalActivity.current)
    var wasRationaleShown by rememberSaveable { mutableStateOf(false) }
    val request =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted
            ->
            val isRationaleShown = activity.shouldShowRequestPermissionRationale(permission)
            if (isDeniedWithoutDialog(isGranted, wasRationaleShown, isRationaleShown)) {
                context.startActivity(appInfo(context))
            }
            onResult()
        }
    PermissionRow(title, summary, isAllowed) {
        if (isAllowed) {
            context.startActivity(appInfo(context))
        } else {
            wasRationaleShown = activity.shouldShowRequestPermissionRationale(permission)
            request.launch(permission)
        }
    }
}

@Composable
private fun PermissionRow(title: String, summary: String, isAllowed: Boolean, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        modifier = Modifier.clickable(onClick = onClick),
        supportingContent = { Text(summary) },
        trailingContent = {
            Text(
                stringResource(
                    if (isAllowed) R.string.permission_allowed else R.string.permission_not_allowed
                )
            )
        },
    )
}

private fun notificationAccessPage(listener: ComponentName): Intent =
    Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
        .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, listener.flattenToString())

private fun appInfo(context: Context): Intent =
    Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null),
    )
