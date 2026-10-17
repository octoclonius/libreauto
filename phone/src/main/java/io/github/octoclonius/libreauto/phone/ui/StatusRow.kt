package io.github.octoclonius.libreauto.phone.ui

import androidx.compose.foundation.clickable
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun StatusRow(
    title: String,
    status: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    ListItem(
        headlineContent = { Text(title) },
        modifier = modifier.clickable(enabled = enabled, onClick = onClick),
        supportingContent = { Text(status) },
    )
}
