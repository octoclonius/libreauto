package io.github.octoclonius.libreauto.phone.licenses

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mikepenz.aboutlibraries.entity.Library
import io.github.octoclonius.libreauto.phone.R
import io.github.octoclonius.libreauto.phone.ui.PageScaffold

private val LICENSE_TEXT_PADDING = 16.dp

@Composable
fun LicenseScreen(uiState: LicensesUiState, uniqueId: String, onBack: () -> Unit) {
    val library =
        (uiState as? LicensesUiState.Success)?.libraries?.firstOrNull { it.uniqueId == uniqueId }
    PageScaffold(
        title = library?.name ?: stringResource(R.string.license_title),
        onBack = onBack,
    ) { innerPadding ->
        when {
            uiState is LicensesUiState.Loading ->
                Box(Modifier.fillMaxSize().padding(innerPadding), Alignment.Center) {
                    CircularProgressIndicator()
                }
            library != null ->
                Column(
                    Modifier.fillMaxSize()
                        .padding(innerPadding)
                        .verticalScroll(rememberScrollState())
                ) {
                    LicenseTexts(library)
                }
        }
    }
}

@Composable
private fun LicenseTexts(library: Library) {
    for (license in library.licenses) {
        Text(
            license.licenseContent ?: license.url ?: stringResource(R.string.license_no_text),
            modifier = Modifier.padding(LICENSE_TEXT_PADDING),
        )
    }
}
