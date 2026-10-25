package shop.whitezia.client.ui

import android.net.Uri

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import shop.whitezia.client.R
import shop.whitezia.client.model.Choice
import shop.whitezia.client.model.WhiteZiaScanDefaults
import shop.whitezia.client.model.WhiteZiaScanState
import shop.whitezia.client.model.WhiteZiaScanStatus
import shop.whitezia.client.model.WhiteZiaSettings
import shop.whitezia.client.model.WhiteZiaUiState
import shop.whitezia.client.model.normalizedConnectionProfiles
import shop.whitezia.client.model.saveResolverProfileAs
import kotlin.math.roundToInt

private const val ScanWorkerMin = 1
private const val ScanWorkerMax = 32
@Composable
internal fun ScanTabContent(
    uiState: WhiteZiaUiState,
    onScanFileSelected: (Uri) -> Unit,
    onScanDefaultListSelected: () -> Unit,
    onScanStartClick: () -> Unit,
    onScanConnectionProfileChange: (String) -> Unit,
    onScanWorkerCountChange: (String) -> Unit,
    onScanStopClick: () -> Unit,
    onScanResumeClick: () -> Unit,
    onSettingsChange: (WhiteZiaSettings) -> Unit,
) {
    val scanState = uiState.scanState
    val workerCount = (uiState.scanWorkerCount.toIntOrNull() ?: WhiteZiaScanDefaults.DefaultWorkerCount)
        .coerceIn(ScanWorkerMin, ScanWorkerMax)
    val connectionProfiles = remember(uiState.settings) {
        uiState.settings.normalizedConnectionProfiles()
    }
    val selectedScanConnectionProfile = connectionProfiles
        .firstOrNull { it.id == uiState.scanConnectionProfileId }
        ?: connectionProfiles.first()
    val connectionFallback = WhiteZiaL10n.genericConnectionFallback
    val scanConnectionProfileOptions = remember(connectionProfiles, connectionFallback) {
        connectionProfiles.map { profile ->
            Choice(
                value = profile.id,
                label = profile.name.ifBlank { connectionFallback },
            )
        }
    }
    val selectedScanProfileNeedsServer = selectedScanConnectionProfile.customServerDomain.isBlank() ||
        selectedScanConnectionProfile.customServerEncryptionKey.isBlank()
    var showScanInfoNotice by rememberSaveable { mutableStateOf(true) }
    var showSaveAsDialog by rememberSaveable(scanState.sessionId) { mutableStateOf(false) }
    val saveAsResolverEntries = remember(scanState.validResolverEntries) {
        scanState.validResolverEntries
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()
    }
    val canSaveAsProfile = saveAsResolverEntries.isNotEmpty()
    val scanFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let(onScanFileSelected)
    }

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
                .padding(horizontal = 20.dp)
                .padding(top = 36.dp),
            verticalArrangement = Arrangement.spacedBy(WhiteZiaSpacing.md),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CompactActionButton(
                    modifier = Modifier.weight(1f),
                    label = WhiteZiaL10n.scanDefaultList,
                    emphasized = !scanState.isRunning,
                    enabled = !scanState.isRunning,
                    onClick = onScanDefaultListSelected,
                )
                CompactActionButton(
                    modifier = Modifier.weight(1f),
                    label = WhiteZiaL10n.scanSelectFile,
                    emphasized = false,
                    enabled = !scanState.isRunning,
                    onClick = {
                        scanFileLauncher.launch(ResolverImportMimeTypes)
                    },
                )
            }
            ScanWorkerSlider(
                workerCount = workerCount,
                enabled = !scanState.isRunning,
                onWorkerCountChange = { onScanWorkerCountChange(it.toString()) },
            )
            ScanNote(
                text = WhiteZiaL10n.scanWorkerWarning,
            )
            WhiteZiaDropdownField(
                label = WhiteZiaL10n.scanProfileLabel,
                value = selectedScanConnectionProfile.id,
                options = scanConnectionProfileOptions,
                onValueChange = onScanConnectionProfileChange,
                enabled = !scanState.isRunning,
            )
            if (selectedScanProfileNeedsServer) {
                val scanProfileFallback = WhiteZiaL10n.scanProfileFallback
                val needsServerSuffix = WhiteZiaL10n.scanProfileNeedsServer
                ScanWarningBanner(
                    text = "${selectedScanConnectionProfile.name.ifBlank { scanProfileFallback }} $needsServerSuffix",
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CompactActionButton(
                    modifier = Modifier.weight(1f),
                    label = WhiteZiaL10n.scanBtnStart,
                    emphasized = true,
                    tone = CompactActionTone.Success,
                    enabled = scanCanStart(scanState) && !selectedScanProfileNeedsServer,
                    onClick = onScanStartClick,
                )
                CompactActionButton(
                    modifier = Modifier.weight(1f),
                    label = WhiteZiaL10n.scanBtnStop,
                    emphasized = false,
                    tone = CompactActionTone.Danger,
                    enabled = scanState.isRunning,
                    onClick = onScanStopClick,
                )
            }
            if (showScanInfoNotice) {
                ScanInfoNotice(
                    text = WhiteZiaL10n.scanAutoSave,
                    onDismiss = { showScanInfoNotice = false },
                )
            }

            SectionCard(
                title = WhiteZiaL10n.scanStatusTitle,
                expanded = true,
                icon = Icons.Filled.DataUsage,
                iconContentDescription = stringResource(R.string.cd_icon_data_usage),
                collapsible = false,
                onToggle = {},
            ) {
                CompactMetricRow(
                    metrics = listOf(
                        CompactMetric(
                            icon = Icons.Filled.DataUsage,
                            iconContentDescription = stringResource(R.string.cd_icon_data_usage),
                            label = WhiteZiaL10n.scanLabelTotal,
                            value = scanState.totalResolvers.toString(),
                        ),
                        CompactMetric(
                            icon = Icons.Rounded.Check,
                            iconContentDescription = stringResource(R.string.cd_icon_success),
                            label = WhiteZiaL10n.scanLabelValid,
                            value = scanState.validResolvers.toString(),
                        ),
                        CompactMetric(
                            icon = Icons.Rounded.Close,
                            iconContentDescription = stringResource(R.string.cd_icon_error),
                            label = WhiteZiaL10n.scanLabelRejected,
                            value = scanState.rejectedResolvers.toString(),
                        ),
                    ),
                )
                Spacer(modifier = Modifier.height(WhiteZiaSpacing.inputSpacing))
                ScanProgressBar(fraction = scanState.fraction)
                Spacer(modifier = Modifier.height(WhiteZiaSpacing.inputSpacing))
                InfoRow(label = WhiteZiaL10n.scanLabelStatus, value = scanStatusLabel(scanState.status))
                InfoRow(label = WhiteZiaL10n.scanLabelSource, value = scanState.sourceName.ifBlank { WhiteZiaL10n.scanNoFileSelected })
                InfoRow(label = WhiteZiaL10n.scanLabelWorkers, value = scanState.workerCount.toString())
                InfoRow(label = WhiteZiaL10n.scanLabelProgress, value = "${scanState.completedResolvers}/${scanState.totalResolvers}")
                if (scanState.message.isNotBlank()) {
                    InfoRow(label = WhiteZiaL10n.scanMessageLabel, value = scanState.message, multilineValue = true)
                }
                if (scanState.workerFailures.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(WhiteZiaSpacing.inputSpacing))
                    ScanWarningBanner(text = scanState.workerFailures.first())
                }
                Spacer(modifier = Modifier.height(WhiteZiaSpacing.inputSpacing))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CompactActionButton(
                        modifier = Modifier.weight(1f),
                        label = WhiteZiaL10n.scanBtnSaveAs,
                        emphasized = false,
                        enabled = canSaveAsProfile,
                        onClick = {
                            showSaveAsDialog = true
                        },
                    )
                    CompactActionButton(
                        modifier = Modifier.weight(1f),
                        label = WhiteZiaL10n.scanBtnResume,
                        emphasized = true,
                        tone = CompactActionTone.Accent,
                        enabled = scanCanResume(scanState),
                        onClick = onScanResumeClick,
                    )
                }
            }
            Spacer(modifier = Modifier.height(WhiteZiaSpacing.sectionSpacing))
        }
    }

    if (showSaveAsDialog) {
        val scanResultsName = WhiteZiaL10n.scanResultsTitle
        val scanResultsSuffix = WhiteZiaL10n.scanResultsSuffix
        ScanSaveAsProfileDialog(
            sourceName = scanState.sourceName,
            resolverCount = saveAsResolverEntries.size,
            initialName = scanResultProfileInitialName(scanState.sourceName, scanResultsName, scanResultsSuffix),
            onDismiss = { showSaveAsDialog = false },
            onSave = { name ->
                onSettingsChange(
                    uiState.settings.saveResolverProfileAs(
                        name = name,
                        resolverText = saveAsResolverEntries.joinToString(separator = "\n"),
                    ),
                )
                showSaveAsDialog = false
            },
        )
    }
}

