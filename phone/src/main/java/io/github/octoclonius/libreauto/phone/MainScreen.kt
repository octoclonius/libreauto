package io.github.octoclonius.libreauto.phone

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import io.github.octoclonius.libreauto.phone.connection.CarRow
import io.github.octoclonius.libreauto.phone.certificate.CertificateProviderRow
import io.github.octoclonius.libreauto.phone.privileged.PrivilegedServiceRow
import io.github.octoclonius.libreauto.phone.ui.PageScaffold
import io.github.octoclonius.libreauto.session.certificate.CertificateProvider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    uiState: MainUiState,
    onRefresh: () -> Unit,
    onOpenCertificatePicker: () -> Unit,
    onChooseCertificateProvider: (CertificateProvider.Choice?) -> Unit,
    onCloseCertificatePicker: () -> Unit,
    onRequestShizukuPermission: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    PageScaffold(
        title = stringResource(R.string.app_name),
        onBack = null,
        actions = {
            IconButton(onClick = onSettingsClick) {
                Icon(
                    painterResource(R.drawable.ic_settings),
                    contentDescription = stringResource(R.string.settings_title),
                )
            }
        },
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.padding(innerPadding),
        ) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                CarRow(state = uiState.car)
                PrivilegedServiceRow(
                    state = uiState.privilegedService,
                    onRequestPermission = onRequestShizukuPermission,
                )
                CertificateProviderRow(
                    state = uiState.certificateProvider,
                    onClick = onOpenCertificatePicker,
                    onChoose = onChooseCertificateProvider,
                    onDismissPicker = onCloseCertificatePicker,
                )
            }
        }
    }
}
