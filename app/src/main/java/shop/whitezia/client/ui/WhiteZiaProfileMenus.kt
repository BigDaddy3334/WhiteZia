package shop.whitezia.client.ui


import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

internal data class ProfileMenuAction(
    val label: String,
    val icon: ImageVector,
    val contentDescription: String,
    val enabled: Boolean,
    val onClick: () -> Unit,
)

@Composable
internal fun ProfileActionsMenu(
    contentDescription: String,
    actions: List<ProfileMenuAction>,
) {
    var expanded by remember { mutableStateOf(false) }
    val haptic = rememberHapticFeedback()
    val enabled = actions.any { it.enabled }
    val background = when {
        !enabled -> WhiteZiaPalette.SurfaceAlt
        expanded -> WhiteZiaPalette.AccentSurface
        else -> WhiteZiaPalette.Surface
    }
    val border = when {
        !enabled -> WhiteZiaPalette.Divider
        expanded -> WhiteZiaPalette.Accent.copy(alpha = 0.28f)
        else -> WhiteZiaPalette.Border
    }
    val iconColor = when {
        !enabled -> WhiteZiaPalette.Disabled
        expanded -> WhiteZiaPalette.AccentText
        else -> WhiteZiaPalette.Muted
    }

    Box {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(background)
                .border(1.5.dp, border, RoundedCornerShape(10.dp))
                .clickable(enabled = enabled) {
                    haptic.performLight()
                    expanded = true
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.MoreVert,
                contentDescription = contentDescription,
                tint = iconColor,
                modifier = Modifier.size(22.dp),
            )
        }
        DropdownMenu(
            expanded = expanded && enabled,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .widthIn(min = 196.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(WhiteZiaPalette.DropdownSurface),
        ) {
            actions.forEach { action ->
                val itemColor = if (action.enabled) WhiteZiaPalette.Ink else WhiteZiaPalette.Disabled
                DropdownMenuItem(
                    modifier = Modifier
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                        .clip(RoundedCornerShape(10.dp)),
                    enabled = action.enabled,
                    leadingIcon = {
                        Icon(
                            imageVector = action.icon,
                            contentDescription = action.contentDescription,
                            tint = itemColor,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    text = {
                        Text(
                            text = action.label,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 13.sp,
                                color = itemColor,
                                fontWeight = FontWeight.Medium,
                            ),
                        )
                    },
                    onClick = {
                        haptic.performLight()
                        expanded = false
                        action.onClick()
                    },
                )
            }
        }
    }
}

@Composable
internal fun DeleteProfileConfirmationDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(WhiteZiaPalette.Surface)
                .border(1.5.dp, WhiteZiaPalette.Border, RoundedCornerShape(22.dp))
                .padding(18.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    color = WhiteZiaPalette.Ink,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.1.sp,
                ),
            )
            Spacer(modifier = Modifier.height(WhiteZiaSpacing.inputSpacing))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    color = WhiteZiaPalette.Description,
                ),
            )
            Spacer(modifier = Modifier.height(WhiteZiaSpacing.md))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CompactActionButton(
                    modifier = Modifier.weight(1f),
                    label = WhiteZiaL10n.btnCancel,
                    emphasized = false,
                    enabled = true,
                    onClick = onDismiss,
                )
                CompactActionButton(
                    modifier = Modifier.weight(1f),
                    label = confirmLabel,
                    emphasized = true,
                    enabled = true,
                    onClick = onConfirm,
                )
            }
        }
    }
}

@Composable
internal fun ProfileDragHandle(
    enabled: Boolean,
    dragging: Boolean,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
) {
    val haptic = rememberHapticFeedback()
    val background = when {
        !enabled -> WhiteZiaPalette.SurfaceAlt
        dragging -> WhiteZiaPalette.AccentSurface
        else -> WhiteZiaPalette.Surface
    }
    val border = if (dragging) {
        WhiteZiaPalette.Accent.copy(alpha = 0.40f)
    } else if (enabled) {
        WhiteZiaPalette.Border
    } else {
        WhiteZiaPalette.Divider
    }
    val iconColor = when {
        !enabled -> WhiteZiaPalette.Disabled
        dragging -> WhiteZiaPalette.AccentText
        else -> WhiteZiaPalette.Muted
    }

    Box(
        modifier = Modifier
            .size(width = 28.dp, height = 44.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .border(1.5.dp, border, RoundedCornerShape(8.dp))
            .pointerInput(enabled) {
                if (!enabled) {
                    return@pointerInput
                }
                detectVerticalDragGestures(
                    onDragStart = {
                        haptic.performHeavy()
                        onDragStart()
                    },
                    onDragCancel = {
                        onDragCancel()
                    },
                    onDragEnd = {
                        onDragEnd()
                    },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount)
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.DragHandle,
            contentDescription = WhiteZiaL10n.cdDragToReorder,
            tint = iconColor,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
internal fun ProfileIconButton(
    icon: ImageVector,
    contentDescription: String,
    emphasized: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = rememberHapticFeedback()
    val background = when {
        !enabled -> WhiteZiaPalette.SurfaceAlt
        emphasized -> WhiteZiaPalette.Accent
        else -> WhiteZiaPalette.Surface
    }
    val border = when {
        !enabled -> WhiteZiaPalette.Divider
        emphasized -> WhiteZiaPalette.AccentPressed
        else -> WhiteZiaPalette.Border
    }
    val iconColor = when {
        !enabled -> WhiteZiaPalette.Disabled
        emphasized -> WhiteZiaPalette.OnAccent
        else -> WhiteZiaPalette.Muted
    }

    Box(
        modifier = modifier
            .size(28.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .border(1.5.dp, border, RoundedCornerShape(8.dp))
            .clickable(enabled = enabled) {
                haptic.performMedium()
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = iconColor,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
internal fun CompactActionButton(
    label: String,
    emphasized: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: CompactActionTone = if (emphasized) CompactActionTone.Accent else CompactActionTone.Default,
) {
    val haptic = rememberHapticFeedback()
    val background = when {
        !enabled -> WhiteZiaPalette.SurfaceAlt
        tone == CompactActionTone.Accent -> WhiteZiaPalette.Accent
        tone == CompactActionTone.Success -> WhiteZiaPalette.Success
        tone == CompactActionTone.Danger -> WhiteZiaPalette.Error
        else -> WhiteZiaPalette.Surface
    }
    val border = when {
        !enabled -> WhiteZiaPalette.Divider
        tone == CompactActionTone.Accent -> WhiteZiaPalette.AccentPressed
        tone == CompactActionTone.Success -> WhiteZiaPalette.SuccessSurface
        tone == CompactActionTone.Danger -> WhiteZiaPalette.ErrorSurface
        else -> WhiteZiaPalette.Border
    }
    val textColor = when {
        !enabled -> WhiteZiaPalette.Disabled
        tone == CompactActionTone.Default -> WhiteZiaPalette.Muted
        else -> WhiteZiaPalette.OnAccent
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(9.dp))
            .background(background)
            .border(1.5.dp, border, RoundedCornerShape(9.dp))
            .clickable(enabled = enabled) {
                haptic.performMedium()
                onClick()
            }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 8.sp,
                color = textColor,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.9.sp,
            ),
        )
    }
}
