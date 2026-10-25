package shop.whitezia.client.ui

import android.content.Context
import android.content.Intent
import android.net.Uri

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Favorite
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import shop.whitezia.client.R

@Composable
internal fun FooterLink() {
    val context = LocalContext.current
    val haptic = rememberHapticFeedback()
    val telegramDescription = stringResource(R.string.cd_telegram_link)

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = WhiteZiaL10n.footerPoweredBy,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 10.sp,
                color = WhiteZiaPalette.Description,
            ),
        )
        Spacer(modifier = Modifier.height(WhiteZiaSpacing.xs))
        Text(
            text = WhiteZiaTelegramUrl,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .semantics {
                    contentDescription = telegramDescription
                }
                .clickable {
                    haptic.performLight()
                    openWhiteZiaTelegram(context)
                }
                .padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 9.sp,
                color = WhiteZiaPalette.AccentText,
            ),
        )
    }
}

@Composable
private fun ThemeModeSegmentedControl(
    selectedMode: String,
    onModeChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = rememberHapticFeedback()

    val themeOptions: List<Pair<String, String>> = listOf(
        "dark" to WhiteZiaL10n.themeModeDark,
        "light" to WhiteZiaL10n.themeModeLight,
        "system" to WhiteZiaL10n.themeModeAuto,
    )
    Column(modifier = modifier) {
        FieldLabel(WhiteZiaL10n.fieldTheme)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(WhiteZiaPalette.Surface)
                .border(1.5.dp, WhiteZiaPalette.ControlBorder, RoundedCornerShape(12.dp))
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            themeOptions.forEach { (modeValue, modeLabel) ->
                val selected = selectedMode == modeValue
                val background by animateColorAsState(
                    targetValue = if (selected) WhiteZiaPalette.Accent else Color.Transparent,
                    animationSpec = tween(180),
                    label = "themeModeSegmentBackground",
                )
                val textColor by animateColorAsState(
                    targetValue = if (selected) WhiteZiaPalette.OnAccent else WhiteZiaPalette.Muted,
                    animationSpec = tween(180),
                    label = "themeModeSegmentText",
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(9.dp))
                        .background(background)
                        .selectable(
                            selected = selected,
                            role = Role.RadioButton,
                            onClick = {
                                haptic.performLight()
                                onModeChange(modeValue)
                            },
                        )
                        .semantics(mergeDescendants = true) {}
                        .padding(horizontal = 6.dp, vertical = 9.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = modeLabel,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 10.sp,
                            color = textColor,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            letterSpacing = 0.4.sp,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
internal fun HeaderCard(
    themeMode: String,
    onThemeModeChange: (String) -> Unit,
    languageCode: String,
    onLanguageCodeChange: (String) -> Unit,
) {
    val context = LocalContext.current
    val haptic = rememberHapticFeedback()
    var overflowExpanded by remember { mutableStateOf(false) }
    var showAppSettingsDialog by remember { mutableStateOf(false) }
    var showDonationDialog by remember { mutableStateOf(false) }
    val appVersion = remember(context.packageName) { appVersionName(context) }
    val telegramLogoDescription = stringResource(R.string.cd_logo_telegram)
    val menuDescription = stringResource(R.string.cd_menu_button)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 420.dp)
            .padding(horizontal = 20.dp, vertical = 22.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(WhiteZiaPalette.SurfaceAlt)
                    .border(1.5.dp, WhiteZiaPalette.Border, RoundedCornerShape(9.dp))
                    .semantics { contentDescription = telegramLogoDescription }
                    .clickable {
                        haptic.performLight()
                        openWhiteZiaTelegram(context)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "W",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = WhiteZiaPalette.AccentText,
                    ),
                )
            }
            Text(
                text = "WhiteZia",
                style = MaterialTheme.typography.headlineSmall.copy(
                    color = WhiteZiaPalette.Ink,
                ),
            )
        }

        Box {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (overflowExpanded) WhiteZiaPalette.AccentSurface else WhiteZiaPalette.Surface,
                    )
                    .border(
                        1.5.dp,
                        if (overflowExpanded) WhiteZiaPalette.Accent.copy(alpha = 0.28f) else WhiteZiaPalette.Border,
                        RoundedCornerShape(10.dp),
                    )
                    .semantics { contentDescription = menuDescription }
                    .clickable {
                        haptic.performLight()
                        overflowExpanded = true
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = WhiteZiaL10n.cdAppMenu,
                    tint = if (overflowExpanded) WhiteZiaPalette.AccentText else WhiteZiaPalette.Muted,
                    modifier = Modifier.size(22.dp),
                )
            }

            DropdownMenu(
                expanded = overflowExpanded,
                onDismissRequest = { overflowExpanded = false },
                modifier = Modifier
                    .widthIn(min = 220.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(WhiteZiaPalette.DropdownSurface),
            ) {
                DropdownMenuItem(
                    modifier = Modifier
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                        .clip(RoundedCornerShape(10.dp)),
                    enabled = false,
                    text = {
                        Column {
                            Text(
                                text = WhiteZiaL10n.menuVersion,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 10.sp,
                                    color = WhiteZiaPalette.Muted,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.7.sp,
                                ),
                            )
                            Spacer(modifier = Modifier.height(WhiteZiaSpacing.xs))
                            Text(
                                text = "v${appVersion.ifBlank { "unknown" }}",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 13.sp,
                                    color = WhiteZiaPalette.Ink,
                                    fontWeight = FontWeight.Medium,
                                ),
                            )
                        }
                    },
                    onClick = {},
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp)
                        .height(1.5.dp)
                        .background(WhiteZiaPalette.Divider),
                )
                DropdownMenuItem(
                    modifier = Modifier
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                        .clip(RoundedCornerShape(10.dp)),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = WhiteZiaL10n.cdAppSettings,
                            tint = WhiteZiaPalette.Ink,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    text = {
                        Text(
                            text = WhiteZiaL10n.menuAppSettings,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 13.sp,
                                color = WhiteZiaPalette.Ink,
                                fontWeight = FontWeight.Medium,
                            ),
                        )
                    },
                    onClick = {
                        haptic.performLight()
                        overflowExpanded = false
                        showAppSettingsDialog = true
                    },
                )
                DropdownMenuItem(
                    modifier = Modifier
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                        .clip(RoundedCornerShape(10.dp)),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Favorite,
                            contentDescription = WhiteZiaL10n.cdDonate,
                            tint = WhiteZiaPalette.Ink,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    text = {
                        Text(
                            text = WhiteZiaL10n.menuDonate,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 13.sp,
                                color = WhiteZiaPalette.Ink,
                                fontWeight = FontWeight.Medium,
                            ),
                        )
                    },
                    onClick = {
                        haptic.performLight()
                        overflowExpanded = false
                        showDonationDialog = true
                    },
                )
            }
        }
    }

    if (showAppSettingsDialog) {
        AppSettingsDialog(
            themeMode = themeMode,
            onThemeModeChange = onThemeModeChange,
            languageCode = languageCode,
            onLanguageCodeChange = onLanguageCodeChange,
            onDismiss = { showAppSettingsDialog = false },
        )
    }
    if (showDonationDialog) {
        DonationDialog(onDismiss = { showDonationDialog = false })
    }
}

