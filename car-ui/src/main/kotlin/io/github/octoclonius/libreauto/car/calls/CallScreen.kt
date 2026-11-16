package io.github.octoclonius.libreauto.car.calls

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.octoclonius.libreauto.car.R
import io.github.octoclonius.libreauto.car.designsystem.theme.CALL_NOTIFICATION_ANSWER_COLOR
import io.github.octoclonius.libreauto.car.designsystem.theme.CALL_NOTIFICATION_DECLINE_COLOR
import io.github.octoclonius.libreauto.car.designsystem.theme.CarSpacing
import io.github.octoclonius.libreauto.car.rail.RAIL_ICON_SIZE
import io.github.octoclonius.libreauto.projection.calls.Caller

internal val CALL_SCREEN_AVATAR_SIZE = 104.dp
private val HELD_CALL_ROW_HEIGHT = 56.dp
private val HELD_CALL_ICON_CIRCLE_SIZE = 32.dp
private val HELD_CALL_ICON_SIZE = 18.dp
private val SCREEN_MARGIN = CarSpacing.extraLarge * 2
private val BUTTON_SIZE = RAIL_ICON_SIZE
private val BUTTON_GAP = CarSpacing.extraLarge
private val END_BUTTON_WIDTH = BUTTON_SIZE * 2 + BUTTON_GAP
private val KEY_GAP = CarSpacing.small
private val KEY_MAX_WIDTH = 200.dp
private val KEY_MAX_HEIGHT = 72.dp
private const val KEYPAD_COLUMNS = 3
private const val KEYPAD_LAYOUT = "123456789*0#"
private const val KEYS = "0123456789*#"
private val KEY_TO_CHARS =
    listOf("+", "", "ABC", "DEF", "GHI", "JKL", "MNO", "PQRS", "TUV", "WXYZ", "", "")

private class ButtonSpot(val width: Dp, val content: @Composable () -> Unit)

@Composable
internal fun CallScreen(uiState: CallScreenUiState, onAction: (CallAction) -> Unit) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        val inCall = uiState as? CallScreenUiState.InCall
        HeldCallRow(inCall?.heldCaller)
        val keypad = inCall?.keypad
        if (keypad == null) {
            CallerRow(uiState.call, Modifier.weight(1f))
        } else {
            Keypad(keypad, onAction, Modifier.weight(1f))
        }
        when (uiState) {
            is CallScreenUiState.InCall -> CallButtons(uiState.buttons, onAction)
            is CallScreenUiState.Ringing -> RingingButtons(uiState, onAction)
        }
        Spacer(Modifier.height(SCREEN_MARGIN))
    }
}

