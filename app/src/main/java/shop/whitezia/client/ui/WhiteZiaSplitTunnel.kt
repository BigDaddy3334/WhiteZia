package shop.whitezia.client.ui

import android.content.Context
import android.content.Intent

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import shop.whitezia.client.R
import shop.whitezia.client.model.WhiteZiaOptions
import shop.whitezia.client.model.WhiteZiaSettings
import java.util.Locale

@Composable
internal fun SplitTunnelSettingsPanel(
    settings: WhiteZiaSettings,
    apps: List<SplitTunnelAppInfo>,
    onSettingsChange: (WhiteZiaSettings) -> Unit,
) {
    var showAppDialog by rememberSaveable { mutableStateOf(false) }
    val selectedPackages = settings.splitTunnelPackages
    val selectedLabels = selectedSplitTunnelLabels(selectedPackages, apps)
    val splitAllAppsLabel = WhiteZiaL10n.splitTunnelAllApps
    val splitNoAppsLabel = WhiteZiaL10n.splitTunnelNoApps
    val appSummary = splitTunnelAppsSummary(
        mode = settings.splitTunnelMode,
        appLabels = selectedLabels,
        allAppsLabel = splitAllAppsLabel,
        noAppsLabel = splitNoAppsLabel,
    )

    InfoCard(
        title = WhiteZiaL10n.splitTunnelTitle,
        compact = true,
    ) {
        WhiteZiaDropdownField(
            label = WhiteZiaL10n.splitTunnelAppRouting,
            value = settings.splitTunnelMode,
            options = localizedSplitTunnelModes(),
            compact = true,
            onValueChange = { mode ->
                onSettingsChange(settings.copy(splitTunnelMode = mode))
            },
        )
        AnimatedVisibility(
            visible = settings.splitTunnelMode != WhiteZiaOptions.SplitTunnelModeOff,
            enter = fadeIn(animationSpec = tween(180)) + expandVertically(animationSpec = tween(180)),
            exit = fadeOut(animationSpec = tween(140)) + shrinkVertically(animationSpec = tween(140)),
        ) {
            Column {
                Spacer(modifier = Modifier.height(WhiteZiaSpacing.sm))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = WhiteZiaL10n.splitTunnelSelected,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 11.sp,
                            color = WhiteZiaPalette.Muted,
                        ),
                    )
                    Text(
                        text = appSummary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = WhiteZiaPalette.Ink,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    CompactActionButton(
                        modifier = Modifier.widthIn(min = 104.dp),
                        label = WhiteZiaL10n.splitTunnelSelectApps,
                        emphasized = true,
                        enabled = apps.isNotEmpty(),
                        onClick = { showAppDialog = true },
                    )
                }
            }
        }
    }

    if (showAppDialog) {
        SplitTunnelAppDialog(
            apps = apps,
            selectedPackages = selectedPackages,
            onDismiss = { showAppDialog = false },
            onSave = { packages ->
                onSettingsChange(settings.copy(splitTunnelPackages = packages))
                showAppDialog = false
            },
        )
    }
}

