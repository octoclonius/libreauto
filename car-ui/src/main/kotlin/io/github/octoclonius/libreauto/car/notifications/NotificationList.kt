package io.github.octoclonius.libreauto.car.notifications

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.octoclonius.libreauto.car.designsystem.theme.CarSpacing

@Composable
internal fun NotificationList(
    notifications: List<NotificationUiState>,
    onClick: (NotificationUiState) -> Unit,
    onDismiss: (NotificationUiState) -> Unit,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(),
) {
    if (!state.canScrollBackward) state.requestScrollToItem(0)
    LazyColumn(
        modifier,
        state,
        contentPadding,
        verticalArrangement = Arrangement.spacedBy(CarSpacing.small),
    ) {
        items(notifications, key = { it.notification.key }) { item ->
            SwipeToDismissBox(
                rememberSwipeToDismissBoxState(),
                backgroundContent = {},
                modifier = Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null),
                onDismiss = { onDismiss(item) },
            ) {
                NotificationCard(
                    item,
                    MaterialTheme.colorScheme.surfaceContainerHighest,
                    onClick = { onClick(item) },
                )
            }
        }
    }
}
