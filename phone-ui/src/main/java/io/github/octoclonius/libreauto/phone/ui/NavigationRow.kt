package io.github.octoclonius.libreauto.phone.ui

import androidx.compose.foundation.clickable
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import io.github.octoclonius.libreauto.phone.R

@Composable
fun NavigationRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
) {
    ListItem(
        headlineContent = { Text(title) },
        modifier = modifier.clickable(onClick = onClick),
        supportingContent = supportingText?.let { { Text(it) } },
        trailingContent = {
            Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null)
        },
    )
}
