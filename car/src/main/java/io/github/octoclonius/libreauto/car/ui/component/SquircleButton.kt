package io.github.octoclonius.libreauto.car.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import io.github.octoclonius.libreauto.car.ui.theme.SquircleShape

@Composable
fun SquircleButton(
    onClick: () -> Unit,
    inset: Dp,
    corner: Dp,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val interactions = remember { MutableInteractionSource() }
    Box(
        modifier.clickable(
            interactionSource = interactions,
            indication = null,
            role = Role.Button,
            onClick = onClick,
        ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.matchParentSize()
                .padding(inset)
                .clip(SquircleShape(corner))
                .indication(interactions, ripple())
        )
        content()
    }
}
