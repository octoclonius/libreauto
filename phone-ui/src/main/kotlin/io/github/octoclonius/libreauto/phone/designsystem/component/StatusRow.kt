package io.github.octoclonius.libreauto.phone.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun StatusRow(
    title: String,
    status: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
) {
    ListItem(
        headlineContent = { Text(title) },
        modifier =
            if (onClick != null) modifier.clickable(enabled = enabled, onClick = onClick)
            else modifier,
        supportingContent = { Text(status) },
    )
}
