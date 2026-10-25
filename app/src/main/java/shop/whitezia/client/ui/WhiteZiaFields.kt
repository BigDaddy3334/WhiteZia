package shop.whitezia.client.ui


import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.intl.Locale as ComposeLocale
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import shop.whitezia.client.R
import shop.whitezia.client.model.Choice
import shop.whitezia.client.model.WhiteZiaOptions

internal enum class CompactActionTone {
    Default,
    Accent,
    Success,
    Danger,
}

@Composable
internal fun SetupInfoRow(
    icon: ImageVector,
    iconContentDescription: String,
    label: String,
    value: String,
    detail: String,
    color: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(color.copy(alpha = 0.14f))
                .border(1.5.dp, color.copy(alpha = 0.22f), RoundedCornerShape(9.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = iconContentDescription,
                tint = color,
                modifier = Modifier.size(16.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 9.sp,
                    color = WhiteZiaPalette.Muted,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Spacer(modifier = Modifier.height(WhiteZiaSpacing.xs))
            Text(
                text = value,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 13.sp,
                    color = WhiteZiaPalette.Ink,
                    fontWeight = FontWeight.SemiBold,
                ),
            )
            Spacer(modifier = Modifier.height(WhiteZiaSpacing.xs))
            Text(
                text = detail,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 10.sp,
                    color = color,
                    fontWeight = FontWeight.Medium,
                ),
            )
        }
    }
}

@Composable
internal fun SetupActionButton(
    label: String,
    supportingText: String,
    icon: ImageVector,
    iconContentDescription: String,
    emphasized: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val haptic = rememberHapticFeedback()
    val background = when {
        !enabled -> WhiteZiaPalette.SurfaceAlt
        emphasized -> WhiteZiaPalette.Accent
        else -> WhiteZiaPalette.SurfaceAlt
    }
    val border = when {
        !enabled -> WhiteZiaPalette.Divider
        emphasized -> WhiteZiaPalette.AccentPressed
        else -> WhiteZiaPalette.Border
    }
    val foreground = when {
        !enabled -> WhiteZiaPalette.Disabled
        emphasized -> WhiteZiaPalette.OnAccent
        else -> WhiteZiaPalette.Ink
    }
    val secondary = when {
        !enabled -> WhiteZiaPalette.Disabled
        emphasized -> WhiteZiaPalette.OnAccent.copy(alpha = 0.78f)
        else -> WhiteZiaPalette.Muted
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(11.dp))
            .background(background)
            .border(1.5.dp, border, RoundedCornerShape(11.dp))
            .clickable(enabled = enabled) {
                haptic.performMedium()
                onClick()
            }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = iconContentDescription,
            tint = foreground,
            modifier = Modifier.size(18.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 12.sp,
                    color = foreground,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Text(
                text = supportingText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 10.sp,
                    color = secondary,
                    fontWeight = FontWeight.Medium,
                ),
            )
        }
    }
}

internal data class CompactMetric(
    val icon: ImageVector,
    val iconContentDescription: String,
    val label: String,
    val value: String,
)

@Composable
internal fun CompactMetricRow(
    metrics: List<CompactMetric>,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        metrics.forEach { metric ->
            CompactMetricPill(
                metric = metric,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun CompactMetricPill(
    metric: CompactMetric,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(WhiteZiaPalette.SuccessSurface)
            .border(1.5.dp, WhiteZiaPalette.Success.copy(alpha = 0.16f), RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = metric.icon,
            contentDescription = metric.iconContentDescription,
            tint = WhiteZiaPalette.Success,
            modifier = Modifier.size(15.dp),
        )
        Column(
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = metric.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 8.sp,
                    letterSpacing = 0.6.sp,
                    color = WhiteZiaPalette.Muted,
                ),
            )
            Text(
                text = metric.value,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = WhiteZiaPalette.Ink,
                ),
            )
        }
    }
}

@Composable
internal fun ResolverActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    enabled: Boolean = true,
) {
    val haptic = rememberHapticFeedback()
    val background = when {
        !enabled -> WhiteZiaPalette.SurfaceAlt
        emphasized -> WhiteZiaPalette.Accent
        else -> WhiteZiaPalette.SurfaceAlt
    }
    val border = when {
        !enabled -> WhiteZiaPalette.Divider
        emphasized -> WhiteZiaPalette.AccentPressed
        else -> WhiteZiaPalette.Border
    }
    val textColor = when {
        !enabled -> WhiteZiaPalette.Disabled
        emphasized -> WhiteZiaPalette.OnAccent
        else -> WhiteZiaPalette.Muted
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .border(1.5.dp, border, RoundedCornerShape(10.dp))
            .clickable(enabled = enabled) {
                haptic.performLight()
                onClick()
            }
            .padding(horizontal = 10.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 9.sp,
                color = textColor,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.sp,
            ),
        )
    }
}

