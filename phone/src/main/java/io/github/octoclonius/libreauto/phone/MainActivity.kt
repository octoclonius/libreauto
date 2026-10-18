package io.github.octoclonius.libreauto.phone

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.octoclonius.libreauto.phone.ui.theme.PhoneTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels { MainViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            PhoneTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                MainScreen(
                    uiState = uiState,
                    onRefresh = viewModel::refresh,
                    onOpenCertificatePicker = viewModel::openCertificatePicker,
                    onChooseCertificateProvider = viewModel::chooseCertificateProvider,
                    onCloseCertificatePicker = viewModel::closeCertificatePicker,
                    onRequestShizukuPermission = viewModel::requestShizukuPermission,
                )
            }
        }
    }
}