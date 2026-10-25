package shop.whitezia.client.ui

import shop.whitezia.client.diagnostics.redactDiagnostics

import android.content.Context
import android.os.Build

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import shop.whitezia.client.model.WhiteZiaOptions
import shop.whitezia.client.model.WhiteZiaSettings
import shop.whitezia.client.model.WhiteZiaUiState
import shop.whitezia.client.model.resolve
import shop.whitezia.client.model.runtimeConnectionSettings
import shop.whitezia.client.model.selectedConnectionProfile
import shop.whitezia.client.model.selectedResolverProfile

@Composable
internal fun LogsTabContent(
    uiState: WhiteZiaUiState,
    onSettingsChange: (WhiteZiaSettings) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HeaderCard(
            themeMode = uiState.settings.themeMode,
            onThemeModeChange = { onSettingsChange(uiState.settings.copy(themeMode = it)) },
            languageCode = uiState.settings.languageCode,
            onLanguageCodeChange = { onSettingsChange(uiState.settings.copy(languageCode = it)) },
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp)
                .padding(horizontal = 20.dp),
        ) {
            ConnectionLogsBlock(uiState = uiState, expanded = true)
            Spacer(modifier = Modifier.height(WhiteZiaSpacing.sectionSpacing))
            FooterLink()
        }
    }
}

@Composable
private fun ConnectionLogsBlock(
    uiState: WhiteZiaUiState,
    expanded: Boolean = false,
) {
    val logs = uiState.connectionLogs
    val visibleLogs = if (expanded) logs else logs.takeLast(10)
    val context = LocalContext.current
    val logsClipboardLabel = WhiteZiaL10n.whiteZiaLogsLabel
    val diagnosticsClipboardLabel = WhiteZiaL10n.whiteZiaDiagnosticsLabel
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = WhiteZiaL10n.logsInlineTitle,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 10.sp,
                    color = WhiteZiaPalette.SectionTitle,
                    letterSpacing = 1.5.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LogActionButton(
                    label = WhiteZiaL10n.btnCopy,
                    onClick = {
                        copyTextToClipboard(
                            context = context,
                            label = logsClipboardLabel,
                            text = uiState.settings.redactDiagnostics(logs.joinToString(separator = "\n")),
                            sensitive = false,
                        )
                    },
                )
                LogActionButton(
                    label = WhiteZiaL10n.logsDiagnostics,
                    onClick = {
                        copyTextToClipboard(
                            context = context,
                            label = diagnosticsClipboardLabel,
                            text = buildDiagnosticsText(context, uiState),
                            sensitive = false,
                        )
                    },
                )
            }
        }
        Spacer(modifier = Modifier.height(WhiteZiaSpacing.sm))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(WhiteZiaPalette.Surface)
                .border(1.5.dp, WhiteZiaPalette.Border, RoundedCornerShape(12.dp)),
        ) {
            SelectionContainer {
                Column(modifier = Modifier.fillMaxWidth()) {
                    visibleLogs.forEachIndexed { index, logLine ->
                        Text(
                            text = logLine,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (index % 2 == 0) WhiteZiaPalette.SurfaceAlt else WhiteZiaPalette.Surface)
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 10.sp,
                                lineHeight = 15.sp,
                                color = WhiteZiaPalette.Description,
                            ),
                        )
                        if (index != visibleLogs.lastIndex) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(WhiteZiaPalette.Divider),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LogActionButton(
    label: String,
    onClick: () -> Unit,
) {
    val haptic = rememberHapticFeedback()

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(WhiteZiaPalette.Surface)
            .border(1.5.dp, WhiteZiaPalette.Border, RoundedCornerShape(8.dp))
            .semantics { contentDescription = label }
            .clickable {
                haptic.performLight()
                onClick()
            }
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 9.sp,
                color = WhiteZiaPalette.AccentText,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.sp,
            ),
        )
    }
}

private fun buildDiagnosticsText(context: Context, uiState: WhiteZiaUiState): String =
    buildConnectionDiagnosticsText(appVersionName(context), Build.VERSION.RELEASE, Build.VERSION.SDK_INT, uiState)
