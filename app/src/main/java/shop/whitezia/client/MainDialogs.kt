package shop.whitezia.client

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlinx.coroutines.delay
import shop.whitezia.client.model.WhiteZiaOptions
import shop.whitezia.client.model.WhiteZiaSettings
import shop.whitezia.client.ui.WhiteZiaBackground
import shop.whitezia.client.ui.WhiteZiaPanel
import shop.whitezia.client.ui.WhiteZiaTextMuted
import shop.whitezia.client.ui.connect.WhiteZiaLogoTextStyle

private data class SplitTunnelAppInfo(
    val packageName: String,
    val label: String,
)

@Composable
internal fun WhiteZiaLogDialog(
    logText: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val logScrollState = rememberScrollState()
    LaunchedEffect(logText) {
        delay(50)
        logScrollState.scrollTo(logScrollState.maxValue)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = WhiteZiaPanel,
        titleContentColor = Color.White,
        textContentColor = WhiteZiaTextMuted,
        title = {
            Text(
                text = "Логи",
                style = WhiteZiaLogoTextStyle(),
                color = Color.White.copy(alpha = 0.92f),
            )
        },
        text = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 260.dp, max = 520.dp),
                color = WhiteZiaBackground,
                tonalElevation = 0.dp,
            ) {
                SelectionContainer {
                    Text(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                            .verticalScroll(logScrollState),
                        text = logText.ifBlank { "Лог пуст" },
                        color = Color.White.copy(alpha = 0.78f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = logText.isNotBlank(),
                onClick = { copyTextToClipboard(context, "WhiteZia logs", logText) },
            ) {
                Text("Копировать")
            }
            TextButton(onClick = onDismiss) { Text("Закрыть") }
        },
    )
}

@Composable
internal fun SplitTunnelDialog(
    settings: WhiteZiaSettings,
    onDismiss: () -> Unit,
    onSettingsChange: (WhiteZiaSettings) -> Unit,
) {
    val context = LocalContext.current
    val apps = remember { loadSplitTunnelAppOptions(context) }
    var selectedMode by remember(settings.splitTunnelMode) { mutableStateOf(settings.splitTunnelMode) }
    var selectedPackages by remember(settings.splitTunnelPackages) {
        mutableStateOf(settings.splitTunnelPackages.toSet())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Split tunnel") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                SplitTunnelModeOptions(selectedMode, onSelectedModeChange = { selectedMode = it })
                if (selectedMode != WhiteZiaOptions.SplitTunnelModeOff) {
                    Spacer(modifier = Modifier.height(12.dp))
                    apps.forEach { app ->
                        val checked = app.packageName in selectedPackages
                        val toggle = {
                            selectedPackages = if (checked) {
                                selectedPackages - app.packageName
                            } else {
                                selectedPackages + app.packageName
                            }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClick = toggle)
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = checked, onCheckedChange = { toggle() })
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    text = app.packageName,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val order = apps.map { it.packageName }
                    val packages = if (selectedMode == WhiteZiaOptions.SplitTunnelModeOff) {
                        emptyList()
                    } else {
                        order.filter { it in selectedPackages } +
                            selectedPackages.filterNot { it in order }.sorted()
                    }
                    onSettingsChange(
                        settings.copy(
                            splitTunnelMode = selectedMode,
                            splitTunnelPackages = packages,
                        ),
                    )
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun SplitTunnelModeOptions(
    selectedMode: String,
    onSelectedModeChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SplitTunnelModeOption("All apps", selectedMode == WhiteZiaOptions.SplitTunnelModeOff) {
            onSelectedModeChange(WhiteZiaOptions.SplitTunnelModeOff)
        }
        SplitTunnelModeOption("Only selected apps", selectedMode == WhiteZiaOptions.SplitTunnelModeInclude) {
            onSelectedModeChange(WhiteZiaOptions.SplitTunnelModeInclude)
        }
        SplitTunnelModeOption("Bypass selected apps", selectedMode == WhiteZiaOptions.SplitTunnelModeExclude) {
            onSelectedModeChange(WhiteZiaOptions.SplitTunnelModeExclude)
        }
    }
}

@Composable
private fun SplitTunnelModeOption(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        modifier = Modifier.fillMaxWidth(),
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
    )
}

private fun copyTextToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
}

@Suppress("DEPRECATION")
private fun loadSplitTunnelAppOptions(context: Context): List<SplitTunnelAppInfo> {
    val packageManager = context.packageManager
    val launcherIntent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
    return packageManager.queryIntentActivities(launcherIntent, 0)
        .asSequence()
        .mapNotNull { resolveInfo ->
            val appPackage = resolveInfo.activityInfo?.packageName ?: return@mapNotNull null
            if (appPackage == context.packageName) return@mapNotNull null
            val label = resolveInfo.loadLabel(packageManager)
                .toString()
                .trim()
                .takeIf(String::isNotEmpty)
                ?: appPackage
            SplitTunnelAppInfo(appPackage, label)
        }
        .distinctBy { it.packageName }
        .sortedWith(compareBy<SplitTunnelAppInfo> { it.label.lowercase(Locale.US) }.thenBy { it.packageName })
        .toList()
}