@Composable
internal fun SectionCard(
    title: String,
    expanded: Boolean,
    icon: ImageVector = Icons.Rounded.Tune,
    iconContentDescription: String,
    collapsible: Boolean = true,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val context = LocalContext.current
    val haptic = rememberHapticFeedback()
    val collapseDescription = stringResource(R.string.cd_section_collapse, title)
    val expandDescription = stringResource(R.string.cd_section_expand, title)
    val isOpen = expanded || !collapsible
    val rotation by animateFloatAsState(
        targetValue = if (isOpen) 180f else 0f,
        animationSpec = tween(260, easing = FastOutSlowInEasing),
        label = "sectionRotation",
    )
    val borderColor by animateColorAsState(
        targetValue = if (isOpen) {
            WhiteZiaPalette.Accent.copy(alpha = 0.26f)
        } else {
            WhiteZiaPalette.Border
        },
        animationSpec = tween(220),
        label = "sectionBorderColor",
    )
    val iconBackground by animateColorAsState(
        targetValue = if (isOpen) {
            WhiteZiaPalette.AccentSurface
        } else {
            WhiteZiaPalette.SurfaceAlt
        },
        animationSpec = tween(220),
        label = "sectionIconBackground",
    )
    val iconColor by animateColorAsState(
        targetValue = if (isOpen) WhiteZiaPalette.AccentText else WhiteZiaPalette.Muted,
        animationSpec = tween(220),
        label = "sectionIconColor",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(WhiteZiaPalette.Surface)
            .border(1.5.dp, borderColor, RoundedCornerShape(18.dp)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .let { modifier ->
                    if (collapsible) {
                        modifier
                            .semantics {
                                contentDescription = if (expanded) {
                                    collapseDescription
                                } else {
                                    expandDescription
                                }
                            }
                            .clickable {
                                haptic.performLight()
                                onToggle()
                            }
                    } else {
                        modifier
                    }
                }
                .padding(horizontal = 14.dp, vertical = 13.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(iconBackground),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = iconContentDescription,
                        tint = iconColor,
                        modifier = Modifier.size(17.dp),
                    )
                }
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = WhiteZiaPalette.Ink,
                            letterSpacing = 0.6.sp,
                        ),
                    )
                    if (collapsible) {
                        Text(
                            text = if (expanded) WhiteZiaL10n.tapToCollapse else WhiteZiaL10n.tapToConfigure,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.sp,
                                color = WhiteZiaPalette.Description,
                                fontWeight = FontWeight.Medium,
                                letterSpacing = 1.1.sp,
                            ),
                        )
                    }
                }
            }
            if (collapsible) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(
                            if (expanded) {
                                WhiteZiaPalette.Accent
                            } else {
                                WhiteZiaPalette.SurfaceAlt
                            },
                        )
                        .padding(start = 10.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = if (expanded) WhiteZiaL10n.parallelTestOpen else WhiteZiaL10n.parallelTestClosed,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 9.sp,
                            color = if (expanded) WhiteZiaPalette.OnAccent else WhiteZiaPalette.Muted,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 0.8.sp,
                        ),
                    )
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = stringResource(
                            if (expanded) R.string.cd_advanced_settings_collapse else R.string.cd_advanced_settings_expand
                        ),
                        tint = if (expanded) WhiteZiaPalette.OnAccent else WhiteZiaPalette.Muted,
                        modifier = Modifier
                            .size(16.dp)
                            .graphicsLayer(rotationZ = rotation),
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = isOpen,
            enter = fadeIn(animationSpec = tween(240)) + expandVertically(animationSpec = tween(240)),
            exit = fadeOut(animationSpec = tween(180)) + shrinkVertically(animationSpec = tween(180)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(WhiteZiaPalette.Surface)
                    .padding(horizontal = 16.dp, vertical = 16.dp),
            ) {
                content()
            }
        }
    }
}

@Composable
internal fun GroupLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.bodyMedium.copy(
            fontSize = 12.sp,
            color = WhiteZiaPalette.SectionTitle,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.8.sp,
        ),
    )
    Spacer(modifier = Modifier.height(WhiteZiaSpacing.sm))
}

