package io.github.octoclonius.libreauto.phone.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.octoclonius.libreauto.phone.R
import io.github.octoclonius.libreauto.phone.designsystem.component.NavigationRow
import io.github.octoclonius.libreauto.phone.designsystem.component.PageScaffold

@Composable
fun SettingsScreen(onBack: () -> Unit, onPermissionsClick: () -> Unit, onAboutClick: () -> Unit) {
    PageScaffold(title = stringResource(R.string.settings_title), onBack = onBack) { innerPadding ->
        Column(Modifier.fillMaxSize().padding(innerPadding).verticalScroll(rememberScrollState())) {
            NavigationRow(
                title = stringResource(R.string.permissions_title),
                onClick = onPermissionsClick,
            )
            NavigationRow(title = stringResource(R.string.about_title), onClick = onAboutClick)
        }
    }
}
