package io.github.octoclonius.libreauto.car.calls

import android.text.BidiFormatter
import android.text.TextDirectionHeuristics
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.octoclonius.libreauto.car.R
import io.github.octoclonius.libreauto.car.apps.AppIcon
import io.github.octoclonius.libreauto.car.designsystem.theme.CALL_NOTIFICATION_ANSWER_COLOR
import io.github.octoclonius.libreauto.car.designsystem.theme.CALL_NOTIFICATION_DECLINE_COLOR
import io.github.octoclonius.libreauto.car.designsystem.theme.CarShapes
import io.github.octoclonius.libreauto.car.designsystem.theme.CarSpacing
import io.github.octoclonius.libreauto.car.notifications.IconCircle
import io.github.octoclonius.libreauto.car.notifications.NOTIFICATION_ICON_SIZE
import io.github.octoclonius.libreauto.car.notifications.cardTextStyle
import io.github.octoclonius.libreauto.car.notifications.cardTitleStyle
import io.github.octoclonius.libreauto.car.overlay.PopUp
import io.github.octoclonius.libreauto.car.overlay.PopUpDefaults
import io.github.octoclonius.libreauto.projection.calls.Caller

internal val CALLER_AVATAR_SIZE = NOTIFICATION_ICON_SIZE
private val APP_BADGE_SIZE = 20.dp
private val CALL_ICON_SIZE = 24.dp
private val MEDIUM_ICON_BUTTON_CONTAINER_HEIGHT = 56.dp
private val MEDIUM_ICON_BUTTON_ICON_SIZE = 24.dp
private val CALL_BUTTON_GAP = CarSpacing.extraLarge

@Composable
internal fun IncomingCallPopUp(
    incoming: IncomingCallUiState?,
    onAnswer: (Int) -> Unit,
    onDecline: (Int) -> Unit,
    onHide: () -> Unit,
    onOnScreen: (Boolean) -> Unit,
    onBounds: (Rect?) -> Unit,
) {
    PopUp(
        incoming,
        key = { it.call.id },
        onHide = onHide,
        onOnScreen = onOnScreen,
        onBounds = onBounds,
    ) { shown ->
        IncomingCallCard(shown, { onAnswer(shown.call.id) }, { onDecline(shown.call.id) })
    }
}

@Composable
private fun IncomingCallCard(
    incoming: IncomingCallUiState,
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = CarShapes.largeIncreased,
        color = PopUpDefaults.color,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = PopUpDefaults.border,
    ) {
        Row(
            Modifier.padding(horizontal = CarSpacing.large, vertical = CarSpacing.medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CallerAvatar(incoming)
            Spacer(Modifier.width(CarSpacing.medium))
            Column(Modifier.weight(1f)) {
                Text(
                    callerText(incoming.call.caller),
                    style = cardTitleStyle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                incoming.subtitle?.let { subtitle ->
                    Text(
                        subtitleText(subtitle),
                        style = cardTextStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(CarSpacing.medium))
            CallButton(
                stringResource(R.string.decline),
                R.drawable.ic_call_end,
                CALL_NOTIFICATION_DECLINE_COLOR,
                onDecline,
            )
            Spacer(Modifier.width(CALL_BUTTON_GAP))
            CallButton(
                stringResource(R.string.answer),
                R.drawable.ic_call,
                CALL_NOTIFICATION_ANSWER_COLOR,
                onAnswer,
            )
        }
    }
}

@Composable
private fun CallerAvatar(incoming: IncomingCallUiState) {
    val photo = incoming.photo
    val appIcon = incoming.appIcon
    when {
        photo != null ->
            Box(Modifier.size(CALLER_AVATAR_SIZE)) {
                Image(
                    photo,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
                if (appIcon != null) {
                    AppIcon(appIcon, APP_BADGE_SIZE, Modifier.align(Alignment.BottomEnd))
                }
            }
        appIcon != null -> AppIcon(appIcon, CALLER_AVATAR_SIZE)
        else ->
            IconCircle {
                Icon(
                    painterResource(R.drawable.ic_call),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(CALL_ICON_SIZE),
                )
            }
    }
}

@Composable
private fun CallButton(
    label: String,
    @DrawableRes icon: Int,
    color: Color,
    onClick: () -> Unit,
) {
    FilledIconButton(
        onClick,
        Modifier.size(MEDIUM_ICON_BUTTON_CONTAINER_HEIGHT),
        shape = CircleShape,
        colors =
            IconButtonDefaults.filledIconButtonColors(
                containerColor = color,
                contentColor = Color.White,
            ),
    ) {
        Icon(painterResource(icon), label, Modifier.size(MEDIUM_ICON_BUTTON_ICON_SIZE))
    }
}

@Composable
private fun callerText(caller: Caller): String =
    when (caller) {
        is Caller.Contact -> caller.name
        is Caller.Named -> caller.name
        is Caller.Number -> leftToRight(caller.number.formatted)
        Caller.Private -> stringResource(R.string.caller_private)
        Caller.Payphone -> stringResource(R.string.caller_payphone)
        Caller.Unknown -> stringResource(R.string.caller_unknown)
    }

@Composable
private fun subtitleText(subtitle: CallSubtitle): String =
    when (subtitle) {
        is CallSubtitle.App -> subtitle.name
        is CallSubtitle.Number -> {
            val number = leftToRight(subtitle.number)
            subtitle.label?.let { stringResource(R.string.caller_number_with_label, it, number) }
                ?: number
        }
        is CallSubtitle.Location -> subtitle.location
    }

private fun leftToRight(number: String): String =
    BidiFormatter.getInstance().unicodeWrap(number, TextDirectionHeuristics.LTR)
