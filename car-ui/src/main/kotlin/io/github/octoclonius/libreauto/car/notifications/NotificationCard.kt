package io.github.octoclonius.libreauto.car.notifications

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.octoclonius.libreauto.car.apps.AppIcon
import io.github.octoclonius.libreauto.car.designsystem.theme.CarShapes
import io.github.octoclonius.libreauto.car.designsystem.theme.CarSpacing

private val CARD_MIN_HEIGHT = 76.dp
internal val NOTIFICATION_ICON_SIZE = 48.dp
private val TITLE_SIZE = 20.sp
private val TEXT_SIZE = 18.sp
private val TEXT_LINE_HEIGHT = 24.sp

@Composable
internal fun NotificationCard(
    item: NotificationUiState,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    border: BorderStroke? = null,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = CarShapes.largeIncreased,
        color = color,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = border,
    ) {
        Row(
            Modifier.heightIn(min = CARD_MIN_HEIGHT)
                .padding(horizontal = CarSpacing.large, vertical = CarSpacing.medium),
            horizontalArrangement = Arrangement.spacedBy(CarSpacing.medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppImage(item.app)
            Column(Modifier.weight(1f)) {
                Text(
                    item.notification.title.ifBlank { item.app.label },
                    style =
                        MaterialTheme.typography.titleLarge.copy(
                            fontSize = TITLE_SIZE,
                            fontWeight = FontWeight.Medium,
                        ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.notification.text.isNotBlank()) {
                    Text(
                        item.notification.text,
                        style =
                            MaterialTheme.typography.bodyLarge.copy(
                                fontSize = TEXT_SIZE,
                                lineHeight = TEXT_LINE_HEIGHT,
                            ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun AppImage(app: NotificationApp) {
    val icon = app.icon
    if (icon != null) {
        AppIcon(icon, NOTIFICATION_ICON_SIZE)
        return
    }
    Box(
        Modifier.size(NOTIFICATION_ICON_SIZE)
            .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            app.label.take(1).uppercase(),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}