private fun scanCanResume(scanState: WhiteZiaScanState): Boolean {
    return !scanState.isRunning &&
        (scanState.status == WhiteZiaScanStatus.Stopped || scanState.status == WhiteZiaScanStatus.Failed) &&
        scanState.completedResolvers < scanState.totalResolvers
}

private fun scanCanStart(scanState: WhiteZiaScanState): Boolean {
    return !scanState.isRunning &&
        scanState.status == WhiteZiaScanStatus.Ready &&
        scanState.sessionId.isNotBlank() &&
        scanState.completedResolvers < scanState.totalResolvers
}

private fun scanResultProfileInitialName(
    sourceName: String,
    defaultName: String,
    suffix: String,
): String {
    val trimmed = sourceName.trim()
    if (trimmed.isBlank()) {
        return defaultName
    }
    val withoutExtension = trimmed.substringBeforeLast('.').trim().ifBlank { trimmed }
    return "$withoutExtension $suffix"
}

@Composable
private fun ScanInfoNotice(
    text: String,
    onDismiss: () -> Unit,
) {
    val haptic = rememberHapticFeedback()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(WhiteZiaPalette.AccentSurface)
            .border(1.5.dp, WhiteZiaPalette.Accent.copy(alpha = 0.22f), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(WhiteZiaPalette.Accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = stringResource(R.string.cd_scan_autosave_enabled),
                tint = WhiteZiaPalette.AccentText,
                modifier = Modifier.size(16.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = WhiteZiaL10n.scanAutoSaveTitle,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 9.sp,
                    color = WhiteZiaPalette.AccentText,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.9.sp,
                ),
            )
            Spacer(modifier = Modifier.height(WhiteZiaSpacing.xs))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                    color = WhiteZiaPalette.AccentText,
                    fontWeight = FontWeight.Medium,
                ),
            )
        }
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .clickable {
                    haptic.performLight()
                    onDismiss()
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = WhiteZiaL10n.cdDismissScannerInfo,
                tint = WhiteZiaPalette.AccentText,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun ScanSaveAsProfileDialog(
    sourceName: String,
    resolverCount: Int,
    initialName: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var name by remember(initialName, sourceName) { mutableStateOf(initialName) }
    val canSave = name.trim().isNotEmpty() && resolverCount > 0
    val scanLabel = sourceName.ifBlank { WhiteZiaL10n.scanCurrentScan }
    val saveBodyTemplate = WhiteZiaL10n.scanSaveBodyTemplate

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
                text = WhiteZiaL10n.scanSaveAsTitle,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    color = WhiteZiaPalette.Ink,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.1.sp,
                ),
            )
            Spacer(modifier = Modifier.height(WhiteZiaSpacing.md))
            Text(
                text = "$resolverCount · $scanLabel · $saveBodyTemplate",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    color = WhiteZiaPalette.Muted,
                ),
            )
            Spacer(modifier = Modifier.height(WhiteZiaSpacing.md))
            WhiteZiaTextField(
                label = WhiteZiaL10n.scanSaveAsName,
                value = name,
                onValueChange = { name = it },
                placeholder = WhiteZiaL10n.scanResultsTitle,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
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
                    label = WhiteZiaL10n.btnSave,
                    emphasized = true,
                    enabled = canSave,
                    onClick = {
                        onSave(name.trim())
                    },
                )
            }
        }
    }
}

