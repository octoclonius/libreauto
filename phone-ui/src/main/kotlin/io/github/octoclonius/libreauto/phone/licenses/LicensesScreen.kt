package io.github.octoclonius.libreauto.phone.licenses

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.mikepenz.aboutlibraries.entity.Library
import io.github.octoclonius.libreauto.phone.R
import io.github.octoclonius.libreauto.phone.designsystem.component.NavigationRow
import io.github.octoclonius.libreauto.phone.designsystem.component.PageScaffold

@Composable
fun LicensesScreen(
    uiState: LicensesUiState,
    onBack: () -> Unit,
    onLibraryClick: (Library) -> Unit,
) {
    PageScaffold(title = stringResource(R.string.licenses_title), onBack = onBack) { innerPadding ->
        when (uiState) {
            LicensesUiState.Loading ->
                Box(Modifier.fillMaxSize().padding(innerPadding), Alignment.Center) {
                    CircularProgressIndicator()
                }
            is LicensesUiState.Success ->
                LazyColumn(Modifier.fillMaxSize().padding(innerPadding)) {
                    items(uiState.libraries, key = { it.uniqueId }) { library ->
                        NavigationRow(title = library.name, onClick = { onLibraryClick(library) })
                    }
                }
        }
    }
}