internal fun appVersionName(context: Context): String {
    return runCatching {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull().orEmpty()
}

private fun openWhiteZiaTelegram(context: Context) {
    val intent = Intent(
        Intent.ACTION_VIEW,
        Uri.parse(WhiteZiaTelegramUrl),
    )
    context.startActivity(intent)
}

@Composable
private fun LanguageModeSegmentedControl(
    selectedCode: String,
    onCodeChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = rememberHapticFeedback()
    val options: List<Pair<String, String>> = listOf(
        "en" to WhiteZiaL10n.languageEn,
        "fa" to WhiteZiaL10n.languageFa,
    )

    Column(modifier = modifier) {
        FieldLabel(WhiteZiaL10n.fieldLanguage)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(WhiteZiaPalette.Surface)
                .border(1.5.dp, WhiteZiaPalette.ControlBorder, RoundedCornerShape(12.dp))
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            options.forEach { (code, label) ->
                val selected = selectedCode == code
                val background by animateColorAsState(
                    targetValue = if (selected) WhiteZiaPalette.Accent else Color.Transparent,
                    animationSpec = tween(180),
                    label = "langSegmentBackground",
                )
                val textColor by animateColorAsState(
                    targetValue = if (selected) WhiteZiaPalette.OnAccent else WhiteZiaPalette.Muted,
                    animationSpec = tween(180),
                    label = "langSegmentText",
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(9.dp))
                        .background(background)
                        .selectable(
                            selected = selected,
                            role = Role.RadioButton,
                            onClick = {
                                haptic.performLight()
                                onCodeChange(code)
                            },
                        )
                        .semantics(mergeDescendants = true) {}
                        .padding(horizontal = 6.dp, vertical = 9.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 10.sp,
                            color = textColor,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            letterSpacing = 0.4.sp,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun AppSettingsDialog(
    themeMode: String,
    onThemeModeChange: (String) -> Unit,
    languageCode: String,
    onLanguageCodeChange: (String) -> Unit,
    onDismiss: () -> Unit,
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
                text = WhiteZiaL10n.appSettingsTitle,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    color = WhiteZiaPalette.Ink,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.1.sp,
                ),
            )
            Spacer(modifier = Modifier.height(WhiteZiaSpacing.md))
            ThemeModeSegmentedControl(
                selectedMode = themeMode,
                onModeChange = onThemeModeChange,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(WhiteZiaSpacing.md))
            LanguageModeSegmentedControl(
                selectedCode = languageCode,
                onCodeChange = onLanguageCodeChange,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(WhiteZiaSpacing.lg))
            CompactActionButton(
                modifier = Modifier.fillMaxWidth(),
                label = WhiteZiaL10n.btnClose,
                emphasized = true,
                enabled = true,
                onClick = onDismiss,
            )
        }
    }
}

@Composable
private fun DonationDialog(
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(WhiteZiaPalette.Surface)
                .border(1.5.dp, WhiteZiaPalette.Border, RoundedCornerShape(22.dp))
                .verticalScroll(rememberScrollState())
                .padding(18.dp),
        ) {
            Text(
                text = WhiteZiaL10n.supportTitle,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    color = WhiteZiaPalette.Ink,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.1.sp,
                ),
            )
            Spacer(modifier = Modifier.height(WhiteZiaSpacing.sm))
            Text(
                text = WhiteZiaL10n.supportBody,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    color = WhiteZiaPalette.Description,
                ),
            )
            Spacer(modifier = Modifier.height(WhiteZiaSpacing.md))
            DonationWallets.forEachIndexed { index, wallet ->
                DonationWalletField(
                    label = wallet.label,
                    address = wallet.address,
                    onCopy = {
                        copyTextToClipboard(
                            context = context,
                            label = wallet.label,
                            text = wallet.address,
                            sensitive = false,
                        )
                    },
                )
                if (index != DonationWallets.lastIndex) {
                    Spacer(modifier = Modifier.height(WhiteZiaSpacing.inputSpacing))
                }
            }
            Spacer(modifier = Modifier.height(WhiteZiaSpacing.lg))
            CompactActionButton(
                modifier = Modifier.fillMaxWidth(),
                label = WhiteZiaL10n.btnClose,
                emphasized = true,
                enabled = true,
                onClick = onDismiss,
            )
        }
    }
}

@Composable
private fun DonationWalletField(
    label: String,
    address: String,
    onCopy: () -> Unit,
) {
    val copyAddressDescription = stringResource(R.string.cd_copy_address, address)
    Column {
        FieldLabel(label)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(WhiteZiaPalette.Input)
                .border(2.5.dp, WhiteZiaPalette.Divider, RoundedCornerShape(10.dp))
                .semantics { contentDescription = copyAddressDescription }
                .clickable(onClick = onCopy)
                .padding(horizontal = 12.dp, vertical = 11.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                modifier = Modifier.weight(1f),
                text = address,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = WhiteZiaPalette.Ink,
                    fontSize = 12.sp,
                ),
            )
            Text(
                text = WhiteZiaL10n.btnCopy,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = WhiteZiaPalette.AccentText,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                ),
            )
        }
    }
}