@Composable
private fun ScanWorkerSlider(
    workerCount: Int,
    enabled: Boolean,
    onWorkerCountChange: (Int) -> Unit,
) {
    val context = LocalContext.current
    val workerCountDescription = stringResource(R.string.cd_worker_count_slider, workerCount)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FieldLabel(WhiteZiaL10n.scanFieldWorkers)
            Text(
                text = workerCount.toString(),
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = WhiteZiaPalette.Ink,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                ),
            )
        }
        Slider(
            value = workerCount.toFloat(),
            onValueChange = { value ->
                onWorkerCountChange(value.roundToInt().coerceIn(ScanWorkerMin, ScanWorkerMax))
            },
            enabled = enabled,
            valueRange = ScanWorkerMin.toFloat()..ScanWorkerMax.toFloat(),
            steps = ScanWorkerMax - ScanWorkerMin - 1,
            modifier = Modifier.semantics {
                contentDescription = workerCountDescription
            },
            colors = SliderDefaults.colors(
                thumbColor = WhiteZiaPalette.Accent,
                activeTrackColor = WhiteZiaPalette.Accent,
                inactiveTrackColor = WhiteZiaPalette.ControlBorder,
                disabledThumbColor = WhiteZiaPalette.Disabled,
                disabledActiveTrackColor = WhiteZiaPalette.ControlBorder,
                disabledInactiveTrackColor = WhiteZiaPalette.Input,
            ),
        )
    }
}