@Composable
internal fun SectionDivider() {
    Spacer(modifier = Modifier.height(WhiteZiaSpacing.md))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(2.dp)
            .background(WhiteZiaPalette.Divider, RoundedCornerShape(1.dp)),
    )
    Spacer(modifier = Modifier.height(WhiteZiaSpacing.md))
}

@Composable
internal fun ToggleRow(
    label: String,
    enabled: Boolean,
    interactiveEnabled: Boolean = true,
    onToggle: () -> Unit,
) {
    val haptic = rememberHapticFeedback()
    val contentAlpha = if (interactiveEnabled) 1f else 0.46f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = enabled,
                enabled = interactiveEnabled,
                role = Role.Switch,
                onValueChange = {
                    haptic.performLight()
                    onToggle()
                },
            )
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 13.sp,
                color = WhiteZiaPalette.FieldLabel.copy(alpha = contentAlpha),
                fontWeight = FontWeight.Medium,
            ),
        )
        Switch(
            checked = enabled,
            onCheckedChange = null,
            enabled = interactiveEnabled,
            modifier = Modifier.clearAndSetSemantics {},
            colors = SwitchDefaults.colors(
                checkedThumbColor = WhiteZiaPalette.OnAccent,
                checkedTrackColor = WhiteZiaPalette.Accent,
                checkedBorderColor = WhiteZiaPalette.Accent,
                uncheckedThumbColor = WhiteZiaPalette.Muted,
                uncheckedTrackColor = WhiteZiaPalette.Input,
                uncheckedBorderColor = WhiteZiaPalette.ControlBorder,
            ),
        )
    }
}

@Composable
internal fun WhiteZiaTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    onFocusChange: (Boolean) -> Unit = {},
    rawValue: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    val borderColor = if (focused) WhiteZiaPalette.Accent.copy(alpha = 0.60f) else WhiteZiaPalette.Divider
    val shape = RoundedCornerShape(10.dp)
    val textStyle = MaterialTheme.typography.bodyMedium.copy(
        color = WhiteZiaPalette.Ink,
        fontSize = 14.sp,
    )
    val fieldTextStyle = if (rawValue) {
        textStyle.copy(
            localeList = LocaleList(ComposeLocale("en-US")),
            textDirection = TextDirection.Ltr,
        )
    } else {
        textStyle
    }

    Column(modifier = modifier) {
        FieldLabel(label)
        val textField: @Composable () -> Unit = {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged {
                        focused = it.isFocused
                        onFocusChange(it.isFocused)
                    },
                singleLine = singleLine,
                minLines = minLines,
                maxLines = maxLines,
                keyboardOptions = keyboardOptions,
                visualTransformation = visualTransformation,
                textStyle = fieldTextStyle,
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(shape)
                            .background(WhiteZiaPalette.Input)
                            .border(2.5.dp, borderColor, shape)
                            .padding(horizontal = 12.dp, vertical = 11.dp),
                    ) {
                        if (value.isEmpty()) {
                            Text(
                                text = placeholder,
                                style = fieldTextStyle.copy(color = WhiteZiaPalette.Placeholder),
                            )
                        }
                        innerTextField()
                    }
                },
            )
        }
        if (rawValue) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                textField()
            }
        } else {
            textField()
        }
    }
}

@Composable
internal fun FieldLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(
            fontSize = 13.sp,
            color = WhiteZiaPalette.FieldLabel,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.9.sp,
        ),
    )
    Spacer(modifier = Modifier.height(WhiteZiaSpacing.xs))
}

@Composable
internal fun localizedBalancingStrategies(): List<Choice<Int>> = listOf(
    Choice(1, WhiteZiaL10n.balancingStrategyRandom),
    Choice(2, WhiteZiaL10n.balancingStrategyRoundRobin),
    Choice(3, WhiteZiaL10n.balancingStrategyLeastLoss),
    Choice(4, WhiteZiaL10n.balancingStrategyLowestLatency),
)

@Composable
internal fun localizedCompressionTypes(): List<Choice<Int>> = listOf(
    Choice(0, WhiteZiaL10n.compressionOff),
    Choice(1, WhiteZiaL10n.compressionZstd),
    Choice(2, WhiteZiaL10n.compressionLz4),
    Choice(3, WhiteZiaL10n.compressionZlib),
)

@Composable
internal fun localizedSplitTunnelModes(): List<Choice<String>> = listOf(
    Choice(WhiteZiaOptions.SplitTunnelModeOff, WhiteZiaL10n.splitTunnelAllAppsChoice),
    Choice(WhiteZiaOptions.SplitTunnelModeInclude, WhiteZiaL10n.splitTunnelOnlySelectedChoice),
    Choice(WhiteZiaOptions.SplitTunnelModeExclude, WhiteZiaL10n.splitTunnelBypassSelectedChoice),
)

