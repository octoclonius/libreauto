package io.github.octoclonius.libreauto.car.notifications

import android.graphics.BlendMode
import android.graphics.BlendModeColorFilter
import android.graphics.drawable.Drawable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.octoclonius.libreauto.car.apps.AppIcon
import io.github.octoclonius.libreauto.car.designsystem.theme.CarShapes
import io.github.octoclonius.libreauto.car.designsystem.theme.CarSpacing

private val CARD_MIN_HEIGHT = 76.dp
internal val NOTIFICATION_ICON_SIZE = 48.dp
private val ICON_CIRCLE_SIZE = 40.dp
private val ICON_CIRCLE_PADDING = 8.dp
private val SMALL_ICON_PADDING = NOTIFICATION_ICON_SIZE * (ICON_CIRCLE_PADDING / ICON_CIRCLE_SIZE)
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
            NotificationImage(item)
            Column(Modifier.weight(1f)) {
                Text(
                    item.notification.title.ifBlank { item.appName },
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
private fun NotificationImage(item: NotificationUiState) {
    when (val icon = item.icon) {
        is NotificationIcon.App -> AppIcon(icon.drawable, NOTIFICATION_ICON_SIZE)
        is NotificationIcon.Small -> IconCircle { SmallIcon(icon.drawable) }
        null ->
            IconCircle {
                Text(
                    item.appName.take(1).uppercase(),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
    }
}

@Composable
private fun IconCircle(content: @Composable () -> Unit) {
    Box(
        Modifier.size(NOTIFICATION_ICON_SIZE)
            .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun SmallIcon(icon: Drawable) {
    val tint = MaterialTheme.colorScheme.onSecondaryContainer.toArgb()
    Spacer(
        Modifier.fillMaxSize().padding(SMALL_ICON_PADDING).drawBehind {
            drawIntoCanvas { canvas ->
                icon.setBounds(0, 0, size.width.toInt(), size.height.toInt())
                icon.colorFilter = BlendModeColorFilter(tint, BlendMode.SRC_IN)
                icon.draw(canvas.nativeCanvas)
            }
        }
    )
}