@Composable
internal fun MtuParallelismSlider(
    parallelism: Int,
    enabled: Boolean,
    onParallelismChange: (Int) -> Unit,
) {
    val context = LocalContext.current
    var sliderValue by remember(parallelism) {
        mutableFloatStateOf(parallelism.toMtuParallelismSliderValue().toFloat())
    }
    val displayedParallelism = sliderValue.toMtuParallelismSliderValue()
    val parallelismDescription = stringResource(R.string.cd_mtu_parallelism_slider, displayedParallelism)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FieldLabel(WhiteZiaL10n.settingResolverParallel)
            Text(
                text = displayedParallelism.toString(),
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = WhiteZiaPalette.Ink,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                ),
            )
        }
        Slider(
            value = sliderValue,
            onValueChange = { value ->
                sliderValue = value.toMtuParallelismSliderValue().toFloat()
            },
            onValueChangeFinished = {
                val committedParallelism = sliderValue.toMtuParallelismSliderValue()
                if (committedParallelism != parallelism) {
                    onParallelismChange(committedParallelism)
                }
            },
            enabled = enabled,
            valueRange = MtuParallelismMin.toFloat()..MtuParallelismMax.toFloat(),
            steps = ((MtuParallelismMax - MtuParallelismMin) / MtuParallelismStep) - 1,
            modifier = Modifier.semantics {
                contentDescription = parallelismDescription
            },
            colors = SliderDefaults.colors(
                thumbColor = WhiteZiaPalette.Accent,
                activeTrackColor = WhiteZiaPalette.Accent,
                inactiveTrackColor = WhiteZiaPalette.ControlBorder,
                disabledThumbColor = WhiteZiaPalette.Disabled,
                disabledActiveTrackColor = WhiteZiaPalette.ControlBorder,
                disabledInactiveTrackColor = WhiteZiaPalette.Input,
            ),
        )
        Text(
            text = WhiteZiaL10n.settingResolverParallelNote,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = WhiteZiaPalette.Muted,
            ),
        )
    }
}

internal fun String.toMtuParallelismSliderValue(): Int {
    return toIntOrNull()?.toMtuParallelismSliderValue() ?: MtuParallelismDefault
}

internal fun Float.toMtuParallelismSliderValue(): Int {
    return roundToInt().toMtuParallelismSliderValue()
}

internal fun Int.toMtuParallelismSliderValue(): Int {
    val rounded = (this.toFloat() / MtuParallelismStep).roundToInt() * MtuParallelismStep
    return rounded.coerceIn(MtuParallelismMin, MtuParallelismMax)
}

@Composable
private fun ScanProgressBar(fraction: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(WhiteZiaPalette.Input),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(10.dp)
                .background(WhiteZiaPalette.Accent),
        )
    }
}

@Composable
private fun ScanNote(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(WhiteZiaPalette.Input)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.WarningAmber,
            contentDescription = stringResource(R.string.cd_icon_warning),
            tint = WhiteZiaPalette.Muted,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall.copy(
                color = WhiteZiaPalette.Muted,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            ),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ScanWarningBanner(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(WhiteZiaPalette.WarningSurface)
            .border(1.5.dp, WhiteZiaPalette.Warning.copy(alpha = 0.26f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.WarningAmber,
            contentDescription = stringResource(R.string.cd_icon_warning),
            tint = WhiteZiaPalette.WarningText,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 12.sp,
                color = WhiteZiaPalette.WarningText,
            ),
        )
    }
}

@Composable
private fun scanStatusLabel(status: String): String {
    return when (status) {
        WhiteZiaScanStatus.Ready -> WhiteZiaL10n.scanStatusReady
        WhiteZiaScanStatus.Starting -> WhiteZiaL10n.scanStatusStarting
        WhiteZiaScanStatus.Running -> WhiteZiaL10n.scanStatusRunning
        WhiteZiaScanStatus.Completed -> WhiteZiaL10n.scanStatusCompleted
        WhiteZiaScanStatus.Failed -> WhiteZiaL10n.scanStatusFailed
        WhiteZiaScanStatus.Stopped -> WhiteZiaL10n.scanStatusStopped
        else -> WhiteZiaL10n.scanStatusIdle
    }
}