@Composable
private fun SplitTunnelAppDialog(
    apps: List<SplitTunnelAppInfo>,
    selectedPackages: List<String>,
    onDismiss: () -> Unit,
    onSave: (List<String>) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var selected by remember(selectedPackages.joinToString("|")) {
        mutableStateOf(selectedPackages.toSet())
    }
    val normalizedQuery = query.trim().lowercase(Locale.US)
    val visibleApps = remember(apps, normalizedQuery) {
        if (normalizedQuery.isEmpty()) {
            apps
        } else {
            apps.filter { app ->
                app.label.lowercase(Locale.US).contains(normalizedQuery) ||
                    app.packageName.lowercase(Locale.US).contains(normalizedQuery)
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 620.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(WhiteZiaPalette.Surface)
                .border(1.5.dp, WhiteZiaPalette.Border, RoundedCornerShape(22.dp))
                .padding(18.dp),
        ) {
            Text(
                text = WhiteZiaL10n.splitTunnelDialogTitle,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    color = WhiteZiaPalette.Ink,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.1.sp,
                ),
            )
            Spacer(modifier = Modifier.height(WhiteZiaSpacing.md))
            WhiteZiaTextField(
                label = WhiteZiaL10n.splitTunnelSearchLabel,
                value = query,
                onValueChange = { query = it },
                placeholder = WhiteZiaL10n.appsSearchPlaceholder,
            )
            Spacer(modifier = Modifier.height(WhiteZiaSpacing.inputSpacing))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                if (visibleApps.isEmpty()) {
                    Text(
                        text = WhiteZiaL10n.splitTunnelNoAppsFound,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 11.sp,
                            color = WhiteZiaPalette.Muted,
                        ),
                    )
                } else {
                    visibleApps.forEach { app ->
                        val checked = app.packageName in selected
                        SplitTunnelAppRow(
                            app = app,
                            checked = checked,
                            onToggle = {
                                selected = if (checked) {
                                    selected - app.packageName
                                } else {
                                    selected + app.packageName
                                }
                            },
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(WhiteZiaSpacing.md))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CompactActionButton(
                    modifier = Modifier.weight(1f),
                    label = WhiteZiaL10n.profileBtnClear,
                    emphasized = false,
                    enabled = selected.isNotEmpty(),
                    onClick = { selected = emptySet() },
                )
                CompactActionButton(
                    modifier = Modifier.weight(1f),
                    label = WhiteZiaL10n.btnCancel,
                    emphasized = false,
                    enabled = true,
                    onClick = onDismiss,
                )
                CompactActionButton(
                    modifier = Modifier.weight(1f),
                    label = WhiteZiaL10n.btnSave,
                    emphasized = true,
                    enabled = true,
                    onClick = {
                        val installedPackageOrder = apps.map { it.packageName }
                        onSave(
                            installedPackageOrder.filter { it in selected } +
                                selected.filterNot { it in installedPackageOrder }.sorted(),
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun SplitTunnelAppRow(
    app: SplitTunnelAppInfo,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    val context = LocalContext.current
    val haptic = rememberHapticFeedback()
    val appToggleDescription = stringResource(R.string.cd_split_tunnel_app_toggle, app.label)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(11.dp))
            .semantics {
                contentDescription = appToggleDescription
            }
            .clickable {
                haptic.performLight()
                onToggle()
            }
            .padding(vertical = 9.dp, horizontal = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = {
                haptic.performLight()
                onToggle()
            },
            colors = CheckboxDefaults.colors(
                checkedColor = WhiteZiaPalette.Accent,
                uncheckedColor = WhiteZiaPalette.ControlBorder,
                checkmarkColor = WhiteZiaPalette.OnAccent,
            ),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 13.sp,
                    color = WhiteZiaPalette.Ink,
                    fontWeight = FontWeight.Medium,
                ),
            )
            Text(
                text = app.packageName,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 10.sp,
                    color = WhiteZiaPalette.Muted,
                ),
            )
        }
    }
}

internal data class SplitTunnelAppInfo(
    val packageName: String,
    val label: String,
)

internal fun loadSplitTunnelAppOptions(context: Context): List<SplitTunnelAppInfo> {
    val packageManager = context.packageManager
    val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_LAUNCHER)
    }
    return packageManager.queryIntentActivities(launcherIntent, 0)
        .asSequence()
        .mapNotNull { resolveInfo ->
            val appPackage = resolveInfo.activityInfo?.packageName ?: return@mapNotNull null
            if (appPackage == context.packageName) {
                return@mapNotNull null
            }
            val label = resolveInfo.loadLabel(packageManager)
                .toString()
                .trim()
                .takeIf(String::isNotEmpty)
                ?: appPackage
            SplitTunnelAppInfo(
                packageName = appPackage,
                label = label,
            )
        }
        .distinctBy { it.packageName }
        .sortedWith(
            compareBy<SplitTunnelAppInfo> { it.label.lowercase(Locale.US) }
                .thenBy { it.packageName },
        )
        .toList()
}

private fun selectedSplitTunnelLabels(
    packageNames: List<String>,
    apps: List<SplitTunnelAppInfo>,
): List<String> {
    val labelsByPackage = apps.associate { it.packageName to it.label }
    return packageNames.map { packageName ->
        labelsByPackage[packageName] ?: packageName
    }
}

private fun splitTunnelAppsSummary(
    mode: String,
    appLabels: List<String>,
    allAppsLabel: String,
    noAppsLabel: String,
): String {
    if (mode == WhiteZiaOptions.SplitTunnelModeOff) {
        return allAppsLabel
    }
    if (appLabels.isEmpty()) {
        return noAppsLabel
    }
    return compactAppLabelSummary(appLabels, noAppsLabel)
}

internal fun splitTunnelConnectionSummary(
    mode: String,
    packageNames: List<String>,
    labelsByPackage: Map<String, String>,
    allAppsLabel: String,
    noAppsLabel: String,
    onlyPrefix: String,
    bypassPrefix: String,
): String {
    val labels = packageNames.map { packageName ->
        labelsByPackage[packageName] ?: packageName
    }
    return when (mode) {
        WhiteZiaOptions.SplitTunnelModeInclude -> {
            if (labels.isEmpty()) allAppsLabel else "$onlyPrefix ${compactAppLabelSummary(labels, noAppsLabel)}"
        }
        WhiteZiaOptions.SplitTunnelModeExclude -> {
            if (labels.isEmpty()) allAppsLabel else "$bypassPrefix ${compactAppLabelSummary(labels, noAppsLabel)}"
        }
        else -> allAppsLabel
    }
}

// Haptic Feedback Utilities

/**
 * Remembers the haptic feedback instance from the current view.
 */
private fun compactAppLabelSummary(appLabels: List<String>, noAppsLabel: String): String {
    return when (appLabels.size) {
        0 -> noAppsLabel
        1 -> appLabels.first()
        2 -> appLabels.joinToString(", ")
        else -> "${appLabels.take(2).joinToString(", ")} +${appLabels.size - 2}"
    }
}