@Composable
internal fun localizedEncryptionMethods(): List<Choice<Int>> = listOf(
    Choice(0, WhiteZiaL10n.encryptionMethodNone),
    Choice(1, WhiteZiaL10n.encryptionMethodXor),
    Choice(2, WhiteZiaL10n.encryptionMethodChacha20),
    Choice(3, WhiteZiaL10n.encryptionMethodAes128),
    Choice(4, WhiteZiaL10n.encryptionMethodAes192),
    Choice(5, WhiteZiaL10n.encryptionMethodAes256),
)

@Composable
internal fun <T> WhiteZiaDropdownField(
    label: String,
    value: T,
    options: List<Choice<T>>,
    onValueChange: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    compact: Boolean = false,
) {
    val haptic = rememberHapticFeedback()
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.value == value }?.label.orEmpty()
    val shape = RoundedCornerShape(if (compact) 10.dp else 12.dp)
    val horizontalPadding = if (compact) 10.dp else 12.dp
    val verticalPadding = if (compact) 8.dp else 10.dp
    val borderColor by animateColorAsState(
        targetValue = if (!enabled) {
            WhiteZiaPalette.Divider
        } else if (expanded) {
            WhiteZiaPalette.Accent.copy(alpha = 0.60f)
        } else {
            WhiteZiaPalette.ControlBorder
        },
        animationSpec = tween(180),
        label = "dropdownBorderColor",
    )
    val backgroundColor by animateColorAsState(
        targetValue = when {
            !enabled -> WhiteZiaPalette.SurfaceAlt
            expanded -> WhiteZiaPalette.DropdownSurface
            else -> WhiteZiaPalette.DropdownSurface
        },
        animationSpec = tween(180),
        label = "dropdownBackgroundColor",
    )
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "dropdownArrowRotation",
    )

    Column(modifier = modifier) {
        FieldLabel(label)
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(backgroundColor)
                    .border(1.5.dp, borderColor, shape)
                    .clickable(enabled = enabled) {
                        haptic.performLight()
                        expanded = true
                    }
                    .padding(horizontal = horizontalPadding, vertical = verticalPadding),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = selectedLabel.ifEmpty { WhiteZiaL10n.dropdownPlaceholderSelect },
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.sp,
                        color = if (enabled) WhiteZiaPalette.Ink else WhiteZiaPalette.Disabled,
                        fontWeight = FontWeight.Medium,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = stringResource(R.string.cd_dropdown_advanced_settings),
                    tint = when {
                        !enabled -> WhiteZiaPalette.Disabled
                        expanded -> WhiteZiaPalette.Accent
                        else -> WhiteZiaPalette.Muted
                    },
                    modifier = Modifier
                        .size(18.dp)
                        .graphicsLayer(rotationZ = arrowRotation),
                )
            }
            DropdownMenu(
                expanded = expanded && enabled,
                onDismissRequest = { expanded = false },
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(WhiteZiaPalette.DropdownSurface),
            ) {
                options.forEach { choice ->
                    val selected = choice.value == value
                    DropdownMenuItem(
                        modifier = Modifier
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (selected) {
                                    WhiteZiaPalette.AccentSurface
                                } else {
                                    Color.Transparent
                                },
                            ),
                        text = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = choice.label,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontSize = 13.sp,
                                        color = if (selected) WhiteZiaPalette.AccentText else WhiteZiaPalette.Ink,
                                        fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                if (selected) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = stringResource(R.string.cd_icon_check),
                                        tint = WhiteZiaPalette.AccentText,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        },
                        onClick = {
                            haptic.performLight()
                            expanded = false
                            onValueChange(choice.value)
                        },
                    )
                }
            }
        }
    }
}

internal fun displayProxyIpAddress(
    listenIp: String,
    networkIpAddress: String,
): String {
    return when (listenIp.trim()) {
        "0.0.0.0", "::", "[::]" -> networkIpAddress.ifBlank { "127.0.0.1" }
        "" -> "127.0.0.1"
        else -> listenIp.trim()
    }
}

@Suppress("DEPRECATION")
internal fun filterDecimalInput(value: String): String {
    var hasDecimalPoint = false
    return buildString {
        value.forEach { character ->
            when {
                character.isDigit() -> append(character)
                character == '.' && !hasDecimalPoint -> {
                    hasDecimalPoint = true
                    append(character)
                }
            }
        }
    }
}