@Composable
private fun RingingButtons(uiState: CallScreenUiState.Ringing, onAction: (CallAction) -> Unit) {
    val id = uiState.call.call.id
    Column(
        Modifier.fillMaxWidth().padding(horizontal = SCREEN_MARGIN),
        verticalArrangement = Arrangement.spacedBy(BUTTON_GAP),
    ) {
        if (uiState.canAnswerAndRelease) {
            Button(
                { onAction(CallAction.AnswerAndRelease(id)) },
                Modifier.fillMaxWidth().height(BUTTON_SIZE),
                shape = CircleShape,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
            ) {
                Text(
                    stringResource(R.string.answer_and_end_ongoing_call),
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(BUTTON_GAP)) {
            PillButton(
                CALL_NOTIFICATION_DECLINE_COLOR,
                R.drawable.ic_call_end,
                R.string.decline,
                Modifier.weight(1f),
            ) {
                onAction(CallAction.Reject(id))
            }
            PillButton(
                CALL_NOTIFICATION_ANSWER_COLOR,
                R.drawable.ic_call,
                R.string.answer,
                Modifier.weight(1f),
            ) {
                onAction(CallAction.Answer(id))
            }
        }
    }
}

@Composable
private fun HeldCallRow(heldCaller: Caller?) {
    Row(
        Modifier.fillMaxWidth().height(HELD_CALL_ROW_HEIGHT).padding(horizontal = CarSpacing.large),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (heldCaller == null) return@Row
        Box(
            Modifier.size(HELD_CALL_ICON_CIRCLE_SIZE)
                .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.ic_phone_paused),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(HELD_CALL_ICON_SIZE),
            )
        }
        Spacer(Modifier.width(CarSpacing.medium))
        Text(
            stringResource(R.string.call_held_caller, callerText(heldCaller)),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CallerRow(uiState: CallUiState, modifier: Modifier) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = SCREEN_MARGIN),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CallerAvatar(uiState, CALL_SCREEN_AVATAR_SIZE)
        Spacer(Modifier.width(CarSpacing.extraLarge))
        Column {
            Text(
                callerText(uiState.call.caller),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            uiState.subtitle?.let { subtitle ->
                Text(
                    subtitleText(subtitle),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun Keypad(keypad: CallScreenKeypad, onAction: (CallAction) -> Unit, modifier: Modifier) {
    val digitsStyle = MaterialTheme.typography.headlineMedium
    val digitsHeight = with(LocalDensity.current) { digitsStyle.lineHeight.toDp() }
    val rows = KEYPAD_LAYOUT.length / KEYPAD_COLUMNS
    DisposableEffect(Unit) { onDispose { onAction(CallAction.StopDtmfTone) } }
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .padding(start = SCREEN_MARGIN, end = SCREEN_MARGIN, bottom = BUTTON_GAP)
    ) {
        val keyWidth =
            minOf((maxWidth - KEY_GAP * (KEYPAD_COLUMNS - 1)) / KEYPAD_COLUMNS, KEY_MAX_WIDTH)
        val keyHeight = minOf((maxHeight - digitsHeight - KEY_GAP * rows) / rows, KEY_MAX_HEIGHT)
        Column(
            Modifier.align(Alignment.CenterStart)
                .width(keyWidth * KEYPAD_COLUMNS + KEY_GAP * (KEYPAD_COLUMNS - 1)),
            verticalArrangement = Arrangement.spacedBy(KEY_GAP),
        ) {
            Text(
                keypad.digits,
                Modifier.fillMaxWidth(),
                style = digitsStyle,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.StartEllipsis,
            )
            KEYPAD_LAYOUT.chunked(KEYPAD_COLUMNS).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(KEY_GAP)) {
                    row.forEach { key ->
                        KeypadKey(
                            key,
                            keypad.toneCallId,
                            onAction,
                            Modifier.width(keyWidth).height(keyHeight),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun KeypadKey(
    key: Char,
    toneCallId: Int?,
    onAction: (CallAction) -> Unit,
    modifier: Modifier,
) {
    val interactions = remember { MutableInteractionSource() }
    val currentToneCallId by rememberUpdatedState(toneCallId)
    val currentOnAction by rememberUpdatedState(onAction)
    Box(
        modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .indication(interactions, ripple())
            .pointerInput(key) {
                detectTapGestures(
                    onPress = { offset ->
                        val press = PressInteraction.Press(offset)
                        interactions.emit(press)
                        currentToneCallId?.let { currentOnAction(CallAction.PlayDtmfTone(it, key)) }
                        val isReleased = tryAwaitRelease()
                        currentOnAction(CallAction.StopDtmfTone)
                        interactions.emit(
                            if (isReleased) PressInteraction.Release(press)
                            else PressInteraction.Cancel(press)
                        )
                    }
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                key.toString(),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            val letters = KEY_TO_CHARS[KEYS.indexOf(key)]
            if (letters.isNotEmpty()) {
                Text(
                    letters,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CallButtons(buttons: CallScreenButtons, onAction: (CallAction) -> Unit) {
    val beforeEnd =
        listOf(
            ButtonSpot(BUTTON_SIZE) {
                buttons.mute?.let {
                    ToggleButton(it, R.drawable.ic_mic_off, R.string.mute, onAction)
                }
            },
            ButtonSpot(BUTTON_SIZE) {
                ToggleButton(buttons.keypad, R.drawable.ic_dialpad, R.string.keypad, onAction)
            },
        )
    val fromEnd =
        listOf(
            ButtonSpot(END_BUTTON_WIDTH) {
                PillButton(
                    CALL_NOTIFICATION_DECLINE_COLOR,
                    R.drawable.ic_call_end,
                    R.string.hang_up,
                    Modifier.width(END_BUTTON_WIDTH),
                ) {
                    onAction(buttons.end)
                }
            },
            ButtonSpot(BUTTON_SIZE) {
                buttons.holdOrMerge?.let { HoldOrMergeButton(it, onAction) }
            },
            ButtonSpot(BUTTON_SIZE) {
                buttons.swap?.let {
                    PlainButton(it, R.drawable.ic_swap_calls, R.string.swap, onAction)
                }
            },
        )
    val spots = beforeEnd + fromEnd
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = SCREEN_MARGIN)) {
        val oneRowWidth =
            spots.fold(0.dp) { width, spot -> width + spot.width } + BUTTON_GAP * (spots.size - 1)
        if (oneRowWidth <= maxWidth) {
            ButtonRow(spots)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(BUTTON_GAP)) {
                ButtonRow(beforeEnd)
                ButtonRow(fromEnd)
            }
        }
    }
}

@Composable
private fun ButtonRow(spots: List<ButtonSpot>) {
    Row(horizontalArrangement = Arrangement.spacedBy(BUTTON_GAP)) {
        spots.forEach { spot ->
            Box(
                Modifier.width(spot.width).height(BUTTON_SIZE),
                contentAlignment = Alignment.Center,
            ) {
                spot.content()
            }
        }
    }
}

@Composable
private fun HoldOrMergeButton(button: CallButton, onAction: (CallAction) -> Unit) {
    if (button.action is CallAction.Merge) {
        PlainButton(button, R.drawable.ic_call_merge, R.string.merge, onAction)
    } else {
        ToggleButton(button, R.drawable.ic_pause, R.string.hold, onAction)
    }
}

@Composable
private fun PlainButton(
    button: CallButton,
    @DrawableRes icon: Int,
    @StringRes label: Int,
    onAction: (CallAction) -> Unit,
) {
    FilledIconButton(
        { onAction(button.action) },
        Modifier.size(BUTTON_SIZE),
        enabled = button.isEnabled,
        shape = CircleShape,
        colors =
            IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
    ) {
        Icon(
            painterResource(icon),
            stringResource(label),
            Modifier.size(MEDIUM_ICON_BUTTON_ICON_SIZE),
        )
    }
}

@Composable
private fun ToggleButton(
    button: CallButton,
    @DrawableRes icon: Int,
    @StringRes label: Int,
    onAction: (CallAction) -> Unit,
) {
    FilledIconToggleButton(
        button.isChecked,
        { onAction(button.action) },
        Modifier.size(BUTTON_SIZE),
        enabled = button.isEnabled,
        shape = CircleShape,
        colors =
            IconButtonDefaults.filledIconToggleButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
    ) {
        Icon(
            painterResource(icon),
            stringResource(label),
            Modifier.size(MEDIUM_ICON_BUTTON_ICON_SIZE),
        )
    }
}

@Composable
private fun PillButton(
    color: Color,
    @DrawableRes icon: Int,
    @StringRes label: Int,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    FilledIconButton(
        onClick,
        modifier.height(BUTTON_SIZE),
        shape = CircleShape,
        colors =
            IconButtonDefaults.filledIconButtonColors(
                containerColor = color,
                contentColor = Color.White,
            ),
    ) {
        Icon(
            painterResource(icon),
            stringResource(label),
            Modifier.size(MEDIUM_ICON_BUTTON_ICON_SIZE),
        )
    }
}
