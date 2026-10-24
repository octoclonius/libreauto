package io.github.octoclonius.libreauto.phone.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.octoclonius.libreauto.phone.R
import io.github.octoclonius.libreauto.phone.ui.NavigationRow
import io.github.octoclonius.libreauto.phone.ui.PageScaffold
import io.github.octoclonius.libreauto.phone.ui.StatusRow

@Composable
fun AboutScreen(onBack: () -> Unit, onLicensesClick: () -> Unit) {
    val context = LocalContext.current
    val versionName =
        remember(context) {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }
    PageScaffold(title = stringResource(R.string.about_title), onBack = onBack) { innerPadding ->
        Column(
            Modifier.fillMaxSize().padding(innerPadding).verticalScroll(rememberScrollState())
        ) {
            versionName?.let {
                StatusRow(title = stringResource(R.string.version_title), status = it)
            }
            NavigationRow(
                title = stringResource(R.string.licenses_title),
                onClick = onLicensesClick,
            )
        }
    }
}
