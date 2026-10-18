package io.github.octoclonius.libreauto.phone

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.octoclonius.libreauto.phone.connection.CarRow
import io.github.octoclonius.libreauto.phone.certificate.CertificateProviderRow
import io.github.octoclonius.libreauto.session.certificate.CertificateProvider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    uiState: MainUiState,
    onRefresh: () -> Unit,
    onOpenCertificatePicker: () -> Unit,
    onChooseCertificateProvider: (CertificateProvider.Choice?) -> Unit,
    onCloseCertificatePicker: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(R.string.app_name))
                }
            )
        }
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.padding(innerPadding),
        ) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                CarRow(state = uiState.car)
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
