package shop.whitezia.client.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.view.HapticFeedbackConstants

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.NetworkPing
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.zIndex
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.intl.Locale as ComposeLocale
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
import androidx.compose.ui.window.Dialog
import shop.whitezia.client.QrScannerActivity
import shop.whitezia.client.R
import shop.whitezia.client.model.AdvancedSettingsProfile
import shop.whitezia.client.model.AutoTuneTrialResult
import shop.whitezia.client.model.Choice
import shop.whitezia.client.model.ConnectionProfile
import shop.whitezia.client.model.ConnectionProgressState
import shop.whitezia.client.model.ConnectionStats
import shop.whitezia.client.model.ConnectionStatus
import shop.whitezia.client.model.ConnectionVerificationState
import shop.whitezia.client.model.ConnectionVerificationStatus
import shop.whitezia.client.model.ResolverProfile
import shop.whitezia.client.model.ResolverRuntimeState
import shop.whitezia.client.model.ServerTestResult
import shop.whitezia.client.model.ServerTestState
import shop.whitezia.client.model.ServerTestStatus
import shop.whitezia.client.model.WhiteZiaOptions
import shop.whitezia.client.model.WhiteZiaScanDefaults
import shop.whitezia.client.model.WhiteZiaScanState
import shop.whitezia.client.model.WhiteZiaScanStatus
import shop.whitezia.client.model.WhiteZiaSettings
import shop.whitezia.client.model.WhiteZiaUiState
import shop.whitezia.client.model.applyAdvancedProfile
import shop.whitezia.client.model.applyAutoTunePreset
import shop.whitezia.client.model.applyResolverProfileToSelectedConnection
import shop.whitezia.client.model.deleteConnectionProfile
import shop.whitezia.client.model.deleteDuplicateConnectionProfiles
import shop.whitezia.client.model.deleteAdvancedProfile
import shop.whitezia.client.model.deleteResolverProfile
import shop.whitezia.client.model.duplicateConnectionProfileCount
import shop.whitezia.client.model.exportAllResolverProfilesText
import shop.whitezia.client.model.exportAllStormDnsProfileLinks
import shop.whitezia.client.model.exportStormDnsProfileLink
import shop.whitezia.client.model.importStormDnsProfileLinks
import shop.whitezia.client.model.importAdvancedSettingsProfileFromToml
import shop.whitezia.client.model.matchesAdvancedProfile
import shop.whitezia.client.model.moveConnectionProfileToIndex
import shop.whitezia.client.model.moveAdvancedProfileToIndex
import shop.whitezia.client.model.moveResolverProfileToIndex
import shop.whitezia.client.model.normalizedAdvancedProfiles
import shop.whitezia.client.model.normalizedConnectionProfiles
import shop.whitezia.client.model.normalizedResolverProfiles
import shop.whitezia.client.model.resolve
import shop.whitezia.client.model.runtimeConnectionSettings
import shop.whitezia.client.model.saveResolverProfileAs
import shop.whitezia.client.model.selectAdvancedProfile
import shop.whitezia.client.model.selectConnectionProfile
import shop.whitezia.client.model.selectedAdvancedProfile
import shop.whitezia.client.model.selectedConnectionProfile
import shop.whitezia.client.model.selectedResolverProfile
import shop.whitezia.client.model.updateManualResolverText
import shop.whitezia.client.model.upsertConnectionProfile
import shop.whitezia.client.model.upsertAdvancedProfile
import shop.whitezia.client.model.upsertResolverProfile
import shop.whitezia.client.model.validateResolverText
import shop.whitezia.client.model.WhiteZiaAutoTunePresets
import shop.whitezia.client.model.WhiteZiaParallelTest
import shop.whitezia.client.model.syncSelectedConnectionProfileFields
import shop.whitezia.client.storm.StormDnsConfigRenderer
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.core.content.FileProvider

@Composable
fun WhiteZiaScreen(
    uiState: WhiteZiaUiState,
    onBatteryOptimizationClick: () -> Unit,
    onNotificationPermissionClick: () -> Unit,
    onConnectClick: () -> Unit,
    onScanFileSelected: (Uri) -> Unit,
    onScanDefaultListSelected: () -> Unit,
    onScanStartClick: () -> Unit,
    onScanConnectionProfileChange: (String) -> Unit,
    onScanWorkerCountChange: (String) -> Unit,
    onScanStopClick: () -> Unit,
    onScanResumeClick: () -> Unit,
    onServerTestClick: (String?) -> Unit,
    onSettingsChange: (WhiteZiaSettings) -> Unit,
) {
    var selectedTab by rememberSaveable { mutableStateOf(WhiteZiaTab.CONNECT) }
    var profileCreateRequest by rememberSaveable { mutableStateOf<ProfileCreateRequest?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .whiteZiaPageBackground(),
    ) {
        Box(modifier = Modifier.weight(1f)) {
            when (selectedTab) {
                WhiteZiaTab.PROFILES -> ProfilesTabContent(
                    uiState = uiState,
                    createRequest = profileCreateRequest,
                    onCreateRequestConsumed = {
                        profileCreateRequest = null
                    },
                    onServerTestClick = onServerTestClick,
                    onSettingsChange = onSettingsChange,
                )
                WhiteZiaTab.CONNECT -> ConnectTabContent(
                    uiState = uiState,
                    onBatteryOptimizationClick = onBatteryOptimizationClick,
                    onNotificationPermissionClick = onNotificationPermissionClick,
                    onConnectClick = onConnectClick,
                    onAddConnectionClick = {
                        profileCreateRequest = ProfileCreateRequest.CONNECTION
                        selectedTab = WhiteZiaTab.PROFILES
                    },
                    onAddResolverProfileClick = {
                        profileCreateRequest = ProfileCreateRequest.RESOLVER
                        selectedTab = WhiteZiaTab.PROFILES
                    },
                    onSettingsChange = onSettingsChange,
                )
                WhiteZiaTab.SCAN -> ScanTabContent(
                    uiState = uiState,
                    onScanFileSelected = onScanFileSelected,
                    onScanDefaultListSelected = onScanDefaultListSelected,
                    onScanStartClick = onScanStartClick,
                    onScanConnectionProfileChange = onScanConnectionProfileChange,
                    onScanWorkerCountChange = onScanWorkerCountChange,
                    onScanStopClick = onScanStopClick,
                    onScanResumeClick = onScanResumeClick,
                    onSettingsChange = onSettingsChange,
                )
                WhiteZiaTab.LOGS -> LogsTabContent(
                    uiState = uiState,
                    onSettingsChange = onSettingsChange,
                )
            }
        }
        BottomNavigationBar(
            selectedTab = selectedTab,
            onTabSelected = { selectedTab = it },
        )
    }
}

private enum class WhiteZiaTab(
    val label: String,
    val icon: ImageVector,
) {
    PROFILES("Profiles", Icons.Filled.Apps),
    CONNECT("Connect", Icons.Rounded.PowerSettingsNew),
    SCAN("Scan", Icons.Rounded.Search),
    LOGS("Logs", Icons.Rounded.Link),
}

private const val ScanWorkerMin = 1
private const val ScanWorkerMax = 32
private const val MtuParallelismMin = 50
private const val MtuParallelismMax = 1000
private const val MtuParallelismStep = 50
private const val MtuParallelismDefault = 100

private enum class CompactActionTone {
    Default,
    Accent,
    Success,
    Danger,
}

private enum class ProfileCreateRequest {
    CONNECTION,
    RESOLVER,
}

@Composable
private fun Modifier.whiteZiaPageBackground(): Modifier {
    val backgroundColor = WhiteZiaPalette.Background
    val accentColor = WhiteZiaPalette.Accent
    return drawBehind {
        drawRect(color = backgroundColor)
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    accentColor.copy(alpha = 0.30f),
                    Color(0xFF245D72).copy(alpha = 0.17f),
                    Color(0xFF111420).copy(alpha = 0.08f),
                    Color.Transparent,
                ),
                center = Offset(x = size.width, y = 0f),
                radius = size.maxDimension * 1.08f,
            ),
        )
    }
}

@Composable
private fun ConnectTabContent(
    uiState: WhiteZiaUiState,
    onBatteryOptimizationClick: () -> Unit,
    onNotificationPermissionClick: () -> Unit,
    onConnectClick: () -> Unit,
    onAddConnectionClick: () -> Unit,
    onAddResolverProfileClick: () -> Unit,
    onSettingsChange: (WhiteZiaSettings) -> Unit,
) {
    val settings = uiState.settings
    var showResolverRequiredMessage by rememberSaveable { mutableStateOf(false) }
    var selectorSheetType by rememberSaveable { mutableStateOf<HomeSelectorType?>(null) }
    var selectorSheetVisible by rememberSaveable { mutableStateOf(false) }
    var showConnectionTomlDialog by rememberSaveable { mutableStateOf(false) }
    var showConnectionEditDialog by remember { mutableStateOf(false) }
    var showResolverEditDialog by remember { mutableStateOf(false) }
    var showAdvancedEditDialog by remember { mutableStateOf(false) }
    var autoTuneSaveResult by remember { mutableStateOf<AutoTuneTrialResult?>(null) }
    var parallelTestSelectionExpanded by rememberSaveable { mutableStateOf(true) }
    val runtimeSettings = remember(settings) { settings.runtimeConnectionSettings() }
    val resolvedSettings = remember(runtimeSettings) { runtimeSettings.resolve() }
    val connectionProfiles = remember(settings) { settings.normalizedConnectionProfiles() }
    val selectedConnectionProfile = remember(settings) { settings.selectedConnectionProfile() }
    val resolverProfiles = remember(settings) { settings.normalizedResolverProfiles() }
    val advancedProfiles = remember(settings) { settings.normalizedAdvancedProfiles() }
    val selectedAdvancedProfile = remember(settings) { settings.selectedAdvancedProfile() }
    val advancedProfileDirty = remember(settings, selectedAdvancedProfile) {
        !settings.matchesAdvancedProfile(selectedAdvancedProfile)
    }
    val hasInitialServerProfile = remember(connectionProfiles) {
        connectionProfiles.any { profile ->
            profile.customServerDomain.isNotBlank() &&
                profile.customServerEncryptionKey.isNotBlank()
        }
    }
    val hasInitialResolverProfile = resolverProfiles.isNotEmpty()
    val showInitialSetup = !hasInitialServerProfile || !hasInitialResolverProfile
    val selectedResolverProfile = remember(settings) { settings.selectedResolverProfile() }
    val resolverValidation = remember(settings.resolverText) { validateResolverText(settings.resolverText) }
    val context = LocalContext.current
    val appContext = remember(context) { context.applicationContext }
    val shouldLoadSplitTunnelApps = resolvedSettings.connectionMode == "vpn" ||
        resolvedSettings.splitTunnelPackages.isNotEmpty()
    var splitTunnelApps by remember(context.packageName) {
        mutableStateOf(emptyList<SplitTunnelAppInfo>())
    }
    LaunchedEffect(context.packageName, shouldLoadSplitTunnelApps) {
        splitTunnelApps = if (shouldLoadSplitTunnelApps) {
            withContext(Dispatchers.IO) {
                loadSplitTunnelAppOptions(appContext)
            }
        } else {
            emptyList()
        }
    }
    val splitTunnelAppLabels = remember(splitTunnelApps) {
        splitTunnelApps.associate { it.packageName to it.label }
    }
    val serverRouteMissingStr = WhiteZiaL10n.serverRouteMissing
    val shareChooserClientConfigLabel = WhiteZiaL10n.shareChooserClientConfig
    val advancedProfileModifiedSuffixLabel = WhiteZiaL10n.advancedProfileModifiedSuffix
    val resolverCountSingular = WhiteZiaL10n.resolverCountOneTemplate
    val resolverCountPlural = WhiteZiaL10n.resolverCountTemplate
    val noSavedListsStr = WhiteZiaL10n.homeSelectorNoSavedLists
    val notSelectedStr = WhiteZiaL10n.homeSelectorNotSelected
    val defaultConnectionLabel = WhiteZiaL10n.setupDefaultConnection
    val defaultResolverLabel = WhiteZiaL10n.setupDefaultResolver
    val defaultAdvancedLabel = WhiteZiaL10n.setupDefaultAdvanced
    val connectionSelectorItems = remember(connectionProfiles, serverRouteMissingStr, defaultConnectionLabel) {
        connectionProfiles.map { profile ->
            HomeSelectorItem(
                id = profile.id,
                title = if (profile.id == ConnectionProfile.DefaultId) defaultConnectionLabel else profile.name,
                detail = profile.customServerDomain.ifBlank { serverRouteMissingStr },
            )
        }
    }
    val resolverSelectorItems = remember(resolverProfiles, resolverCountSingular, resolverCountPlural, defaultResolverLabel) {
        resolverProfiles.map { profile ->
            HomeSelectorItem(
                id = profile.id,
                title = if (profile.id == ResolverProfile.DefaultId) defaultResolverLabel else profile.name,
                detail = resolverCountLabel(
                    validateResolverText(profile.resolverText).normalizedResolvers.size,
                    resolverCountSingular,
                    resolverCountPlural,
                ),
            )
        }
    }
    val advancedSelectorItems = remember(advancedProfiles, defaultAdvancedLabel) {
        advancedProfiles.map { profile ->
            HomeSelectorItem(
                id = profile.id,
                title = if (profile.id == AdvancedSettingsProfile.DefaultId) defaultAdvancedLabel else profile.name,
                detail = advancedProfileSummary(profile),
            )
        }
    }
    val resolverSelectorDetail = selectedResolverProfile?.resolverText?.let { resolverText ->
        resolverCountLabel(
            validateResolverText(resolverText).normalizedResolvers.size,
            resolverCountSingular,
            resolverCountPlural,
        )
    } ?: if (resolverProfiles.isEmpty()) {
        noSavedListsStr
    } else {
        notSelectedStr
    }
    val hasResolvers = resolverValidation.isValid
    val proxyIpAddress = displayProxyIpAddress(
        listenIp = resolvedSettings.listenIp,
        networkIpAddress = uiState.networkIpAddress,
    )
    val proxyAddress = "$proxyIpAddress:${resolvedSettings.listenPort}"
    val httpProxyAddress = "$proxyIpAddress:${resolvedSettings.httpProxyPort}"
    val showNotificationBanner = resolvedSettings.connectionMode == "vpn" && !uiState.notificationsEnabled
    val showBatteryBanner = !uiState.batteryOptimizationIgnored &&
        !settings.batteryOptimizationWarningDismissed
    val shouldCollapseParallelTestSelection =
        settings.autoTuneEnabled &&
            resolvedSettings.connectionMode == "vpn" &&
            uiState.connectionStatus == ConnectionStatus.CONNECTED &&
            uiState.autoTuneTrialResults.any { it.selected }

    LaunchedEffect(
        shouldCollapseParallelTestSelection,
        settings.autoTuneEnabled,
        uiState.connectionStatus,
    ) {
        when {
            shouldCollapseParallelTestSelection -> parallelTestSelectionExpanded = false
            !settings.autoTuneEnabled || uiState.connectionStatus == ConnectionStatus.DISCONNECTED ->
                parallelTestSelectionExpanded = true
        }
    }

    fun normalizeManualResolverInput() {
        if (selectedResolverProfile != null || !resolverValidation.isValid) {
            return
        }
        if (resolverValidation.normalizedText != settings.resolverText) {
            onSettingsChange(settings.updateManualResolverText(resolverValidation.normalizedText))
        }
    }

    fun openSelector(type: HomeSelectorType) {
        selectorSheetType = type
        selectorSheetVisible = true
    }

    fun closeSelector() {
        selectorSheetVisible = false
    }

    BackHandler(enabled = selectorSheetVisible) {
        closeSelector()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            HeaderCard(
                themeMode = settings.themeMode,
                onThemeModeChange = { onSettingsChange(settings.copy(themeMode = it)) },
                languageCode = settings.languageCode,
                onLanguageCodeChange = { onSettingsChange(settings.copy(languageCode = it)) },
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 420.dp)
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
            AnimatedVisibility(
                visible = showNotificationBanner,
                enter = fadeIn(animationSpec = tween(220)) + expandVertically(animationSpec = tween(220)),
                exit = fadeOut(animationSpec = tween(160)) + shrinkVertically(animationSpec = tween(160)),
            ) {
                Column {
                    NotificationPermissionBanner(onClick = onNotificationPermissionClick)
                    Spacer(modifier = Modifier.height(WhiteZiaSpacing.xl))
                }
            }
            AnimatedVisibility(
                visible = showBatteryBanner,
                enter = fadeIn(animationSpec = tween(220)) + expandVertically(animationSpec = tween(220)),
                exit = fadeOut(animationSpec = tween(160)) + shrinkVertically(animationSpec = tween(160)),
            ) {
                Column {
                    BatteryOptimizationBanner(
                        onClick = onBatteryOptimizationClick,
                        onDismiss = {
                            onSettingsChange(settings.copy(batteryOptimizationWarningDismissed = true))
                        },
                    )
                    Spacer(modifier = Modifier.height(WhiteZiaSpacing.xl))
                }
            }
            Spacer(
                modifier = Modifier.height(
                    if (!showNotificationBanner && !showBatteryBanner) 36.dp else 18.dp,
                ),
            )
                ConnectionModeSegmentedControl(
                    modifier = Modifier.fillMaxWidth(),
                    selectedMode = resolvedSettings.connectionMode,
                    enabled = uiState.connectionStatus == ConnectionStatus.DISCONNECTED,
                    onModeChange = { connectionMode ->
                        onSettingsChange(
                            settings.copy(connectionMode = connectionMode),
                        )
                    },
                )
            AnimatedVisibility(
                visible = resolvedSettings.connectionMode == "vpn",
                enter = fadeIn(animationSpec = tween(180)) + expandVertically(animationSpec = tween(180)),
                exit = fadeOut(animationSpec = tween(140)) + shrinkVertically(animationSpec = tween(140)),
            ) {
                Column {
                    Spacer(modifier = Modifier.height(WhiteZiaSpacing.inputSpacing))
                    AnimatedVisibility(
                        visible = !settings.fullVpnPerformanceWarningDismissed,
                        enter = fadeIn(animationSpec = tween(180)) + expandVertically(animationSpec = tween(180)),
                        exit = fadeOut(animationSpec = tween(140)) + shrinkVertically(animationSpec = tween(140)),
                    ) {
                        Column {
                            FullVpnPerformanceWarning(
                                onDismiss = {
                                    onSettingsChange(settings.copy(fullVpnPerformanceWarningDismissed = true))
                                },
                            )
                            Spacer(modifier = Modifier.height(WhiteZiaSpacing.inputSpacing))
                        }
                    }
                    SplitTunnelSettingsPanel(
                        settings = settings,
                        apps = splitTunnelApps,
                        onSettingsChange = onSettingsChange,
                    )
                }
            }
            Spacer(modifier = Modifier.height(WhiteZiaSpacing.xl))
            ConnectButton(
                status = uiState.connectionStatus,
                progressState = uiState.connectionProgress,
                enabled = uiState.connectionStatus != ConnectionStatus.DISCONNECTED || hasResolvers,
                onClick = {
                    if (uiState.connectionStatus == ConnectionStatus.DISCONNECTED && !hasResolvers) {
                        showResolverRequiredMessage = true
                    } else {
                        showResolverRequiredMessage = false
                        if (uiState.connectionStatus == ConnectionStatus.DISCONNECTED) {
                            normalizeManualResolverInput()
                        }
                        onConnectClick()
                    }
                },
            )
            AnimatedVisibility(
                visible = resolvedSettings.connectionMode == "proxy" || resolvedSettings.connectionMode == "vpn",
                enter = fadeIn(animationSpec = tween(180)) + expandVertically(animationSpec = tween(180)),
                exit = fadeOut(animationSpec = tween(140)) + shrinkVertically(animationSpec = tween(140)),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = WhiteZiaSpacing.md),
                ) {
                    val parallelTestControlsEnabled = uiState.connectionStatus == ConnectionStatus.DISCONNECTED
                    ToggleRow(
                        label = WhiteZiaL10n.parallelTest,
                        enabled = settings.autoTuneEnabled,
                        interactiveEnabled = parallelTestControlsEnabled,
                        onToggle = {
                            val selectedIds = WhiteZiaParallelTest.normalizeConfigIds(
                                configIds = settings.parallelTestSelectedConfigIds,
                                advancedProfiles = advancedProfiles,
                                includeAggressive = settings.parallelTestAggressivePresetsEnabled,
                            )
                            onSettingsChange(
                                settings.copy(
                                    autoTuneEnabled = !settings.autoTuneEnabled,
                                    parallelTestSelectedConfigIds = selectedIds,
                                ),
                            )
                        },
                    )
                    AnimatedVisibility(
                        visible = settings.autoTuneEnabled,
                        enter = fadeIn(animationSpec = tween(180)) + expandVertically(animationSpec = tween(180)),
                        exit = fadeOut(animationSpec = tween(140)) + shrinkVertically(animationSpec = tween(140)),
                    ) {
                        ParallelTestSelectionPanel(
                            settings = settings,
                            advancedProfiles = advancedProfiles,
                            controlsEnabled = parallelTestControlsEnabled,
                            expanded = parallelTestSelectionExpanded,
                            onExpandedChange = { parallelTestSelectionExpanded = it },
                            onSettingsChange = onSettingsChange,
                        )
                    }
                    Spacer(modifier = Modifier.height(WhiteZiaSpacing.sm))
                    MtuParallelismSlider(
                        parallelism = settings.mtuTestParallelismResolvers.toMtuParallelismSliderValue(),
                        enabled = parallelTestControlsEnabled,
                        onParallelismChange = { parallelism ->
                            onSettingsChange(settings.copy(mtuTestParallelismResolvers = parallelism.toString()))
                        },
                    )
                }
            }
            AnimatedVisibility(
                visible = uiState.connectionStatus != ConnectionStatus.CONNECTED,
                enter = fadeIn(animationSpec = tween(180)) + expandVertically(animationSpec = tween(180)),
                exit = fadeOut(animationSpec = tween(140)) + shrinkVertically(animationSpec = tween(140)),
            ) {
                Column {
                    Spacer(modifier = Modifier.height(WhiteZiaSpacing.md))
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        HomeSelectorCard(
                            label = WhiteZiaL10n.sectionConnection,
                            value = if (selectedConnectionProfile.id == ConnectionProfile.DefaultId) WhiteZiaL10n.setupDefaultConnection else selectedConnectionProfile.name,
                            detail = selectedConnectionProfile.customServerDomain.ifBlank { WhiteZiaL10n.serverRouteMissing },
                            selected = true,
                            enabled = uiState.connectionStatus == ConnectionStatus.DISCONNECTED,
                            onClick = { openSelector(HomeSelectorType.CONNECTION) },
                            onEditClick = {
                                showResolverRequiredMessage = false
                                showConnectionEditDialog = true
                            },
                        )
                        HomeSelectorCard(
                            label = WhiteZiaL10n.sectionResolver,
                            value = when {
                                selectedResolverProfile == null -> WhiteZiaL10n.homeSelectorResolverProfileFallback
                                selectedResolverProfile.id == ResolverProfile.DefaultId -> WhiteZiaL10n.setupDefaultResolver
                                else -> selectedResolverProfile.name
                            },
                            detail = resolverSelectorDetail,
                            selected = selectedResolverProfile != null,
                            enabled = uiState.connectionStatus == ConnectionStatus.DISCONNECTED,
                            onClick = { openSelector(HomeSelectorType.RESOLVER) },
                            editEnabled = selectedResolverProfile?.id != ResolverProfile.DefaultId,
                            onEditClick = {
                                showResolverRequiredMessage = false
                                showResolverEditDialog = true
                            },
                        )
                        AdvancedProfileControls(
                            selectedProfile = selectedAdvancedProfile,
                            dirty = advancedProfileDirty,
                            enabled = uiState.connectionStatus == ConnectionStatus.DISCONNECTED,
                            onSelectClick = { openSelector(HomeSelectorType.ADVANCED) },
                            onEditClick = {
                                showResolverRequiredMessage = false
                                showAdvancedEditDialog = true
                            },
                        )
                    }
                }
            }
            AnimatedVisibility(
                visible = uiState.connectionStatus == ConnectionStatus.CONNECTED,
                enter = fadeIn(animationSpec = tween(180)) + expandVertically(animationSpec = tween(180)),
                exit = fadeOut(animationSpec = tween(120)) + shrinkVertically(animationSpec = tween(120)),
            ) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    ResolverRuntimeSummary(
                        resolverState = uiState.resolverRuntimeState,
                        progressState = uiState.connectionProgress,
                        connectionStatus = uiState.connectionStatus,
                    )
                    ConnectionVerificationSummary(
                        modifier = Modifier.padding(top = 10.dp),
                        verification = uiState.connectionVerification,
                    )
                }
            }
            AnimatedVisibility(
                visible = showResolverRequiredMessage &&
                    uiState.connectionStatus == ConnectionStatus.DISCONNECTED &&
                    !hasResolvers,
                enter = fadeIn(animationSpec = tween(160)) + expandVertically(animationSpec = tween(160)),
                exit = fadeOut(animationSpec = tween(120)) + shrinkVertically(animationSpec = tween(120)),
            ) {
                Text(
                    modifier = Modifier.padding(top = 10.dp),
                    text = WhiteZiaL10n.connectNeedResolvers,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 11.sp,
                        color = WhiteZiaPalette.WarningText,
                        fontWeight = FontWeight.Medium,
                    ),
                )
            }
            AnimatedVisibility(
                visible = showInitialSetup,
                enter = fadeIn(animationSpec = tween(180)) + expandVertically(animationSpec = tween(180)),
                exit = fadeOut(animationSpec = tween(140)) + shrinkVertically(animationSpec = tween(140)),
            ) {
                Column {
                    Spacer(modifier = Modifier.height(WhiteZiaSpacing.xl))
                    val invalidIpLabel = WhiteZiaL10n.setupInvalidResolverIp
                    val noResolversConfiguredLabel = WhiteZiaL10n.setupNoResolversConfigured
                    ConnectionSetupCard(
                        selectedConnectionProfile = selectedConnectionProfile,
                        selectedResolverProfile = selectedResolverProfile,
                        resolverCount = resolverValidation.normalizedResolvers.size,
                        resolverIssue = resolverValidation.invalidEntries.firstOrNull()?.let { invalidEntry ->
                            "$invalidIpLabel: $invalidEntry"
                        } ?: if (resolverValidation.normalizedResolvers.isEmpty()) {
                            noResolversConfiguredLabel
                        } else {
                            null
                        },
                        actionsEnabled = uiState.connectionStatus != ConnectionStatus.CONNECTING,
                        onAddConnectionClick = onAddConnectionClick,
                        onAddResolverProfileClick = onAddResolverProfileClick,
                    )
                    Spacer(modifier = Modifier.height(WhiteZiaSpacing.xl))
                }
            }

            AnimatedVisibility(
                visible = uiState.connectionStatus == ConnectionStatus.CONNECTED,
                enter = fadeIn(animationSpec = tween(220)) + expandVertically(animationSpec = tween(220)),
                exit = fadeOut(animationSpec = tween(180)) + shrinkVertically(animationSpec = tween(180)),
            ) {
                LiveSpeedStrip(stats = uiState.connectionStats)
            }

            AnimatedVisibility(
                visible = uiState.connectionStatus == ConnectionStatus.CONNECTED ||
                    uiState.autoTuneTrialResults.isNotEmpty(),
                enter = fadeIn(animationSpec = tween(260)) + expandVertically(animationSpec = tween(260)),
                exit = fadeOut(animationSpec = tween(180)) + shrinkVertically(animationSpec = tween(180)),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp),
                ) {
                    ConnectionInfoCard(
                        connectionProfileName = if (selectedConnectionProfile.id == ConnectionProfile.DefaultId) WhiteZiaL10n.setupDefaultConnection else selectedConnectionProfile.name,
                        resolverProfileName = when {
                            selectedResolverProfile == null -> WhiteZiaL10n.setupManualResolvers
                            selectedResolverProfile.id == ResolverProfile.DefaultId -> WhiteZiaL10n.setupDefaultResolver
                            else -> selectedResolverProfile.name
                        },
                        settingProfileName = if (selectedAdvancedProfile.id == AdvancedSettingsProfile.DefaultId) {
                            selectedAdvancedProfile.copy(name = WhiteZiaL10n.setupDefaultAdvanced).displayName(dirty = advancedProfileDirty, modifiedSuffix = advancedProfileModifiedSuffixLabel)
                        } else {
                            selectedAdvancedProfile.displayName(dirty = advancedProfileDirty, modifiedSuffix = advancedProfileModifiedSuffixLabel)
                        },
                        listenAddress = proxyAddress,
                        httpProxyAddress = httpProxyAddress,
                        connectionMode = if (resolvedSettings.connectionMode == "vpn") WhiteZiaL10n.connectionModeVpn else WhiteZiaL10n.connectionModeProxy,
                        httpProxyEnabled = resolvedSettings.httpProxyEnabled,
                        protocol = resolvedSettings.protocolType,
                        socksAuthEnabled = resolvedSettings.socks5Authentication,
                        username = resolvedSettings.socksUsername,
                        password = resolvedSettings.socksPassword,
                        stats = uiState.connectionStats,
                        autoTuneResults = uiState.autoTuneTrialResults,
                        onSaveAutoTuneResult = { result ->
                            autoTuneSaveResult = result
                        },
                        showProxyDetails = resolvedSettings.connectionMode == "proxy",
                        splitTunnelMode = resolvedSettings.splitTunnelMode,
                        splitTunnelPackages = resolvedSettings.splitTunnelPackages,
                        splitTunnelAppLabels = splitTunnelAppLabels,
                        canDownloadToml = selectedConnectionProfile.customServerDomain.isNotBlank() &&
                            selectedConnectionProfile.customServerEncryptionKey.isNotBlank(),
                        onDownloadToml = {
                            showConnectionTomlDialog = true
                        },
                    )
                }
            }

            Spacer(modifier = Modifier.height(WhiteZiaSpacing.sectionSpacing))
            FooterLink()
        }
        }

        selectorSheetType?.let { activeSelector ->
            HomeSelectorSheet(
                visible = selectorSheetVisible,
                title = when (activeSelector) {
                    HomeSelectorType.CONNECTION -> WhiteZiaL10n.selectorConnectionProfiles
                    HomeSelectorType.RESOLVER -> WhiteZiaL10n.selectorResolverProfiles
                    HomeSelectorType.ADVANCED -> WhiteZiaL10n.selectorSettingProfiles
                },
                searchPlaceholder = when (activeSelector) {
                    HomeSelectorType.CONNECTION -> WhiteZiaL10n.homeSelectorSearchConnections
                    HomeSelectorType.RESOLVER -> WhiteZiaL10n.homeSelectorSearchResolvers
                    HomeSelectorType.ADVANCED -> WhiteZiaL10n.homeSelectorSearchSettings
                },
                items = when (activeSelector) {
                    HomeSelectorType.CONNECTION -> connectionSelectorItems
                    HomeSelectorType.RESOLVER -> resolverSelectorItems
                    HomeSelectorType.ADVANCED -> advancedSelectorItems
                },
                selectedId = when (activeSelector) {
                    HomeSelectorType.CONNECTION -> selectedConnectionProfile.id
                    HomeSelectorType.RESOLVER -> selectedResolverProfile?.id
                    HomeSelectorType.ADVANCED -> selectedAdvancedProfile.id
                },
                emptyMessage = when (activeSelector) {
                    HomeSelectorType.CONNECTION -> WhiteZiaL10n.homeSelectorNoConnectionProfiles
                    HomeSelectorType.RESOLVER -> WhiteZiaL10n.homeSelectorNoResolverProfiles
                    HomeSelectorType.ADVANCED -> WhiteZiaL10n.homeSelectorNoSettingProfiles
                },
                onDismiss = { closeSelector() },
                onSelect = { itemId ->
                    showResolverRequiredMessage = false
                    when (activeSelector) {
                        HomeSelectorType.CONNECTION -> {
                            onSettingsChange(settings.selectConnectionProfile(itemId))
                        }
                        HomeSelectorType.RESOLVER -> {
                            onSettingsChange(settings.applyResolverProfileToSelectedConnection(itemId))
                        }
                        HomeSelectorType.ADVANCED -> {
                            onSettingsChange(settings.selectAdvancedProfile(itemId))
                        }
                    }
                    closeSelector()
                },
            )
        }

        if (showConnectionTomlDialog) {
            ConnectionProfileExportDialog(
                title = WhiteZiaL10n.downloadTomlTitle,
                fieldLabel = "client_config.toml",
                placeholder = "client_config.toml",
                showQr = false,
                linkResult = remember(settings, selectedConnectionProfile, showConnectionTomlDialog) {
                    runCatching {
                        StormDnsConfigRenderer.renderClientToml(
                            connectionProfile = selectedConnectionProfile,
                            settings = settings,
                        )
                    }
                },
                onDismiss = { showConnectionTomlDialog = false },
                onShare = { toml ->
                    shareClientConfigToml(context, toml, shareChooserClientConfigLabel)
                },
            )
        }

        if (showConnectionEditDialog) {
            ConnectionProfileDialog(
                profile = selectedConnectionProfile,
                onDismiss = { showConnectionEditDialog = false },
                onSave = { updatedProfile ->
                    val nextProfile = updatedProfile.copy(
                        id = selectedConnectionProfile.id,
                        serverMode = "custom",
                        resolverProfileId = selectedConnectionProfile.resolverProfileId,
                        connectionMode = selectedConnectionProfile.connectionMode,
                    )
                    onSettingsChange(
                        settings
                            .upsertConnectionProfile(nextProfile)
                            .selectConnectionProfile(selectedConnectionProfile.id),
                    )
                    showConnectionEditDialog = false
                },
            )
        }

        if (showResolverEditDialog) {
            ResolverProfileDialog(
                profile = selectedResolverProfile,
                initialResolverText = selectedResolverProfile?.resolverText ?: settings.resolverText,
                onDismiss = { showResolverEditDialog = false },
                onSave = { profile ->
                    val nextSettings = if (selectedResolverProfile == null) {
                        settings.upsertResolverProfile(profile)
                    } else {
                        settings.upsertResolverProfile(profile.copy(id = selectedResolverProfile.id))
                    }
                    onSettingsChange(nextSettings)
                    showResolverEditDialog = false
                },
            )
        }

        if (showAdvancedEditDialog) {
            val editingProfile = selectedAdvancedProfile.takeIf { it.id != AdvancedSettingsProfile.DefaultId }
            AdvancedSettingsProfileDialog(
                profile = editingProfile,
                initialName = editingProfile?.name ?: advancedSaveAsInitialName(selectedAdvancedProfile, WhiteZiaL10n.homeSelectorCustomAdvanced, WhiteZiaL10n.profileNameCopySuffix),
                initialSettings = settings,
                onDismiss = { showAdvancedEditDialog = false },
                onSave = { profile ->
                    val nextProfile = if (editingProfile == null) {
                        profile
                    } else {
                        profile.copy(id = editingProfile.id)
                    }
                    onSettingsChange(settings.upsertAdvancedProfile(nextProfile))
                    showAdvancedEditDialog = false
                },
            )
        }

        autoTuneSaveResult?.let { result ->
            val initialSettings = remember(result.configId, settings, advancedProfiles) {
                parallelTestInitialSettingsForResult(
                    result = result,
                    settings = settings,
                    advancedProfiles = advancedProfiles,
                )
            }
            if (initialSettings == null) {
                autoTuneSaveResult = null
            } else {
                AdvancedSettingsProfileDialog(
                    profile = null,
                    initialName = result.label,
                    initialSettings = initialSettings,
                    onDismiss = { autoTuneSaveResult = null },
                    onSave = { profile ->
                        onSettingsChange(settings.upsertAdvancedProfile(profile))
                        autoTuneSaveResult = null
                    },
                )
            }
        }
    }
}

private fun parallelTestInitialSettingsForResult(
    result: AutoTuneTrialResult,
    settings: WhiteZiaSettings,
    advancedProfiles: List<AdvancedSettingsProfile>,
): WhiteZiaSettings? {
    WhiteZiaParallelTest.presetIdFromConfigId(result.configId)?.let { presetId ->
        val preset = WhiteZiaAutoTunePresets.all.firstOrNull { it.id == presetId } ?: return null
        return settings
            .applyAutoTunePreset(preset)
            .copy(autoTuneEnabled = false)
            .syncSelectedConnectionProfileFields()
    }
    WhiteZiaParallelTest.settingProfileIdFromConfigId(result.configId)?.let { profileId ->
        val profile = advancedProfiles.firstOrNull { it.id == profileId } ?: return null
        return settings
            .applyAdvancedProfile(profile)
            .copy(autoTuneEnabled = false)
            .syncSelectedConnectionProfileFields()
    }
    return null
}

@Composable
private fun ParallelTestSelectionPanel(
    settings: WhiteZiaSettings,
    advancedProfiles: List<AdvancedSettingsProfile>,
    controlsEnabled: Boolean,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSettingsChange: (WhiteZiaSettings) -> Unit,
) {
    val context = LocalContext.current
    val haptic = rememberHapticFeedback()
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "parallelTestSelectionArrow",
    )
    val userProfiles = advancedProfiles.filter { it.id != AdvancedSettingsProfile.DefaultId }
    val selectedIds = WhiteZiaParallelTest.normalizeConfigIds(
        configIds = settings.parallelTestSelectedConfigIds,
        advancedProfiles = advancedProfiles,
        includeAggressive = settings.parallelTestAggressivePresetsEnabled,
    )
    val selectedSet = selectedIds.toSet()
    val stableWhiteZiaConfigIds = WhiteZiaParallelTest.stableConfigIds
    val aggressiveWhiteZiaConfigIds = WhiteZiaParallelTest.aggressiveConfigIds
    val selectedStableWhiteZiaCount = stableWhiteZiaConfigIds.count { it in selectedSet }
    val allStableWhiteZiaSelected = selectedStableWhiteZiaCount == stableWhiteZiaConfigIds.size
    val canAddStableWhiteZiaConfigs = allStableWhiteZiaSelected ||
        selectedIds.size - selectedStableWhiteZiaCount + stableWhiteZiaConfigIds.size <=
        WhiteZiaParallelTest.MaxSelectedConfigs
    val selectedAggressiveWhiteZiaCount = aggressiveWhiteZiaConfigIds.count { it in selectedSet }
    val allAggressiveWhiteZiaSelected = settings.parallelTestAggressivePresetsEnabled &&
        selectedAggressiveWhiteZiaCount == aggressiveWhiteZiaConfigIds.size
    val canAddAggressiveWhiteZiaConfigs = allAggressiveWhiteZiaSelected ||
        selectedIds.size - selectedAggressiveWhiteZiaCount + aggressiveWhiteZiaConfigIds.size <=
        WhiteZiaParallelTest.MaxSelectedConfigs

    fun updateSelectedConfigIds(
        nextIds: List<String>,
        includeAggressive: Boolean = settings.parallelTestAggressivePresetsEnabled,
    ) {
        val normalizedIds = WhiteZiaParallelTest.normalizeConfigIds(
            configIds = nextIds,
            advancedProfiles = advancedProfiles,
            defaultIfEmpty = false,
            includeAggressive = includeAggressive,
        )
        val keepsAggressive = includeAggressive && aggressiveWhiteZiaConfigIds.any { it in normalizedIds }
        onSettingsChange(
            settings.copy(
                parallelTestSelectedConfigIds = normalizedIds,
                parallelTestAggressivePresetsEnabled = keepsAggressive,
            ),
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = WhiteZiaSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(WhiteZiaSpacing.sm),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .semantics {
                    contentDescription = if (expanded) {
                        context.getString(R.string.cd_parallel_test_collapse)
                    } else {
                        context.getString(R.string.cd_parallel_test_expand)
                    }
                }
                .clickable {
                    haptic.performLight()
                    onExpandedChange(!expanded)
                }
                .padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${WhiteZiaL10n.connectSelectedCount} ${selectedIds.size}/${WhiteZiaParallelTest.MaxSelectedConfigs}",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    color = WhiteZiaPalette.FieldLabel,
                    fontWeight = FontWeight.SemiBold,
                ),
            )
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (expanded) WhiteZiaPalette.Accent else WhiteZiaPalette.SurfaceAlt)
                    .padding(start = 10.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = if (expanded) WhiteZiaL10n.parallelTestOpenLabel else WhiteZiaL10n.parallelTestClosedLabel,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 9.sp,
                        color = if (expanded) WhiteZiaPalette.OnAccent else WhiteZiaPalette.Muted,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.8.sp,
                    ),
                )
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = if (expanded) {
                        WhiteZiaL10n.parallelTestCollapseDescription
                    } else {
                        WhiteZiaL10n.parallelTestExpandDescription
                    },
                    tint = if (expanded) WhiteZiaPalette.OnAccent else WhiteZiaPalette.Muted,
                    modifier = Modifier
                        .size(16.dp)
                        .graphicsLayer(rotationZ = arrowRotation),
                )
            }
        }
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(animationSpec = tween(180)) + expandVertically(animationSpec = tween(180)),
            exit = fadeOut(animationSpec = tween(140)) + shrinkVertically(animationSpec = tween(140)),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(WhiteZiaSpacing.sm),
            ) {
                Text(
                    text = WhiteZiaL10n.parallelTestDescription,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 11.sp,
                        color = WhiteZiaPalette.Muted,
                        lineHeight = 16.sp,
                    ),
                )
                ParallelTestConfigRow(
                    label = WhiteZiaL10n.whiteZiaConfigsLabel,
                    detail = WhiteZiaL10n.whiteZiaConfigsDescription,
                    checked = allStableWhiteZiaSelected,
                    enabled = controlsEnabled && canAddStableWhiteZiaConfigs,
                    onToggle = {
                        haptic.performLight()
                        val withoutStableWhiteZiaConfigs = selectedIds.filterNot { it in stableWhiteZiaConfigIds }
                        if (allStableWhiteZiaSelected) {
                            if (withoutStableWhiteZiaConfigs.isNotEmpty()) {
                                updateSelectedConfigIds(withoutStableWhiteZiaConfigs)
                            }
                        } else if (canAddStableWhiteZiaConfigs) {
                            updateSelectedConfigIds(stableWhiteZiaConfigIds + withoutStableWhiteZiaConfigs)
                        }
                    },
                )
                ParallelTestConfigRow(
                    label = WhiteZiaL10n.whiteZiaAggressiveConfigsLabel,
                    detail = WhiteZiaL10n.whiteZiaAggressiveConfigsDescription,
                    checked = allAggressiveWhiteZiaSelected,
                    enabled = controlsEnabled && canAddAggressiveWhiteZiaConfigs,
                    onToggle = {
                        haptic.performLight()
                        val withoutAggressiveWhiteZiaConfigs = selectedIds.filterNot {
                            it in aggressiveWhiteZiaConfigIds
                        }
                        if (allAggressiveWhiteZiaSelected) {
                            updateSelectedConfigIds(
                                withoutAggressiveWhiteZiaConfigs,
                                includeAggressive = false,
                            )
                        } else if (canAddAggressiveWhiteZiaConfigs) {
                            updateSelectedConfigIds(
                                withoutAggressiveWhiteZiaConfigs + aggressiveWhiteZiaConfigIds,
                                includeAggressive = true,
                            )
                        }
                    },
                )
                if (userProfiles.isNotEmpty()) {
                    Text(
                        modifier = Modifier.padding(top = WhiteZiaSpacing.xs),
                        text = WhiteZiaL10n.parallelTestYourConfigs,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            color = WhiteZiaPalette.FieldLabel,
                            fontWeight = FontWeight.SemiBold,
                        ),
                    )
                    userProfiles.forEach { profile ->
                        val configId = WhiteZiaParallelTest.settingConfigId(profile.id)
                        val checked = configId in selectedSet
                        val enabled = checked || selectedIds.size < WhiteZiaParallelTest.MaxSelectedConfigs
                        ParallelTestConfigRow(
                            label = profile.name.ifBlank { WhiteZiaL10n.genericSettingFallback },
                            detail = advancedProfileSummary(profile),
                            checked = checked,
                            enabled = controlsEnabled && enabled,
                            onToggle = {
                                haptic.performLight()
                                val nextIds = if (checked) {
                                    if (selectedIds.size == 1) {
                                        selectedIds
                                    } else {
                                        selectedIds.filterNot { it == configId }
                                    }
                                } else {
                                    selectedIds + configId
                                }
                                updateSelectedConfigIds(nextIds)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ParallelTestConfigRow(
    label: String,
    detail: String,
    checked: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    val contentAlpha = if (enabled) 1f else 0.46f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(11.dp))
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Checkbox,
                onValueChange = { onToggle() },
            )
            .padding(vertical = 7.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            enabled = enabled,
            onCheckedChange = null,
            modifier = Modifier.clearAndSetSemantics {},
            colors = CheckboxDefaults.colors(
                checkedColor = WhiteZiaPalette.Accent,
                uncheckedColor = WhiteZiaPalette.ControlBorder,
                checkmarkColor = WhiteZiaPalette.OnAccent,
                disabledCheckedColor = WhiteZiaPalette.Accent.copy(alpha = 0.42f),
                disabledUncheckedColor = WhiteZiaPalette.ControlBorder.copy(alpha = 0.42f),
            ),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 12.sp,
                    color = WhiteZiaPalette.Ink.copy(alpha = contentAlpha),
                    fontWeight = FontWeight.Medium,
                ),
            )
            Text(
                text = detail,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 10.sp,
                    color = WhiteZiaPalette.Muted.copy(alpha = contentAlpha),
                    lineHeight = 14.sp,
                ),
            )
        }
    }
}

@Composable
private fun ProfilesTabContent(
    uiState: WhiteZiaUiState,
    createRequest: ProfileCreateRequest?,
    onCreateRequestConsumed: () -> Unit,
    onServerTestClick: (String?) -> Unit,
    onSettingsChange: (WhiteZiaSettings) -> Unit,
) {
    var selectedProfileTab by rememberSaveable { mutableStateOf(ProfileTab.CONNECTION) }
    var connectionCreateRequestId by rememberSaveable { mutableStateOf(0) }
    var resolverCreateRequestId by rememberSaveable { mutableStateOf(0) }
    var showSettingGuide by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(createRequest) {
        when (createRequest) {
            ProfileCreateRequest.CONNECTION -> {
                selectedProfileTab = ProfileTab.CONNECTION
                connectionCreateRequestId += 1
                onCreateRequestConsumed()
            }
            ProfileCreateRequest.RESOLVER -> {
                selectedProfileTab = ProfileTab.RESOLVER
                resolverCreateRequestId += 1
                onCreateRequestConsumed()
            }
            null -> Unit
        }
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
                .padding(horizontal = 20.dp),
        ) {
            ProfileTabSwitch(
                selectedTab = selectedProfileTab,
                onTabSelected = { selectedProfileTab = it },
            )
            Spacer(modifier = Modifier.height(WhiteZiaSpacing.md))
            InfoCard(
                title = when (selectedProfileTab) {
                    ProfileTab.CONNECTION -> WhiteZiaL10n.selectorConnectionProfiles.uppercase()
                    ProfileTab.RESOLVER -> WhiteZiaL10n.selectorResolverProfiles.uppercase()
                    ProfileTab.SETTING -> WhiteZiaL10n.selectorSettingProfiles.uppercase()
                },
                titleAction = if (selectedProfileTab == ProfileTab.SETTING) {
                    {
                        SettingProfileGuideButton(
                            onClick = { showSettingGuide = true },
                        )
                    }
                } else {
                    null
                },
            ) {
                when (selectedProfileTab) {
                    ProfileTab.CONNECTION -> ConnectionProfilesSettings(
                        settings = uiState.settings,
                        activeConnectionProfileId = uiState.activeConnectionProfileId,
                        connectionStatus = uiState.connectionStatus,
                        serverTestState = uiState.serverTestState,
                        openCreateRequestId = connectionCreateRequestId,
                        onServerTestClick = onServerTestClick,
                        onSettingsChange = onSettingsChange,
                    )
                    ProfileTab.RESOLVER -> ResolverProfilesSettings(
                        settings = uiState.settings,
                        connectionStatus = uiState.connectionStatus,
                        openCreateRequestId = resolverCreateRequestId,
                        onSettingsChange = onSettingsChange,
                    )
                    ProfileTab.SETTING -> SettingProfilesSettings(
                        settings = uiState.settings,
                        connectionStatus = uiState.connectionStatus,
                        onSettingsChange = onSettingsChange,
                    )
                }
            }
            Spacer(modifier = Modifier.height(WhiteZiaSpacing.sectionSpacing))
            FooterLink()
        }
    }

    if (showSettingGuide) {
        SettingProfileGuideDialog(
            onDismiss = { showSettingGuide = false },
        )
    }
}

private enum class ProfileTab(val label: String) {
    CONNECTION("Connection"),
    RESOLVER("Resolver"),
    SETTING("Setting"),
}

@Composable
private fun tabLabel(tab: WhiteZiaTab): String = when (tab) {
    WhiteZiaTab.PROFILES -> WhiteZiaL10n.tabProfiles
    WhiteZiaTab.CONNECT -> WhiteZiaL10n.tabConnect
    WhiteZiaTab.SCAN -> WhiteZiaL10n.tabScan
    WhiteZiaTab.LOGS -> WhiteZiaL10n.tabLogs
}

@Composable
private fun profileTabLabel(tab: ProfileTab): String = when (tab) {
    ProfileTab.CONNECTION -> WhiteZiaL10n.profileTabConnection
    ProfileTab.RESOLVER -> WhiteZiaL10n.profileTabResolver
    ProfileTab.SETTING -> WhiteZiaL10n.profileTabSetting
}

@Composable
private fun SettingProfileGuideButton(
    onClick: () -> Unit,
) {
    val haptic = rememberHapticFeedback()
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(WhiteZiaPalette.AccentSurface)
            .border(1.5.dp, WhiteZiaPalette.Accent.copy(alpha = 0.26f), CircleShape)
            .clickable {
                haptic.performLight()
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.HelpOutline,
            contentDescription = WhiteZiaL10n.cdSettingGuide,
            tint = WhiteZiaPalette.AccentText,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun SettingProfileGuideDialog(
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    modifier = Modifier.weight(1f),
                    text = WhiteZiaL10n.settingGuideTitle,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 14.sp,
                        color = WhiteZiaPalette.Ink,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.1.sp,
                    ),
                )
                ProfileIconButton(
                    icon = Icons.Rounded.Close,
                    contentDescription = WhiteZiaL10n.btnClose,
                    emphasized = false,
                    enabled = true,
                    onClick = onDismiss,
                )
            }
            Spacer(modifier = Modifier.height(WhiteZiaSpacing.sm))
            Text(
                text = WhiteZiaL10n.settingGuideIntro,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    color = WhiteZiaPalette.Description,
                ),
            )
            Spacer(modifier = Modifier.height(WhiteZiaSpacing.xs))
            Text(
                text = WhiteZiaL10n.settingGuideSource,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 9.sp,
                    lineHeight = 13.sp,
                    color = WhiteZiaPalette.Pale,
                ),
            )
            Spacer(modifier = Modifier.height(WhiteZiaSpacing.md))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                WhiteZiaL10n.settingGuideSections.forEachIndexed { index, section ->
                    if (index > 0) {
                        SectionDivider()
                    }
                    SettingsGuideSectionView(section = section)
                }
            }
        }
    }
}

@Composable
private fun SettingsGuideSectionView(
    section: SettingsGuideSection,
) {
    Text(
        text = section.title.uppercase(),
        style = MaterialTheme.typography.bodyMedium.copy(
            fontSize = 12.sp,
            color = WhiteZiaPalette.SectionTitle,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
        ),
    )
    Spacer(modifier = Modifier.height(WhiteZiaSpacing.sm))
    section.entries.forEachIndexed { index, entry ->
        if (index > 0) {
            Spacer(modifier = Modifier.height(WhiteZiaSpacing.sm))
        }
        SettingsGuideEntryView(entry = entry)
    }
}

@Composable
private fun SettingsGuideEntryView(
    entry: SettingsGuideEntry,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(WhiteZiaPalette.SurfaceAlt)
            .border(1.5.dp, WhiteZiaPalette.Border, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 9.dp),
    ) {
        Text(
            text = entry.title,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 11.sp,
                lineHeight = 15.sp,
                color = WhiteZiaPalette.Ink,
                fontWeight = FontWeight.Bold,
            ),
        )
        Spacer(modifier = Modifier.height(WhiteZiaSpacing.xs))
        Text(
            text = entry.body,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 10.sp,
                lineHeight = 15.sp,
                color = WhiteZiaPalette.Description,
            ),
        )
        Spacer(modifier = Modifier.height(WhiteZiaSpacing.xs))
        Text(
            text = "${WhiteZiaL10n.settingGuideEffectLabel}: ${entry.effect}",
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 10.sp,
                lineHeight = 15.sp,
                color = WhiteZiaPalette.AccentText,
                fontWeight = FontWeight.Medium,
            ),
        )
    }
}

@Composable
private fun LogsTabContent(
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
private fun ScanTabContent(
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
                contentDescription = context.getString(R.string.cd_worker_count_slider, workerCount)
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
private fun MtuParallelismSlider(
    parallelism: Int,
    enabled: Boolean,
    onParallelismChange: (Int) -> Unit,
) {
    val context = LocalContext.current
    var sliderValue by remember(parallelism) { mutableStateOf(parallelism.toMtuParallelismSliderValue().toFloat()) }
    val displayedParallelism = sliderValue.toMtuParallelismSliderValue()
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
                contentDescription = context.getString(R.string.cd_mtu_parallelism_slider, displayedParallelism)
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

private fun String.toMtuParallelismSliderValue(): Int {
    return toIntOrNull()?.toMtuParallelismSliderValue() ?: MtuParallelismDefault
}

private fun Float.toMtuParallelismSliderValue(): Int {
    return roundToInt().toMtuParallelismSliderValue()
}

private fun Int.toMtuParallelismSliderValue(): Int {
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

@Composable
private fun BottomNavigationBar(
    selectedTab: WhiteZiaTab,
    onTabSelected: (WhiteZiaTab) -> Unit,
) {
    val haptic = rememberHapticFeedback()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(WhiteZiaPalette.SurfaceAlt)
            .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.5.dp, WhiteZiaPalette.Border)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            WhiteZiaTab.entries.forEach { tab ->
                val selected = selectedTab == tab
                val localizedLabel = tabLabel(tab)
                val background by animateColorAsState(
                    targetValue = if (selected) WhiteZiaPalette.AccentSurface else Color.Transparent,
                    animationSpec = tween(180),
                    label = "bottomNavBackground",
                )
                val color by animateColorAsState(
                    targetValue = if (selected) WhiteZiaPalette.AccentText else WhiteZiaPalette.Disabled,
                    animationSpec = tween(180),
                    label = "bottomNavColor",
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(background)
                        .selectable(
                            selected = selected,
                            role = Role.Tab,
                            onClick = {
                                haptic.performLight()
                                onTabSelected(tab)
                            },
                        )
                        .semantics(mergeDescendants = true) {}
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = localizedLabel,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 9.sp,
                            color = color,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            letterSpacing = 0.5.sp,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileTabSwitch(
    selectedTab: ProfileTab,
    onTabSelected: (ProfileTab) -> Unit,
) {
    val haptic = rememberHapticFeedback()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(WhiteZiaPalette.Surface)
            .border(1.5.dp, WhiteZiaPalette.ControlBorder, RoundedCornerShape(14.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ProfileTab.entries.forEach { tab ->
            val selected = selectedTab == tab
            val localizedProfileLabel = profileTabLabel(tab)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (selected) {
                            WhiteZiaPalette.Accent
                        } else {
                            Color.Transparent
                        },
                    )
                    .selectable(
                        selected = selected,
                        role = Role.Tab,
                        onClick = {
                            haptic.performLight()
                            onTabSelected(tab)
                        },
                    )
                    .semantics(mergeDescendants = true) {}
                    .padding(horizontal = 8.dp, vertical = 11.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = localizedProfileLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 9.sp,
                        color = if (selected) WhiteZiaPalette.OnAccent else WhiteZiaPalette.Muted,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        letterSpacing = 0.4.sp,
                    ),
                )
            }
        }
    }
}

@Composable
private fun FooterLink() {
    val context = LocalContext.current
    val haptic = rememberHapticFeedback()

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
                    contentDescription = context.getString(R.string.cd_telegram_link)
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
private fun ConnectionModeSegmentedControl(
    selectedMode: String,
    enabled: Boolean,
    onModeChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = rememberHapticFeedback()

    val modeOptions: List<Pair<String, String>> = listOf(
        "proxy" to WhiteZiaL10n.connectionModeProxy,
        "vpn" to WhiteZiaL10n.connectionModeVpn,
    )
    Column(modifier = modifier) {
        FieldLabel(WhiteZiaL10n.fieldMode)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(if (enabled) WhiteZiaPalette.Surface else WhiteZiaPalette.SurfaceAlt)
                .border(1.5.dp, WhiteZiaPalette.ControlBorder, RoundedCornerShape(12.dp))
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            modeOptions.forEach { (modeValue, modeLabel) ->
                val selected = selectedMode == modeValue
                val background by animateColorAsState(
                    targetValue = if (selected) {
                        WhiteZiaPalette.Accent
                    } else {
                        Color.Transparent
                    },
                    animationSpec = tween(180),
                    label = "connectionModeSegmentBackground",
                )
                val textColor by animateColorAsState(
                    targetValue = when {
                        !enabled -> WhiteZiaPalette.Disabled
                        selected -> WhiteZiaPalette.OnAccent
                        else -> WhiteZiaPalette.Muted
                    },
                    animationSpec = tween(180),
                    label = "connectionModeSegmentText",
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(9.dp))
                        .background(background)
                        .selectable(
                            selected = selected,
                            enabled = enabled,
                            role = Role.RadioButton,
                            onClick = {
                                haptic.performLight()
                                onModeChange(modeValue)
                            },
                        )
                        .semantics(mergeDescendants = true) {}
                        .padding(horizontal = 6.dp, vertical = 10.dp),
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

private enum class HomeSelectorType {
    CONNECTION,
    RESOLVER,
    ADVANCED,
}

private data class HomeSelectorItem(
    val id: String,
    val title: String,
    val detail: String,
)

@Composable
private fun HomeSelectorCard(
    label: String,
    value: String,
    detail: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onEditClick: (() -> Unit)? = null,
    editEnabled: Boolean = enabled,
) {
    val haptic = rememberHapticFeedback()
    val borderColor = when {
        !enabled -> WhiteZiaPalette.Divider
        selected -> WhiteZiaPalette.Accent.copy(alpha = 0.28f)
        else -> WhiteZiaPalette.Border
    }
    val textColor = if (enabled) WhiteZiaPalette.Ink else WhiteZiaPalette.Disabled
    val detailColor = when {
        !enabled -> WhiteZiaPalette.Disabled
        selected -> WhiteZiaPalette.AccentText
        else -> WhiteZiaPalette.Muted
    }

    Column(modifier = modifier) {
        FieldLabel(label)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(if (enabled) WhiteZiaPalette.Surface else WhiteZiaPalette.SurfaceAlt)
                .border(1.5.dp, borderColor, RoundedCornerShape(12.dp))
                .clickable(enabled = enabled) {
                    haptic.performLight()
                    onClick()
                }
                .padding(horizontal = 11.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = value,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 12.sp,
                        color = textColor,
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
                        color = detailColor,
                        fontWeight = FontWeight.Medium,
                    ),
                )
            }
            if (onEditClick != null) {
                ProfileIconButton(
                    icon = Icons.Rounded.Edit,
                    contentDescription = "${WhiteZiaL10n.cdEditPrefix} $label",
                    emphasized = false,
                    enabled = editEnabled,
                    onClick = onEditClick,
                )
            }
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = stringResource(R.string.cd_dropdown_advanced_settings),
                tint = if (enabled) WhiteZiaPalette.Muted else WhiteZiaPalette.Disabled,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun AdvancedProfileControls(
    selectedProfile: AdvancedSettingsProfile,
    dirty: Boolean,
    enabled: Boolean,
    onSelectClick: () -> Unit,
    onEditClick: () -> Unit,
) {
    HomeSelectorCard(
        label = WhiteZiaL10n.homeSelectorSettingLabel,
        value = if (selectedProfile.id == AdvancedSettingsProfile.DefaultId) WhiteZiaL10n.setupDefaultAdvanced else selectedProfile.name,
        detail = if (dirty) WhiteZiaL10n.homeSelectorUnsavedChanges else advancedProfileSummary(selectedProfile),
        selected = !dirty,
        enabled = enabled,
        onClick = onSelectClick,
        onEditClick = onEditClick,
    )
}

@Composable
private fun AdvancedSettingsImportDialog(
    onDismiss: () -> Unit,
    onImport: (String, String) -> Result<WhiteZiaSettings>,
) {
    val context = LocalContext.current
    var name by rememberSaveable { mutableStateOf("") }
    var tomlText by rememberSaveable { mutableStateOf("") }
    var importError by rememberSaveable { mutableStateOf<String?>(null) }
    val canImport = name.trim().isNotEmpty() && tomlText.trim().isNotEmpty()
    val errorImportSettingsFileLabel = WhiteZiaL10n.errorImportSettingsFile
    val errorImportSettingsLabel = WhiteZiaL10n.errorImportSettings
    val importTomlFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) {
            return@rememberLauncherForActivityResult
        }
        readTextFromUri(context, uri, errorImportSettingsFileLabel)
            .onSuccess { importedText ->
                tomlText = importedText
                importError = null
            }
            .onFailure { error ->
                importError = error.message ?: errorImportSettingsFileLabel
            }
    }

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
                text = WhiteZiaL10n.profileDialogImportSettings,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    color = WhiteZiaPalette.Ink,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.1.sp,
                ),
            )
            Spacer(modifier = Modifier.height(WhiteZiaSpacing.md))
            WhiteZiaTextField(
                label = WhiteZiaL10n.profileFieldName,
                value = name,
                onValueChange = {
                    name = it
                    importError = null
                },
                placeholder = WhiteZiaL10n.settingProfileImportedSettingsPlaceholder,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CompactActionButton(
                    modifier = Modifier.weight(1f),
                    label = WhiteZiaL10n.profileBtnImportFile,
                    emphasized = false,
                    enabled = true,
                    onClick = {
                        importTomlFileLauncher.launch(SettingsImportMimeTypes)
                    },
                )
                CompactActionButton(
                    modifier = Modifier.weight(1f),
                    label = WhiteZiaL10n.profileBtnClear,
                    emphasized = false,
                    enabled = tomlText.isNotBlank(),
                    onClick = {
                        tomlText = ""
                        importError = null
                    },
                )
            }
            WhiteZiaTextField(
                label = WhiteZiaL10n.profileFieldToml,
                value = tomlText,
                onValueChange = {
                    tomlText = it
                    importError = null
                },
                placeholder = "LISTEN_PORT = 10886\nLOG_LEVEL = \"WARN\"",
                singleLine = false,
                minLines = 7,
                maxLines = 12,
            )
            importError?.let { message ->
                Spacer(modifier = Modifier.height(WhiteZiaSpacing.iconSpacing))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 10.sp,
                        color = WhiteZiaPalette.Error,
                    ),
                )
            }
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
                    label = WhiteZiaL10n.btnImport,
                    emphasized = true,
                    enabled = canImport,
                    onClick = {
                        onImport(name, tomlText)
                            .onFailure { error ->
                                importError = error.message ?: errorImportSettingsLabel
                            }
                    },
                )
            }
        }
    }
}

@Composable
private fun AdvancedSettingsFields(
    settings: WhiteZiaSettings,
    showProxySettings: Boolean,
    onSettingsChange: (WhiteZiaSettings) -> Unit,
) {
    GroupLabel(WhiteZiaL10n.groupMtu)
    MtuSettingsGroup(
        settings = settings,
        onSettingsChange = onSettingsChange,
    )

    SectionDivider()
    GroupLabel(WhiteZiaL10n.groupRuntimeWorkers)
    RuntimeWorkersSettingsGroup(
        settings = settings,
        onSettingsChange = onSettingsChange,
    )

    SectionDivider()
    if (showProxySettings) {
        GroupLabel(WhiteZiaL10n.groupLocalProxy)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WhiteZiaTextField(
                modifier = Modifier.weight(1f),
                label = WhiteZiaL10n.settingListenIp,
                value = settings.listenIp,
                onValueChange = { onSettingsChange(settings.copy(listenIp = it)) },
                placeholder = "127.0.0.1",
            )
            WhiteZiaTextField(
                modifier = Modifier.weight(1f),
                label = WhiteZiaL10n.settingListenPort,
                value = settings.listenPort,
                onValueChange = { onSettingsChange(settings.copy(listenPort = it.filter(Char::isDigit))) },
                placeholder = "10886",
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    capitalization = KeyboardCapitalization.None,
                ),
            )
        }

        ToggleRow(
            label = WhiteZiaL10n.settingHttpProxy,
            enabled = settings.httpProxyEnabled,
            onToggle = {
                onSettingsChange(settings.copy(httpProxyEnabled = !settings.httpProxyEnabled))
            },
        )
        AnimatedVisibility(
            visible = settings.httpProxyEnabled,
            enter = fadeIn(animationSpec = tween(220)) + expandVertically(animationSpec = tween(220)),
            exit = fadeOut(animationSpec = tween(160)) + shrinkVertically(animationSpec = tween(160)),
        ) {
            WhiteZiaTextField(
                label = WhiteZiaL10n.settingHttpPort,
                value = settings.httpProxyPort,
                onValueChange = { onSettingsChange(settings.copy(httpProxyPort = it.filter(Char::isDigit))) },
                placeholder = "10887",
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    capitalization = KeyboardCapitalization.None,
                ),
            )
        }

        ToggleRow(
            label = WhiteZiaL10n.settingSocks5Auth,
            enabled = settings.socks5Authentication,
            onToggle = {
                onSettingsChange(settings.copy(socks5Authentication = !settings.socks5Authentication))
            },
        )

        AnimatedVisibility(
            visible = settings.socks5Authentication,
            enter = fadeIn(animationSpec = tween(220)) + expandVertically(animationSpec = tween(220)),
            exit = fadeOut(animationSpec = tween(160)) + shrinkVertically(animationSpec = tween(160)),
        ) {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WhiteZiaTextField(
                        modifier = Modifier.weight(1f),
                        label = WhiteZiaL10n.settingUsername,
                        value = settings.socksUsername,
                        onValueChange = { onSettingsChange(settings.copy(socksUsername = it)) },
                        placeholder = "master_dns_vpn",
                    )
                    WhiteZiaTextField(
                        modifier = Modifier.weight(1f),
                        label = WhiteZiaL10n.settingPassword,
                        value = settings.socksPassword,
                        onValueChange = { onSettingsChange(settings.copy(socksPassword = it)) },
                        placeholder = "master_dns_vpn",
                        visualTransformation = PasswordVisualTransformation(),
                    )
                }
            }
        }

        SectionDivider()
    }

    GroupLabel(WhiteZiaL10n.groupNetworkTuning)

    WhiteZiaDropdownField(
        label = WhiteZiaL10n.settingBalancingStrategy,
        value = settings.balancingStrategy,
        options = localizedBalancingStrategies(),
        onValueChange = { onSettingsChange(settings.copy(balancingStrategy = it)) },
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        WhiteZiaTextField(
            modifier = Modifier.weight(1f),
            label = WhiteZiaL10n.settingUploadDup,
            value = settings.uploadDuplication,
            onValueChange = {
                onSettingsChange(settings.copy(uploadDuplication = it.filter(Char::isDigit)))
            },
            placeholder = "3",
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                capitalization = KeyboardCapitalization.None,
            ),
        )
        WhiteZiaTextField(
            modifier = Modifier.weight(1f),
            label = WhiteZiaL10n.settingDownloadDup,
            value = settings.downloadDuplication,
            onValueChange = {
                onSettingsChange(settings.copy(downloadDuplication = it.filter(Char::isDigit)))
            },
            placeholder = "7",
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                capitalization = KeyboardCapitalization.None,
            ),
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        WhiteZiaDropdownField(
            modifier = Modifier.weight(1f),
            label = WhiteZiaL10n.settingUploadCompress,
            value = settings.uploadCompression,
            options = localizedCompressionTypes(),
            onValueChange = { onSettingsChange(settings.copy(uploadCompression = it)) },
        )
        WhiteZiaDropdownField(
            modifier = Modifier.weight(1f),
            label = WhiteZiaL10n.settingDownloadCompress,
            value = settings.downloadCompression,
            options = localizedCompressionTypes(),
            onValueChange = { onSettingsChange(settings.copy(downloadCompression = it)) },
        )
    }
    ToggleRow(
        label = WhiteZiaL10n.settingBaseEncodeData,
        enabled = settings.baseEncodeData,
        onToggle = {
            onSettingsChange(settings.copy(baseEncodeData = !settings.baseEncodeData))
        },
    )

    SectionDivider()
    GroupLabel(WhiteZiaL10n.groupReliability)

    WhiteZiaTextField(
        label = WhiteZiaL10n.settingPingWatchdog,
        value = settings.pingWatchdogSeconds,
        onValueChange = {
            onSettingsChange(settings.copy(pingWatchdogSeconds = it.filter(Char::isDigit)))
        },
        placeholder = "300",
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            capitalization = KeyboardCapitalization.None,
        ),
    )
    ToggleRow(
        label = WhiteZiaL10n.settingTrafficWarmup,
        enabled = settings.trafficWarmupEnabled,
        onToggle = {
            onSettingsChange(settings.copy(trafficWarmupEnabled = !settings.trafficWarmupEnabled))
        },
    )
    AnimatedVisibility(
        visible = settings.trafficWarmupEnabled,
        enter = fadeIn(animationSpec = tween(220)) + expandVertically(animationSpec = tween(220)),
        exit = fadeOut(animationSpec = tween(160)) + shrinkVertically(animationSpec = tween(160)),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WhiteZiaTextField(
                modifier = Modifier.weight(1f),
                label = WhiteZiaL10n.settingWarmupProbes,
                value = settings.trafficWarmupProbeCount,
                onValueChange = {
                    onSettingsChange(settings.copy(trafficWarmupProbeCount = it.filter(Char::isDigit)))
                },
                placeholder = "4",
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    capitalization = KeyboardCapitalization.None,
                ),
            )
            WhiteZiaTextField(
                modifier = Modifier.weight(1f),
                label = WhiteZiaL10n.settingKeepalive,
                value = settings.trafficKeepaliveIntervalSeconds,
                onValueChange = {
                    onSettingsChange(
                        settings.copy(trafficKeepaliveIntervalSeconds = it.filter(Char::isDigit)),
                    )
                },
                placeholder = "5",
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    capitalization = KeyboardCapitalization.None,
                ),
            )
        }
    }
    WhiteZiaDropdownField(
        label = WhiteZiaL10n.settingLogLevel,
        value = settings.logLevel,
        options = WhiteZiaOptions.logLevels,
        onValueChange = { onSettingsChange(settings.copy(logLevel = it)) },
    )
}

private fun advancedProfileSummary(profile: AdvancedSettingsProfile): String {
    return "MTU ${profile.minUploadMtu}-${profile.maxUploadMtu}/${profile.minDownloadMtu}-${profile.maxDownloadMtu}, ${profile.logLevel}"
}

private fun advancedSaveAsInitialName(profile: AdvancedSettingsProfile, customAdvancedLabel: String, copySuffix: String): String {
    return if (profile.id == AdvancedSettingsProfile.DefaultId) {
        customAdvancedLabel
    } else {
        "${profile.name} $copySuffix"
    }
}

private fun AdvancedSettingsProfile.displayName(dirty: Boolean, modifiedSuffix: String): String {
    return if (dirty) {
        "$name $modifiedSuffix"
    } else {
        name
    }
}

@Composable
private fun HomeSelectorSheet(
    visible: Boolean,
    title: String,
    searchPlaceholder: String,
    items: List<HomeSelectorItem>,
    selectedId: String?,
    emptyMessage: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    var query by rememberSaveable(title) { mutableStateOf("") }
    val normalizedQuery = query.trim()
    val filteredItems = remember(items, normalizedQuery) {
        if (normalizedQuery.isBlank()) {
            items
        } else {
            val lowerQuery = normalizedQuery.lowercase(Locale.getDefault())
            items.filter { item ->
                item.title.lowercase(Locale.getDefault()).contains(lowerQuery) ||
                    item.detail.lowercase(Locale.getDefault()).contains(lowerQuery)
            }
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(
            animationSpec = tween(260, easing = FastOutSlowInEasing),
            initialOffsetX = { -it },
        ) + fadeIn(animationSpec = tween(180)),
        exit = slideOutHorizontally(
            animationSpec = tween(220, easing = FastOutSlowInEasing),
            targetOffsetX = { it },
        ) + fadeOut(animationSpec = tween(160)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .whiteZiaPageBackground()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 420.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ProfileIconButton(
                        icon = Icons.Rounded.Close,
                        contentDescription = WhiteZiaL10n.cdCloseSelector,
                        emphasized = false,
                        enabled = true,
                        onClick = onDismiss,
                    )
                    Text(
                        modifier = Modifier.weight(1f),
                        text = title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleLarge.copy(
                            color = WhiteZiaPalette.Ink,
                        ),
                    )
                }
                Spacer(modifier = Modifier.height(WhiteZiaSpacing.lg))
                WhiteZiaTextField(
                    label = WhiteZiaL10n.settingSearch,
                    value = query,
                    onValueChange = { query = it },
                    placeholder = searchPlaceholder,
                )
                Spacer(modifier = Modifier.height(WhiteZiaSpacing.md))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (filteredItems.isEmpty()) {
                        Text(
                            text = emptyMessage,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 11.sp,
                                color = WhiteZiaPalette.Muted,
                            ),
                        )
                    } else {
                        filteredItems.forEach { item ->
                            HomeSelectorSheetRow(
                                item = item,
                                selected = item.id == selectedId,
                                onClick = { onSelect(item.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeSelectorSheetRow(
    item: HomeSelectorItem,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val haptic = rememberHapticFeedback()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) WhiteZiaPalette.AccentSurface else WhiteZiaPalette.Surface)
            .border(
                1.5.dp,
                if (selected) WhiteZiaPalette.Accent.copy(alpha = 0.28f) else WhiteZiaPalette.Border,
                RoundedCornerShape(12.dp),
            )
            .semantics {
                contentDescription = context.getString(R.string.cd_select_profile_item, item.title)
            }
            .clickable {
                haptic.performLight()
                onClick()
            }
            .padding(horizontal = 12.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
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
                text = item.detail,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 10.sp,
                    color = if (selected) WhiteZiaPalette.AccentText else WhiteZiaPalette.Muted,
                    fontWeight = FontWeight.Medium,
                ),
            )
        }
        if (selected) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = WhiteZiaL10n.cdSelected,
                tint = WhiteZiaPalette.AccentText,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun ConnectionSetupCard(
    selectedConnectionProfile: ConnectionProfile,
    selectedResolverProfile: ResolverProfile?,
    resolverCount: Int,
    resolverIssue: String?,
    actionsEnabled: Boolean,
    onAddConnectionClick: () -> Unit,
    onAddResolverProfileClick: () -> Unit,
) {
    val serverRouteMissing = WhiteZiaL10n.serverRouteMissing
    val serverRouteAndKeyMissing = WhiteZiaL10n.setupServerRouteAndKey
    val encryptionKeyMissing = WhiteZiaL10n.setupEncryptionKeyMissing
    val serverRoute = selectedConnectionProfile.customServerDomain.ifBlank { serverRouteMissing }
    val connectionIssue = when {
        selectedConnectionProfile.customServerDomain.isBlank() &&
            selectedConnectionProfile.customServerEncryptionKey.isBlank() -> serverRouteAndKeyMissing
        selectedConnectionProfile.customServerDomain.isBlank() -> serverRouteMissing
        selectedConnectionProfile.customServerEncryptionKey.isBlank() -> encryptionKeyMissing
        else -> null
    }
    val resolverSource = when {
        selectedResolverProfile == null -> WhiteZiaL10n.setupManualResolvers
        selectedResolverProfile.id == ResolverProfile.DefaultId -> WhiteZiaL10n.setupDefaultResolver
        else -> selectedResolverProfile.name
    }
    val resolverDetail = resolverIssue ?: resolverCountLabel(
        resolverCount,
        WhiteZiaL10n.resolverCountOneTemplate,
        WhiteZiaL10n.resolverCountTemplate,
    )

    InfoCard(title = WhiteZiaL10n.setupSectionSetup, compact = true) {
        SetupInfoRow(
            icon = if (connectionIssue == null) Icons.Rounded.Link else Icons.Rounded.WarningAmber,
            iconContentDescription = stringResource(
                if (connectionIssue == null) R.string.cd_icon_link else R.string.cd_icon_warning
            ),
            label = WhiteZiaL10n.sectionConnection,
            value = when {
                selectedConnectionProfile.name.isBlank() -> WhiteZiaL10n.sectionConnection
                selectedConnectionProfile.id == ConnectionProfile.DefaultId -> WhiteZiaL10n.setupDefaultConnection
                else -> selectedConnectionProfile.name
            },
            detail = connectionIssue ?: serverRoute,
            color = if (connectionIssue == null) WhiteZiaPalette.AccentText else WhiteZiaPalette.WarningText,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.5.dp)
                .background(WhiteZiaPalette.Divider),
        )
        SetupInfoRow(
            icon = if (resolverIssue == null) Icons.Rounded.Check else Icons.Rounded.WarningAmber,
            iconContentDescription = stringResource(
                if (resolverIssue == null) R.string.cd_icon_check else R.string.cd_icon_warning
            ),
            label = WhiteZiaL10n.setupResolversLabel,
            value = resolverSource,
            detail = resolverDetail,
            color = if (resolverIssue == null) WhiteZiaPalette.Success else WhiteZiaPalette.WarningText,
        )
        Spacer(modifier = Modifier.height(WhiteZiaSpacing.md))
        SetupActionButton(
            label = WhiteZiaL10n.setupAddConnection,
            supportingText = WhiteZiaL10n.setupAddConnectionSupportingText,
            icon = Icons.Rounded.Add,
            iconContentDescription = stringResource(R.string.cd_icon_add),
            emphasized = true,
            enabled = actionsEnabled,
            onClick = onAddConnectionClick,
        )
        Spacer(modifier = Modifier.height(WhiteZiaSpacing.sm))
        SetupActionButton(
            label = WhiteZiaL10n.setupAddResolver,
            supportingText = WhiteZiaL10n.setupAddResolverSupportingText,
            icon = Icons.Rounded.Add,
            iconContentDescription = stringResource(R.string.cd_icon_add),
            emphasized = false,
            enabled = actionsEnabled,
            onClick = onAddResolverProfileClick,
        )
    }
}

@Composable
private fun SetupInfoRow(
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
private fun SetupActionButton(
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

private fun resolverCountLabel(
    count: Int,
    singular: String,
    plural: String,
): String {
    return "$count ${if (count == 1) singular else plural}"
}

private data class ProfileTopAction(
    val label: String,
    val emphasized: Boolean,
    val enabled: Boolean,
    val onClick: () -> Unit,
)

@Composable
private fun ProfileTopActionGrid(actions: List<ProfileTopAction>) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(WhiteZiaSpacing.sm),
    ) {
        actions.chunked(2).forEach { rowActions ->
            if (rowActions.size == 1) {
                val action = rowActions.first()
                ResolverActionButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp),
                    label = action.label,
                    emphasized = action.emphasized,
                    enabled = action.enabled,
                    onClick = action.onClick,
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    rowActions.forEach { action ->
                        ResolverActionButton(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 44.dp),
                            label = action.label,
                            emphasized = action.emphasized,
                            enabled = action.enabled,
                            onClick = action.onClick,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectionProfilesSettings(
    settings: WhiteZiaSettings,
    activeConnectionProfileId: String?,
    connectionStatus: ConnectionStatus,
    serverTestState: ServerTestState,
    openCreateRequestId: Int,
    onServerTestClick: (String?) -> Unit,
    onSettingsChange: (WhiteZiaSettings) -> Unit,
) {
    val profiles = settings.normalizedConnectionProfiles()
    val selectedProfile = settings.selectedConnectionProfile()
    val customProfiles = profiles.filter { it.serverMode == "custom" }
    val serverTestScores = remember(serverTestState.results) { buildServerTestScores(serverTestState.results) }
    val serverTestResultsById = remember(serverTestState.results) {
        serverTestState.results.associateBy { it.serverId }
    }
    val context = LocalContext.current
    var dialogProfile by remember { mutableStateOf<ConnectionProfile?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var exportProfile by remember { mutableStateOf<ConnectionProfile?>(null) }
    var showExportAllDialog by remember { mutableStateOf(false) }
    var showDeleteDuplicatesDialog by remember { mutableStateOf(false) }
    var deleteProfile by remember { mutableStateOf<ConnectionProfile?>(null) }
    var draggedProfileId by remember { mutableStateOf<String?>(null) }
    var dragStartIndex by remember { mutableStateOf(0) }
    var dragOffsetY by remember { mutableStateOf(0f) }
    var measuredItemHeightPx by remember { mutableStateOf(0) }
    val canManageProfiles = connectionStatus != ConnectionStatus.CONNECTING
    val duplicateProfileCount = settings.duplicateConnectionProfileCount()
    val shareSubjectProfileLabel = WhiteZiaL10n.shareSubjectProfile
    val shareChooserProfileLabel = WhiteZiaL10n.shareChooserProfile
    val importSuccessLabel = WhiteZiaL10n.profileImportSuccess
    val importErrorProfileLabel = WhiteZiaL10n.errorImportProfile
    var importNotice by remember { mutableStateOf<String?>(null) }
    var importNoticeIsError by remember { mutableStateOf(false) }
    val qrScanner = rememberQrProfileImportLauncher(
        onDecoded = { decodedLink ->
            runCatching {
                settings.importStormDnsProfileLinks(decodedLink)
            }.onSuccess { importedSettings ->
                importNoticeIsError = false
                importNotice = importSuccessLabel
                onSettingsChange(importedSettings)
            }.onFailure { error ->
                importNoticeIsError = true
                importNotice = error.message ?: importErrorProfileLabel
            }
        },
        onError = { message ->
            importNoticeIsError = true
            importNotice = message
        },
    )
    val draggedIndex = draggedProfileId?.let { profileId ->
        customProfiles.indexOfFirst { it.id == profileId }.takeIf { it >= 0 }
    }
    val dragTargetIndex = draggedIndex?.let {
        val indexOffset = dragOffsetToProfileIndexOffset(
            offsetY = dragOffsetY,
            itemHeightPx = measuredItemHeightPx.toFloat(),
        )
        (dragStartIndex + indexOffset).coerceIn(0, customProfiles.lastIndex)
    }

    fun clearDragState() {
        draggedProfileId = null
        dragStartIndex = 0
        dragOffsetY = 0f
    }

    fun finishDrag(commit: Boolean) {
        val profileId = draggedProfileId
        val targetIndex = if (profileId != null && customProfiles.isNotEmpty()) {
            val indexOffset = dragOffsetToProfileIndexOffset(
                offsetY = dragOffsetY,
                itemHeightPx = measuredItemHeightPx.toFloat(),
            )
            (dragStartIndex + indexOffset).coerceIn(0, customProfiles.lastIndex)
        } else {
            null
        }
        clearDragState()
        if (commit && profileId != null && targetIndex != null && canManageProfiles) {
            onSettingsChange(settings.moveConnectionProfileToIndex(profileId, targetIndex))
        }
    }

    LaunchedEffect(openCreateRequestId) {
        if (openCreateRequestId > 0 && canManageProfiles) {
            showCreateDialog = true
        }
    }

    ProfileTopActionGrid(
        actions = listOf(
            ProfileTopAction(
                label = WhiteZiaL10n.profileBtnCreate,
                emphasized = true,
                enabled = canManageProfiles,
                onClick = {
                    showCreateDialog = true
                },
            ),
            ProfileTopAction(
                label = WhiteZiaL10n.profileBtnImport,
                emphasized = false,
                enabled = canManageProfiles,
                onClick = {
                    showImportDialog = true
                },
            ),
            ProfileTopAction(
                label = WhiteZiaL10n.profileBtnScanQr,
                emphasized = false,
                enabled = canManageProfiles,
                onClick = qrScanner,
            ),
            ProfileTopAction(
                label = if (serverTestState.isRunning) {
                    WhiteZiaL10n.serverTestRunning
                } else {
                    WhiteZiaL10n.serverTestButton
                },
                emphasized = connectionStatus == ConnectionStatus.CONNECTED && !serverTestState.isRunning,
                enabled = connectionStatus == ConnectionStatus.CONNECTED && !serverTestState.isRunning,
                onClick = { onServerTestClick(null) },
            ),
            ProfileTopAction(
                label = WhiteZiaL10n.profileBtnDeleteDups,
                emphasized = false,
                enabled = canManageProfiles && duplicateProfileCount > 0,
                onClick = {
                    showDeleteDuplicatesDialog = true
                },
            ),
            ProfileTopAction(
                label = WhiteZiaL10n.profileBtnExportAll,
                emphasized = false,
                enabled = customProfiles.any {
                    it.customServerDomain.isNotBlank() && it.customServerEncryptionKey.isNotBlank()
                },
                onClick = {
                    showExportAllDialog = true
                },
            ),
        )
    )
    importNotice?.let { message ->
        Spacer(modifier = Modifier.height(WhiteZiaSpacing.sm))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 10.sp,
                color = if (importNoticeIsError) WhiteZiaPalette.Error else WhiteZiaPalette.Success,
                fontWeight = FontWeight.Medium,
            ),
        )
    }

    SectionDivider()
    GroupLabel(WhiteZiaL10n.groupCustomConnections)
    if (serverTestState.results.isEmpty() && serverTestState.message.isNotBlank()) {
        Text(
            text = serverTestDisplayMessage(serverTestState),
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 10.sp,
                color = WhiteZiaPalette.Muted,
                fontWeight = FontWeight.Medium,
            ),
        )
        Spacer(modifier = Modifier.height(WhiteZiaSpacing.sm))
    }
    if (customProfiles.isEmpty()) {
        Text(
            text = WhiteZiaL10n.customConnectionsEmpty,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 10.sp,
                color = WhiteZiaPalette.Muted,
            ),
        )
        Spacer(modifier = Modifier.height(WhiteZiaSpacing.sm))
    }
    customProfiles.forEachIndexed { index, profile ->
        val isActive = profile.id == activeConnectionProfileId &&
            connectionStatus != ConnectionStatus.DISCONNECTED
        val canEdit = canManageProfiles
        val canDelete = canManageProfiles && !isActive
        val canTest = connectionStatus == ConnectionStatus.CONNECTED &&
            !serverTestState.isRunning &&
            profile.customServerDomain.isNotBlank() &&
            profile.customServerEncryptionKey.isNotBlank()
        val isDragging = profile.id == draggedProfileId
        val serverTestResult = serverTestResultsById[profile.id]
        val targetTranslationY = profileDragTranslationY(
            itemIndex = index,
            draggedIndex = draggedIndex,
            targetIndex = dragTargetIndex,
            itemHeightPx = measuredItemHeightPx.toFloat(),
        )
        val animatedTranslationY by animateFloatAsState(
            targetValue = if (isDragging) 0f else targetTranslationY,
            animationSpec = spring(),
            label = "connectionProfileDragTranslation",
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .zIndex(if (isDragging) 1f else 0f)
                .graphicsLayer {
                    translationY = if (isDragging) dragOffsetY else animatedTranslationY
                    shadowElevation = if (isDragging) 8f else 0f
                    alpha = if (isDragging) 0.96f else 1f
                }
                .onGloballyPositioned { coordinates ->
                    measuredItemHeightPx = coordinates.size.height.takeIf { it > 0 } ?: measuredItemHeightPx
                },
        ) {
            ConnectionProfileRow(
                profile = profile,
                selected = profile.id == selectedProfile.id,
                active = isActive,
                canEdit = canEdit,
                canDelete = canDelete,
                canTest = canTest,
                canDrag = canManageProfiles && customProfiles.size > 1,
                dragging = isDragging,
                serverTestResult = serverTestResult,
                serverTestScore = serverTestResult?.let { serverTestScores[it.serverId] },
                onDragStart = {
                    if (canManageProfiles && customProfiles.size > 1) {
                        draggedProfileId = profile.id
                        dragStartIndex = index
                        dragOffsetY = 0f
                    }
                },
                onDrag = { deltaY ->
                    if (draggedProfileId == profile.id) {
                        dragOffsetY += deltaY
                    }
                },
                onDragEnd = {
                    finishDrag(commit = true)
                },
                onDragCancel = {
                    finishDrag(commit = false)
                },
                onExport = {
                    exportProfile = profile
                },
                onTest = {
                    onServerTestClick(profile.id)
                },
                onEdit = {
                    dialogProfile = profile
                },
                onDelete = {
                    if (canDelete) {
                        deleteProfile = profile
                    }
                },
            )
            Spacer(modifier = Modifier.height(WhiteZiaSpacing.sm))
        }
    }

    if (showCreateDialog) {
        ConnectionProfileDialog(
            profile = null,
            onDismiss = { showCreateDialog = false },
            onSave = { profile ->
                val profileId = "profile-${System.currentTimeMillis()}"
                val nextProfile = profile.copy(id = profileId, serverMode = "custom")
                onSettingsChange(
                    settings
                        .upsertConnectionProfile(nextProfile)
                        .selectConnectionProfile(profileId),
                )
                showCreateDialog = false
            },
        )
    }

    if (showImportDialog) {
        ConnectionProfileImportDialog(
            onDismiss = { showImportDialog = false },
            onImport = { links ->
                runCatching {
                    settings.importStormDnsProfileLinks(links)
                }.onSuccess { importedSettings ->
                    onSettingsChange(importedSettings)
                    showImportDialog = false
                }
            },
        )
    }

    exportProfile?.let { profile ->
        ConnectionProfileExportDialog(
            title = WhiteZiaL10n.profileDialogExportConnection,
            fieldLabel = WhiteZiaL10n.profileFieldProfileLinkSingle,
            linkResult = remember(settings, profile) {
                runCatching { settings.exportStormDnsProfileLink(profile) }
            },
            onDismiss = { exportProfile = null },
            onShare = { link ->
                shareProfileLink(context, link, shareSubjectProfileLabel, shareChooserProfileLabel)
            },
        )
    }

    if (showExportAllDialog) {
        ConnectionProfileExportDialog(
            title = WhiteZiaL10n.profileDialogExportAllConnections,
            fieldLabel = WhiteZiaL10n.profileFieldProfileLinksLabel,
            linkResult = remember(settings, showExportAllDialog) {
                runCatching { settings.exportAllStormDnsProfileLinks() }
            },
            onDismiss = { showExportAllDialog = false },
            onShare = { links ->
                shareProfileLink(context, links, shareSubjectProfileLabel, shareChooserProfileLabel)
            },
        )
    }

    if (showDeleteDuplicatesDialog) {
        DeleteProfileConfirmationDialog(
            title = WhiteZiaL10n.deleteDupsTitle,
            message = if (duplicateProfileCount == 1) {
                WhiteZiaL10n.deleteDupsMessageSingleConnection
            } else {
                WhiteZiaL10n.deleteDupsMessageManyConnection
            },
            confirmLabel = WhiteZiaL10n.dialogDeleteConfirm,
            onDismiss = { showDeleteDuplicatesDialog = false },
            onConfirm = {
                if (canManageProfiles && duplicateProfileCount > 0) {
                    onSettingsChange(settings.deleteDuplicateConnectionProfiles(activeConnectionProfileId))
                }
                showDeleteDuplicatesDialog = false
            },
        )
    }

    dialogProfile?.let { profile ->
        ConnectionProfileDialog(
            profile = profile,
            onDismiss = { dialogProfile = null },
            onSave = { updatedProfile ->
                val nextProfile = updatedProfile.copy(id = profile.id, serverMode = "custom")
                onSettingsChange(
                    settings
                        .upsertConnectionProfile(nextProfile)
                        .selectConnectionProfile(profile.id),
                )
                dialogProfile = null
            },
        )
    }

    deleteProfile?.let { profile ->
        val profileIsActive = profile.id == activeConnectionProfileId &&
            connectionStatus != ConnectionStatus.DISCONNECTED
        DeleteProfileConfirmationDialog(
            title = WhiteZiaL10n.deleteConnectionTitle,
            message = WhiteZiaL10n.deleteConnectionMessageTemplate,
            confirmLabel = WhiteZiaL10n.dialogDeleteConfirm,
            onDismiss = { deleteProfile = null },
            onConfirm = {
                if (canManageProfiles && !profileIsActive) {
                    onSettingsChange(settings.deleteConnectionProfile(profile.id))
                }
                deleteProfile = null
            },
        )
    }
}

@Composable
private fun ResolverProfilesSettings(
    settings: WhiteZiaSettings,
    connectionStatus: ConnectionStatus,
    openCreateRequestId: Int,
    onSettingsChange: (WhiteZiaSettings) -> Unit,
) {
    val profiles = settings.normalizedResolverProfiles()
    val selectedProfile = settings.selectedResolverProfile()
    val context = LocalContext.current
    var dialogProfile by remember { mutableStateOf<ResolverProfile?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showExportAllDialog by remember { mutableStateOf(false) }
    var deleteProfile by remember { mutableStateOf<ResolverProfile?>(null) }
    val canChangeProfiles = connectionStatus != ConnectionStatus.CONNECTING
    val shareChooserResolversLabel = WhiteZiaL10n.shareChooserResolvers
    var draggedProfileId by remember { mutableStateOf<String?>(null) }
    var dragStartIndex by remember { mutableStateOf(0) }
    var dragOffsetY by remember { mutableStateOf(0f) }
    var measuredItemHeightPx by remember { mutableStateOf(0) }
    val draggedIndex = draggedProfileId?.let { profileId ->
        profiles.indexOfFirst { it.id == profileId }.takeIf { it >= 0 }
    }
    val dragTargetIndex = draggedIndex?.let {
        val indexOffset = dragOffsetToProfileIndexOffset(
            offsetY = dragOffsetY,
            itemHeightPx = measuredItemHeightPx.toFloat(),
        )
        (dragStartIndex + indexOffset).coerceIn(0, profiles.lastIndex)
    }

    fun clearDragState() {
        draggedProfileId = null
        dragStartIndex = 0
        dragOffsetY = 0f
    }

    fun finishDrag(commit: Boolean) {
        val profileId = draggedProfileId
        val targetIndex = if (profileId != null && profiles.isNotEmpty()) {
            val indexOffset = dragOffsetToProfileIndexOffset(
                offsetY = dragOffsetY,
                itemHeightPx = measuredItemHeightPx.toFloat(),
            )
            (dragStartIndex + indexOffset).coerceIn(0, profiles.lastIndex)
        } else {
            null
        }
        clearDragState()
        if (commit && profileId != null && targetIndex != null && canChangeProfiles) {
            onSettingsChange(settings.moveResolverProfileToIndex(profileId, targetIndex))
        }
    }

    LaunchedEffect(openCreateRequestId) {
        if (openCreateRequestId > 0 && canChangeProfiles) {
            showCreateDialog = true
        }
    }

    ProfileTopActionGrid(
        actions = listOf(
            ProfileTopAction(
                label = WhiteZiaL10n.profileBtnCreate,
                emphasized = true,
                enabled = canChangeProfiles,
                onClick = { showCreateDialog = true },
            ),
            ProfileTopAction(
                label = WhiteZiaL10n.profileBtnSaveCurrent,
                emphasized = false,
                enabled = canChangeProfiles && settings.resolverText.isNotBlank(),
                onClick = {
                    showCreateDialog = true
                },
            ),
            ProfileTopAction(
                label = WhiteZiaL10n.profileBtnExportAll,
                emphasized = false,
                enabled = profiles.isNotEmpty(),
                onClick = {
                    showExportAllDialog = true
                },
            ),
        )
    )

    SectionDivider()
    if (profiles.isEmpty()) {
        Text(
            text = WhiteZiaL10n.profileNoResolverLists,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 10.sp,
                color = WhiteZiaPalette.Muted,
            ),
        )
        Spacer(modifier = Modifier.height(WhiteZiaSpacing.sm))
    }
    profiles.forEachIndexed { index, profile ->
        val isDefaultProfile = profile.id == ResolverProfile.DefaultId
        val isDragging = profile.id == draggedProfileId
        val targetTranslationY = profileDragTranslationY(
            itemIndex = index,
            draggedIndex = draggedIndex,
            targetIndex = dragTargetIndex,
            itemHeightPx = measuredItemHeightPx.toFloat(),
        )
        val animatedTranslationY by animateFloatAsState(
            targetValue = if (isDragging) 0f else targetTranslationY,
            animationSpec = spring(),
            label = "resolverProfileDragTranslation",
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .zIndex(if (isDragging) 1f else 0f)
                .graphicsLayer {
                    translationY = if (isDragging) dragOffsetY else animatedTranslationY
                    shadowElevation = if (isDragging) 8f else 0f
                    alpha = if (isDragging) 0.96f else 1f
                }
                .onGloballyPositioned { coordinates ->
                    measuredItemHeightPx = coordinates.size.height.takeIf { it > 0 } ?: measuredItemHeightPx
                },
        ) {
            ResolverProfileRow(
                profile = profile,
                selected = profile.id == selectedProfile?.id,
                canUse = canChangeProfiles,
                canEdit = canChangeProfiles && !isDefaultProfile,
                canDelete = canChangeProfiles && !isDefaultProfile,
                canDrag = canChangeProfiles &&
                    !isDefaultProfile &&
                    profiles.count { it.id != ResolverProfile.DefaultId } > 1,
                dragging = isDragging,
                onUse = {
                    if (canChangeProfiles) {
                        onSettingsChange(settings.applyResolverProfileToSelectedConnection(profile.id))
                    }
                },
                onDragStart = {
                    if (canChangeProfiles && !isDefaultProfile && profiles.size > 1) {
                        draggedProfileId = profile.id
                        dragStartIndex = index
                        dragOffsetY = 0f
                    }
                },
                onDrag = { deltaY ->
                    if (draggedProfileId == profile.id) {
                        dragOffsetY += deltaY
                    }
                },
                onDragEnd = {
                    finishDrag(commit = true)
                },
                onDragCancel = {
                    finishDrag(commit = false)
                },
                onEdit = { dialogProfile = profile },
                onDelete = {
                    if (canChangeProfiles && !isDefaultProfile) {
                        deleteProfile = profile
                    }
                },
            )
            Spacer(modifier = Modifier.height(WhiteZiaSpacing.sm))
        }
    }

    if (showCreateDialog) {
        ResolverProfileDialog(
            profile = null,
            initialResolverText = settings.resolverText,
            onDismiss = { showCreateDialog = false },
            onSave = { profile ->
                onSettingsChange(settings.upsertResolverProfile(profile))
                showCreateDialog = false
            },
        )
    }

    if (showExportAllDialog) {
        ResolverProfilesExportDialog(
            settings = settings,
            onDismiss = { showExportAllDialog = false },
            onShare = { exportFile ->
                shareCachedExportFile(
                    context = context,
                    exportFile = exportFile,
                    subject = ResolverProfilesExportFileName,
                    chooserTitle = shareChooserResolversLabel,
                )
            },
        )
    }

    dialogProfile?.let { profile ->
        ResolverProfileDialog(
            profile = profile,
            initialResolverText = profile.resolverText,
            onDismiss = { dialogProfile = null },
            onSave = { updatedProfile ->
                onSettingsChange(settings.upsertResolverProfile(updatedProfile.copy(id = profile.id)))
                dialogProfile = null
            },
        )
    }

    deleteProfile?.let { profile ->
        DeleteProfileConfirmationDialog(
            title = WhiteZiaL10n.deleteResolverTitle,
            message = WhiteZiaL10n.deleteResolverMessageTemplate,
            confirmLabel = WhiteZiaL10n.dialogDeleteConfirm,
            onDismiss = { deleteProfile = null },
            onConfirm = {
                if (canChangeProfiles) {
                    onSettingsChange(settings.deleteResolverProfile(profile.id))
                }
                deleteProfile = null
            },
        )
    }
}

@Composable
private fun SettingProfilesSettings(
    settings: WhiteZiaSettings,
    connectionStatus: ConnectionStatus,
    onSettingsChange: (WhiteZiaSettings) -> Unit,
) {
    val profiles = settings.normalizedAdvancedProfiles()
    val defaultProfile = profiles.first()
    val customProfiles = profiles.filter { it.id != AdvancedSettingsProfile.DefaultId }
    val selectedProfile = settings.selectedAdvancedProfile()
    val context = LocalContext.current
    val shareChooserAdvancedSettingsLabel = WhiteZiaL10n.shareChooserAdvancedSettings
    var showCreateDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var editProfile by remember { mutableStateOf<AdvancedSettingsProfile?>(null) }
    var exportProfile by remember { mutableStateOf<AdvancedSettingsProfile?>(null) }
    var deleteProfile by remember { mutableStateOf<AdvancedSettingsProfile?>(null) }
    var draggedProfileId by remember { mutableStateOf<String?>(null) }
    var dragStartIndex by remember { mutableStateOf(0) }
    var dragOffsetY by remember { mutableStateOf(0f) }
    var measuredItemHeightPx by remember { mutableStateOf(0) }
    val canManageProfiles = connectionStatus == ConnectionStatus.DISCONNECTED
    val draggedIndex = draggedProfileId?.let { profileId ->
        customProfiles.indexOfFirst { it.id == profileId }.takeIf { it >= 0 }
    }
    val dragTargetIndex = draggedIndex?.let {
        val indexOffset = dragOffsetToProfileIndexOffset(
            offsetY = dragOffsetY,
            itemHeightPx = measuredItemHeightPx.toFloat(),
        )
        (dragStartIndex + indexOffset).coerceIn(0, customProfiles.lastIndex)
    }

    fun clearDragState() {
        draggedProfileId = null
        dragStartIndex = 0
        dragOffsetY = 0f
    }

    fun finishDrag(commit: Boolean) {
        val profileId = draggedProfileId
        val targetIndex = if (profileId != null && customProfiles.isNotEmpty()) {
            val indexOffset = dragOffsetToProfileIndexOffset(
                offsetY = dragOffsetY,
                itemHeightPx = measuredItemHeightPx.toFloat(),
            )
            (dragStartIndex + indexOffset).coerceIn(0, customProfiles.lastIndex)
        } else {
            null
        }
        clearDragState()
        if (commit && profileId != null && targetIndex != null && canManageProfiles) {
            onSettingsChange(settings.moveAdvancedProfileToIndex(profileId, targetIndex))
        }
    }

    ProfileTopActionGrid(
        actions = listOf(
            ProfileTopAction(
                label = WhiteZiaL10n.profileBtnCreate,
                emphasized = true,
                enabled = canManageProfiles,
                onClick = { showCreateDialog = true },
            ),
            ProfileTopAction(
                label = WhiteZiaL10n.profileBtnImport,
                emphasized = false,
                enabled = canManageProfiles,
                onClick = { showImportDialog = true },
            ),
        )
    )

    SectionDivider()
    GroupLabel(WhiteZiaL10n.groupDefault)
    SettingProfileRow(
        profile = defaultProfile,
        selected = defaultProfile.id == selectedProfile.id,
        dirty = defaultProfile.id == selectedProfile.id && !settings.matchesAdvancedProfile(defaultProfile),
        canUse = canManageProfiles,
        canEdit = false,
        canDelete = false,
        canDrag = false,
        dragging = false,
        onUse = {
            if (canManageProfiles) {
                onSettingsChange(settings.selectAdvancedProfile(defaultProfile.id))
            }
        },
        onDragStart = {},
        onDrag = {},
        onDragEnd = {},
        onDragCancel = {},
        onExport = { exportProfile = defaultProfile },
        onEdit = {},
        onDelete = {},
    )
    Spacer(modifier = Modifier.height(WhiteZiaSpacing.sm))

    GroupLabel(WhiteZiaL10n.groupCustomSettings)
    if (customProfiles.isEmpty()) {
        Text(
            text = WhiteZiaL10n.profileNoSettingProfiles,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 10.sp,
                color = WhiteZiaPalette.Muted,
            ),
        )
        Spacer(modifier = Modifier.height(WhiteZiaSpacing.sm))
    }
    customProfiles.forEachIndexed { index, profile ->
        val isDragging = profile.id == draggedProfileId
        val targetTranslationY = profileDragTranslationY(
            itemIndex = index,
            draggedIndex = draggedIndex,
            targetIndex = dragTargetIndex,
            itemHeightPx = measuredItemHeightPx.toFloat(),
        )
        val animatedTranslationY by animateFloatAsState(
            targetValue = if (isDragging) 0f else targetTranslationY,
            animationSpec = spring(),
            label = "advancedProfileDragTranslation",
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .zIndex(if (isDragging) 1f else 0f)
                .graphicsLayer {
                    translationY = if (isDragging) dragOffsetY else animatedTranslationY
                    shadowElevation = if (isDragging) 8f else 0f
                    alpha = if (isDragging) 0.96f else 1f
                }
                .onGloballyPositioned { coordinates ->
                    measuredItemHeightPx = coordinates.size.height.takeIf { it > 0 } ?: measuredItemHeightPx
                },
        ) {
            SettingProfileRow(
                profile = profile,
                selected = profile.id == selectedProfile.id,
                dirty = profile.id == selectedProfile.id && !settings.matchesAdvancedProfile(profile),
                canUse = canManageProfiles,
                canEdit = canManageProfiles,
                canDelete = canManageProfiles,
                canDrag = canManageProfiles && customProfiles.size > 1,
                dragging = isDragging,
                onUse = {
                    if (canManageProfiles) {
                        onSettingsChange(settings.selectAdvancedProfile(profile.id))
                    }
                },
                onDragStart = {
                    if (canManageProfiles && customProfiles.size > 1) {
                        draggedProfileId = profile.id
                        dragStartIndex = index
                        dragOffsetY = 0f
                    }
                },
                onDrag = { deltaY ->
                    if (draggedProfileId == profile.id) {
                        dragOffsetY += deltaY
                    }
                },
                onDragEnd = {
                    finishDrag(commit = true)
                },
                onDragCancel = {
                    finishDrag(commit = false)
                },
                onExport = { exportProfile = profile },
                onEdit = { editProfile = profile },
                onDelete = {
                    if (canManageProfiles) {
                        deleteProfile = profile
                    }
                },
            )
            Spacer(modifier = Modifier.height(WhiteZiaSpacing.sm))
        }
    }

    if (showCreateDialog) {
        AdvancedSettingsProfileDialog(
            profile = null,
            initialName = advancedSaveAsInitialName(selectedProfile, WhiteZiaL10n.homeSelectorCustomAdvanced, WhiteZiaL10n.profileNameCopySuffix),
            initialSettings = settings,
            onDismiss = { showCreateDialog = false },
            onSave = { profile ->
                onSettingsChange(settings.upsertAdvancedProfile(profile))
                showCreateDialog = false
            },
        )
    }

    if (showImportDialog) {
        AdvancedSettingsImportDialog(
            onDismiss = { showImportDialog = false },
            onImport = { profileName, toml ->
                runCatching {
                    settings.importAdvancedSettingsProfileFromToml(profileName, toml)
                }.onSuccess { importedSettings ->
                    onSettingsChange(importedSettings)
                    showImportDialog = false
                }
            },
        )
    }

    editProfile?.let { profile ->
        AdvancedSettingsProfileDialog(
            profile = profile,
            initialName = profile.name,
            initialSettings = settings.applyAdvancedProfile(profile),
            onDismiss = { editProfile = null },
            onSave = { updatedProfile ->
                onSettingsChange(settings.upsertAdvancedProfile(updatedProfile.copy(id = profile.id)))
                editProfile = null
            },
        )
    }

    exportProfile?.let { profile ->
        ConnectionProfileExportDialog(
            title = WhiteZiaL10n.profileDialogExportSettings,
            fieldLabel = "advanced_settings.toml",
            placeholder = "advanced_settings.toml",
            showQr = false,
            linkResult = remember(settings, profile) {
                runCatching {
                    StormDnsConfigRenderer.renderAdvancedSettingsToml(settings.applyAdvancedProfile(profile))
                }
            },
            onDismiss = { exportProfile = null },
            onShare = { toml ->
                shareAdvancedSettingsToml(context, toml, shareChooserAdvancedSettingsLabel)
            },
        )
    }

    deleteProfile?.let { profile ->
        DeleteProfileConfirmationDialog(
            title = WhiteZiaL10n.deleteSettingTitle,
            message = WhiteZiaL10n.deleteSettingMessageTemplate,
            confirmLabel = WhiteZiaL10n.dialogDeleteConfirm,
            onDismiss = { deleteProfile = null },
            onConfirm = {
                if (canManageProfiles) {
                    onSettingsChange(settings.deleteAdvancedProfile(profile.id))
                }
                deleteProfile = null
            },
        )
    }
}

@Composable
private fun AdvancedSettingsProfileDialog(
    profile: AdvancedSettingsProfile?,
    initialName: String,
    initialSettings: WhiteZiaSettings,
    onDismiss: () -> Unit,
    onSave: (AdvancedSettingsProfile) -> Unit,
) {
    var name by remember(profile?.id) { mutableStateOf(initialName) }
    var draftSettings by remember(profile?.id) { mutableStateOf(initialSettings) }
    val canSave = name.trim().isNotEmpty()

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
                text = if (profile == null) WhiteZiaL10n.profileDialogCreateSetting else WhiteZiaL10n.profileDialogEditSetting,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    color = WhiteZiaPalette.Ink,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.1.sp,
                ),
            )
            Spacer(modifier = Modifier.height(WhiteZiaSpacing.md))
            WhiteZiaTextField(
                label = WhiteZiaL10n.profileFieldName,
                value = name,
                onValueChange = { name = it },
                placeholder = WhiteZiaL10n.settingProfileFastTunnelPlaceholder,
            )
            Spacer(modifier = Modifier.height(WhiteZiaSpacing.sm))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                AdvancedSettingsFields(
                    settings = draftSettings,
                    showProxySettings = true,
                    onSettingsChange = { draftSettings = it },
                )
            }
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
                        onSave(
                            AdvancedSettingsProfile.fromSettings(
                                settings = draftSettings,
                                id = profile?.id.orEmpty(),
                                name = name.trim(),
                            ),
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun SettingProfileRow(
    profile: AdvancedSettingsProfile,
    selected: Boolean,
    dirty: Boolean,
    canUse: Boolean,
    canEdit: Boolean,
    canDelete: Boolean,
    canDrag: Boolean,
    dragging: Boolean,
    onUse: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onExport: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val summarySuffix = when {
        dirty -> " - ${WhiteZiaL10n.profileStatusModified}"
        selected -> " - ${WhiteZiaL10n.profileStatusSelected}"
        else -> ""
    }
    val displayName = if (profile.id == AdvancedSettingsProfile.DefaultId) WhiteZiaL10n.setupDefaultAdvanced else profile.name
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(11.dp))
            .background(if (selected) WhiteZiaPalette.AccentSurface else WhiteZiaPalette.SurfaceAlt)
            .border(
                1.5.dp,
                if (selected) WhiteZiaPalette.Accent.copy(alpha = 0.18f) else WhiteZiaPalette.Border,
                RoundedCornerShape(11.dp),
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProfileDragHandle(
                enabled = canDrag,
                dragging = dragging,
                onDragStart = onDragStart,
                onDrag = onDrag,
                onDragEnd = onDragEnd,
                onDragCancel = onDragCancel,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.sp,
                        color = WhiteZiaPalette.Ink,
                        fontWeight = FontWeight.Medium,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(WhiteZiaSpacing.xs))
                Text(
                    text = "${advancedProfileSummary(profile)}$summarySuffix",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 10.sp,
                        color = when {
                            dirty -> WhiteZiaPalette.WarningText
                            selected -> WhiteZiaPalette.AccentText
                            else -> WhiteZiaPalette.Muted
                        },
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            ProfileActionsMenu(
                contentDescription = WhiteZiaL10n.settingProfileMenuActions,
                actions = listOf(
                    ProfileMenuAction(
                        label = if (selected) WhiteZiaL10n.profileMenuUseSelected else WhiteZiaL10n.profileMenuUse,
                        icon = Icons.Rounded.Check,
                        contentDescription = WhiteZiaL10n.useSettingProfile,
                        enabled = canUse,
                        onClick = onUse,
                    ),
                    ProfileMenuAction(
                        label = WhiteZiaL10n.profileMenuExport,
                        icon = Icons.Rounded.Link,
                        contentDescription = WhiteZiaL10n.exportSettingProfileAction,
                        enabled = true,
                        onClick = onExport,
                    ),
                    ProfileMenuAction(
                        label = WhiteZiaL10n.profileMenuEdit,
                        icon = Icons.Rounded.Edit,
                        contentDescription = WhiteZiaL10n.editSettingProfileAction,
                        enabled = canEdit,
                        onClick = onEdit,
                    ),
                    ProfileMenuAction(
                        label = WhiteZiaL10n.profileMenuDelete,
                        icon = Icons.Rounded.Delete,
                        contentDescription = WhiteZiaL10n.deleteSettingProfileAction,
                        enabled = canDelete,
                        onClick = onDelete,
                    ),
                ),
            )
        }
    }
}

@Composable
private fun ResolverProfileDialog(
    profile: ResolverProfile?,
    initialResolverText: String,
    onDismiss: () -> Unit,
    onSave: (ResolverProfile) -> Unit,
) {
    val context = LocalContext.current
    var name by remember(profile?.id) { mutableStateOf(profile?.name.orEmpty()) }
    var resolverText by remember(profile?.id) { mutableStateOf(profile?.resolverText ?: initialResolverText) }
    var importError by remember(profile?.id) { mutableStateOf<String?>(null) }
    val resolverValidation = remember(resolverText) { validateResolverText(resolverText) }
    val invalidResolverIpTemplate = WhiteZiaL10n.errorInvalidResolverIpTemplate
    val enterValidResolverIpLabel = WhiteZiaL10n.errorEnterValidResolverIp
    val enterProfileNameToSaveLabel = WhiteZiaL10n.errorEnterProfileNameToSave
    val resolverValidSingularTemplate = WhiteZiaL10n.resolverValidSingularTemplate
    val resolverValidPluralTemplate = WhiteZiaL10n.resolverValidPluralTemplate
    val unableToOpenResolverLabel = WhiteZiaL10n.errorUnableToOpenResolverFile
    val noResolverEntriesLabel = WhiteZiaL10n.errorNoResolverEntries
    val validationMessage = resolverValidationMessage(
        name = name,
        resolverText = resolverText,
        invalidEntries = resolverValidation.invalidEntries,
        validResolverCount = resolverValidation.normalizedResolvers.size,
        invalidResolverIpTemplate = invalidResolverIpTemplate,
        enterValidResolverIpLabel = enterValidResolverIpLabel,
        enterProfileNameToSaveLabel = enterProfileNameToSaveLabel,
        validResolverSingularTemplate = resolverValidSingularTemplate,
        validResolverPluralTemplate = resolverValidPluralTemplate,
    )
    val validationMessageIsError = validationMessage != null && (!resolverValidation.isValid || name.isBlank())
    val errorImportResolverLabel = WhiteZiaL10n.errorImportResolver
    val importResolverFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) {
            return@rememberLauncherForActivityResult
        }
        readResolverTextFromUri(
            context = context,
            uri = uri,
            unableToOpenLabel = unableToOpenResolverLabel,
            invalidResolverIpTemplate = invalidResolverIpTemplate,
            noResolverEntriesLabel = noResolverEntriesLabel,
        )
            .onSuccess { importedResolverText ->
                resolverText = importedResolverText
                importError = null
            }
            .onFailure { error ->
                importError = error.message ?: errorImportResolverLabel
            }
    }
    val canSave = name.trim().isNotEmpty() && resolverValidation.isValid

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
                text = if (profile == null) WhiteZiaL10n.profileDialogCreateResolver else WhiteZiaL10n.profileDialogEditResolver,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    color = WhiteZiaPalette.Ink,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.1.sp,
                ),
            )
            Spacer(modifier = Modifier.height(WhiteZiaSpacing.md))
            WhiteZiaTextField(
                label = WhiteZiaL10n.profileFieldName,
                value = name,
                onValueChange = { name = it },
                placeholder = WhiteZiaL10n.resolverProfileHomeResolversPlaceholder,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CompactActionButton(
                    modifier = Modifier.weight(1f),
                    label = WhiteZiaL10n.profileBtnImportFile,
                    emphasized = false,
                    enabled = true,
                    onClick = {
                        importResolverFileLauncher.launch(ResolverImportMimeTypes)
                    },
                )
                CompactActionButton(
                    modifier = Modifier.weight(1f),
                    label = WhiteZiaL10n.profileBtnClear,
                    emphasized = false,
                    enabled = resolverText.isNotBlank(),
                    onClick = {
                        resolverText = ""
                        importError = null
                    },
                )
            }
            importError?.let { message ->
                Spacer(modifier = Modifier.height(WhiteZiaSpacing.iconSpacing))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 10.sp,
                        color = WhiteZiaPalette.Error,
                    ),
                )
            }
            WhiteZiaTextField(
                label = WhiteZiaL10n.profileFieldResolvers,
                value = resolverText,
                onValueChange = {
                    resolverText = it
                    importError = null
                },
                placeholder = WhiteZiaL10n.resolverFieldPlaceholder,
                singleLine = false,
                minLines = 6,
                maxLines = 10,
            )
            validationMessage?.let { message ->
                Spacer(modifier = Modifier.height(WhiteZiaSpacing.iconSpacing))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 10.sp,
                        color = if (validationMessageIsError) WhiteZiaPalette.Error else WhiteZiaPalette.Muted,
                    ),
                )
            }
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
                        onSave(
                            ResolverProfile(
                                id = profile?.id.orEmpty(),
                                name = name.trim(),
                                resolverText = resolverValidation.normalizedText,
                            ),
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun ResolverProfileRow(
    profile: ResolverProfile,
    selected: Boolean,
    canUse: Boolean,
    canEdit: Boolean,
    canDelete: Boolean,
    canDrag: Boolean,
    dragging: Boolean,
    onUse: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val resolverCount = profile.resolverText
        .let { validateResolverText(it).normalizedResolvers.size }
    val resolverSummary = WhiteZiaL10n.resolverProfileSummary(resolverCount) +
        if (selected) " - ${WhiteZiaL10n.profileStatusSelected}" else ""
    val displayName = if (profile.id == ResolverProfile.DefaultId) WhiteZiaL10n.setupDefaultResolver else profile.name
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(11.dp))
            .background(if (selected) WhiteZiaPalette.AccentSurface else WhiteZiaPalette.SurfaceAlt)
            .border(
                1.5.dp,
                if (selected) WhiteZiaPalette.Accent.copy(alpha = 0.18f) else WhiteZiaPalette.Border,
                RoundedCornerShape(11.dp),
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProfileDragHandle(
                enabled = canDrag,
                dragging = dragging,
                onDragStart = onDragStart,
                onDrag = onDrag,
                onDragEnd = onDragEnd,
                onDragCancel = onDragCancel,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.sp,
                        color = WhiteZiaPalette.Ink,
                        fontWeight = FontWeight.Medium,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(WhiteZiaSpacing.xs))
                Text(
                    text = resolverSummary,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 10.sp,
                        color = if (selected) WhiteZiaPalette.AccentText else WhiteZiaPalette.Muted,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            ProfileActionsMenu(
                contentDescription = WhiteZiaL10n.resolverProfileMenuActions,
                actions = listOf(
                    ProfileMenuAction(
                        label = if (selected) WhiteZiaL10n.profileMenuUseSelected else WhiteZiaL10n.profileMenuUse,
                        icon = Icons.Rounded.Check,
                        contentDescription = WhiteZiaL10n.useResolverProfile,
                        enabled = canUse,
                        onClick = onUse,
                    ),
                    ProfileMenuAction(
                        label = WhiteZiaL10n.profileMenuEdit,
                        icon = Icons.Rounded.Edit,
                        contentDescription = WhiteZiaL10n.editResolverProfileAction,
                        enabled = canEdit,
                        onClick = onEdit,
                    ),
                    ProfileMenuAction(
                        label = WhiteZiaL10n.profileMenuDelete,
                        icon = Icons.Rounded.Delete,
                        contentDescription = WhiteZiaL10n.deleteResolverProfileAction,
                        enabled = canDelete,
                        onClick = onDelete,
                    ),
                ),
            )
        }
    }
}

@Composable
private fun ConnectionProfileImportDialog(
    onDismiss: () -> Unit,
    onImport: (String) -> Result<WhiteZiaSettings>,
) {
    var profileLinks by remember { mutableStateOf("") }
    var importError by remember { mutableStateOf<String?>(null) }
    val canImport = profileLinks.trim().isNotEmpty()
    val errorImportProfileLabel = WhiteZiaL10n.errorImportProfile
    val qrScanner = rememberQrProfileImportLauncher(
        onDecoded = { decodedLink ->
            profileLinks = decodedLink
            importError = null
            onImport(decodedLink)
                .onFailure { error ->
                    importError = error.message ?: errorImportProfileLabel
                }
        },
        onError = { message ->
            importError = message
        },
    )

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
                text = WhiteZiaL10n.profileDialogImportConnection,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    color = WhiteZiaPalette.Ink,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.1.sp,
                ),
            )
            Spacer(modifier = Modifier.height(WhiteZiaSpacing.md))
            WhiteZiaTextField(
                label = WhiteZiaL10n.profileFieldProfileLinks,
                value = profileLinks,
                onValueChange = {
                    profileLinks = it
                    importError = null
                },
                placeholder = "stormdns://...\nstormdns://...",
                singleLine = false,
                minLines = 5,
                maxLines = 9,
            )
            CompactActionButton(
                modifier = Modifier.fillMaxWidth(),
                label = WhiteZiaL10n.profileBtnScanQr,
                emphasized = false,
                enabled = true,
                onClick = qrScanner,
            )
            importError?.let { message ->
                Spacer(modifier = Modifier.height(WhiteZiaSpacing.iconSpacing))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 10.sp,
                        color = WhiteZiaPalette.Error,
                    ),
                )
            }
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
                    label = WhiteZiaL10n.btnImport,
                    emphasized = true,
                    enabled = canImport,
                    onClick = {
                        onImport(profileLinks)
                            .onFailure { error ->
                                importError = error.message ?: errorImportProfileLabel
                            }
                    },
                )
            }
        }
    }
}

@Composable
private fun ConnectionProfileExportDialog(
    title: String,
    fieldLabel: String,
    linkResult: Result<String>,
    onDismiss: () -> Unit,
    onShare: (String) -> Unit,
    placeholder: String = "stormdns://...",
    showQr: Boolean = true,
) {
    val context = LocalContext.current

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
            Spacer(modifier = Modifier.height(WhiteZiaSpacing.md))
            val link = linkResult.getOrNull()
            if (link != null) {
                if (showQr && !link.contains('\n')) {
                    ProfileQrPreview(link = link)
                    Spacer(modifier = Modifier.height(WhiteZiaSpacing.md))
                }
                WhiteZiaTextField(
                    label = fieldLabel,
                    value = link,
                    onValueChange = {},
                    placeholder = placeholder,
                    singleLine = false,
                    minLines = if (link.contains('\n')) 7 else 5,
                    maxLines = 12,
                    rawValue = true,
                )
                Spacer(modifier = Modifier.height(WhiteZiaSpacing.md))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CompactActionButton(
                        modifier = Modifier.weight(1f),
                        label = WhiteZiaL10n.btnClose,
                        emphasized = false,
                        enabled = true,
                        onClick = onDismiss,
                    )
                    CompactActionButton(
                        modifier = Modifier.weight(1f),
                        label = WhiteZiaL10n.btnCopy,
                        emphasized = false,
                        enabled = true,
                        onClick = {
                            copyTextToClipboard(
                                context = context,
                                label = fieldLabel,
                                text = link,
                                sensitive = true,
                            )
                        },
                    )
                    CompactActionButton(
                        modifier = Modifier.weight(1f),
                        label = WhiteZiaL10n.btnShare,
                        emphasized = true,
                        enabled = true,
                        onClick = {
                            onShare(link)
                        },
                    )
                }
            } else {
                Text(
                    text = linkResult.exceptionOrNull()?.message ?: WhiteZiaL10n.errorExportProfile,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 11.sp,
                        color = WhiteZiaPalette.Error,
                    ),
                )
                Spacer(modifier = Modifier.height(WhiteZiaSpacing.md))
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
}

@Composable
private fun ResolverProfilesExportDialog(
    settings: WhiteZiaSettings,
    onDismiss: () -> Unit,
    onShare: (File) -> Unit,
) {
    val context = LocalContext.current
    val errorExportProfileLabel = WhiteZiaL10n.errorExportProfile
    var exportState by remember(settings) { mutableStateOf(ResolverProfilesExportDialogState()) }

    LaunchedEffect(settings) {
        exportState = ResolverProfilesExportDialogState()
        val result = withContext(Dispatchers.IO) {
            runCatching {
                writeResolverProfilesExport(
                    context = context.applicationContext,
                    settings = settings,
                )
            }
        }
        exportState = result.fold(
            onSuccess = { exportResult ->
                ResolverProfilesExportDialogState(
                    saving = false,
                    resolverCount = exportResult.resolverCount,
                    savedLocation = exportResult.savedLocation,
                    shareFile = exportResult.shareFile,
                )
            },
            onFailure = { error ->
                ResolverProfilesExportDialogState(
                    saving = false,
                    errorMessage = error.message ?: errorExportProfileLabel,
                )
            },
        )
    }

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
                text = WhiteZiaL10n.profileDialogExportAllResolvers,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    color = WhiteZiaPalette.Ink,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.1.sp,
                ),
            )
            Spacer(modifier = Modifier.height(WhiteZiaSpacing.md))
            when {
                exportState.saving -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = WhiteZiaPalette.Accent,
                            strokeWidth = 2.dp,
                        )
                        Text(
                            text = WhiteZiaL10n.profileExportSavingFile,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 11.sp,
                                color = WhiteZiaPalette.Muted,
                            ),
                        )
                    }
                    Spacer(modifier = Modifier.height(WhiteZiaSpacing.md))
                    CompactActionButton(
                        modifier = Modifier.fillMaxWidth(),
                        label = WhiteZiaL10n.btnClose,
                        emphasized = false,
                        enabled = true,
                        onClick = onDismiss,
                    )
                }
                exportState.errorMessage != null -> {
                    Text(
                        text = exportState.errorMessage ?: WhiteZiaL10n.errorExportProfile,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 11.sp,
                            color = WhiteZiaPalette.Error,
                        ),
                    )
                    Spacer(modifier = Modifier.height(WhiteZiaSpacing.md))
                    CompactActionButton(
                        modifier = Modifier.fillMaxWidth(),
                        label = WhiteZiaL10n.btnClose,
                        emphasized = true,
                        enabled = true,
                        onClick = onDismiss,
                    )
                }
                else -> {
                    Text(
                        text = WhiteZiaL10n.profileExportResolverTotalTemplate.format(
                            WhiteZiaL10n.resolverProfileSummary(exportState.resolverCount),
                        ),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 12.sp,
                            color = WhiteZiaPalette.Ink,
                            fontWeight = FontWeight.Medium,
                        ),
                    )
                    Spacer(modifier = Modifier.height(WhiteZiaSpacing.sm))
                    Text(
                        text = WhiteZiaL10n.profileExportSavedToTemplate.format(exportState.savedLocation),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 10.sp,
                            color = WhiteZiaPalette.Muted,
                        ),
                    )
                    Spacer(modifier = Modifier.height(WhiteZiaSpacing.md))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CompactActionButton(
                            modifier = Modifier.weight(1f),
                            label = WhiteZiaL10n.btnClose,
                            emphasized = false,
                            enabled = true,
                            onClick = onDismiss,
                        )
                        CompactActionButton(
                            modifier = Modifier.weight(1f),
                            label = WhiteZiaL10n.btnShare,
                            emphasized = true,
                            enabled = exportState.shareFile != null,
                            onClick = {
                                exportState.shareFile?.let(onShare)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileQrPreview(link: String) {
    val qrBitmap = remember(link) {
        runCatching { buildQrBitmap(link, QrBitmapSizePx) }.getOrNull()
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        if (qrBitmap != null) {
            Image(
                bitmap = qrBitmap.asImageBitmap(),
                contentDescription = WhiteZiaL10n.cdProfileQrCode,
                modifier = Modifier
                    .size(210.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White)
                    .border(1.5.dp, WhiteZiaPalette.Border, RoundedCornerShape(12.dp))
                    .padding(10.dp),
            )
        } else {
            Text(
                text = WhiteZiaL10n.profileQrUnavailable,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 10.sp,
                    color = WhiteZiaPalette.Error,
                ),
            )
        }
    }
}

@Composable
private fun rememberQrProfileImportLauncher(
    onDecoded: (String) -> Unit,
    onError: (String) -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val cancelledMessage = WhiteZiaL10n.qrScanCancelled
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val decoded = result.data?.getStringExtra(QrScannerActivity.EXTRA_QR_VALUE)?.trim().orEmpty()
        if (result.resultCode == Activity.RESULT_OK && decoded.isNotEmpty()) {
            onDecoded(decoded)
        } else {
            onError(result.data?.getStringExtra(QrScannerActivity.EXTRA_ERROR) ?: cancelledMessage)
        }
    }
    return remember(launcher, context, cancelledMessage, onDecoded, onError) {
        {
            runCatching {
                launcher.launch(Intent(context, QrScannerActivity::class.java))
            }.onFailure {
                onError(cancelledMessage)
            }
        }
    }
}

private fun buildQrBitmap(
    value: String,
    sizePx: Int,
): Bitmap {
    val hints = mapOf(
        EncodeHintType.CHARACTER_SET to "UTF-8",
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
        EncodeHintType.MARGIN to 2,
    )
    val matrix = QRCodeWriter().encode(value, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
    val pixels = IntArray(matrix.width * matrix.height)
    for (y in 0 until matrix.height) {
        val rowOffset = y * matrix.width
        for (x in 0 until matrix.width) {
            pixels[rowOffset + x] = if (matrix[x, y]) {
                android.graphics.Color.BLACK
            } else {
                android.graphics.Color.WHITE
            }
        }
    }
    return Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888).apply {
        setPixels(pixels, 0, matrix.width, 0, 0, matrix.width, matrix.height)
    }
}

@Composable
private fun ConnectionProfileDialog(
    profile: ConnectionProfile?,
    onDismiss: () -> Unit,
    onSave: (ConnectionProfile) -> Unit,
) {
    var name by remember(profile?.id) { mutableStateOf(profile?.name.orEmpty()) }
    var domain by remember(profile?.id) { mutableStateOf(profile?.customServerDomain.orEmpty()) }
    var encryptionKey by remember(profile?.id) { mutableStateOf(profile?.customServerEncryptionKey.orEmpty()) }
    var encryptionMethod by remember(profile?.id) {
        mutableStateOf(profile?.customServerEncryptionMethod ?: 1)
    }
    val canSave = name.isNotBlank() && domain.isNotBlank() && encryptionKey.isNotBlank()

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
                text = if (profile == null) WhiteZiaL10n.profileDialogCreateConnection else WhiteZiaL10n.profileDialogEditConnection,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    color = WhiteZiaPalette.Ink,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.1.sp,
                ),
            )
            Spacer(modifier = Modifier.height(WhiteZiaSpacing.md))
            WhiteZiaTextField(
                label = WhiteZiaL10n.profileFieldName,
                value = name,
                onValueChange = { name = it },
                placeholder = WhiteZiaL10n.profileMyStormDnsPlaceholder,
            )
            WhiteZiaTextField(
                label = WhiteZiaL10n.profileFieldDomain,
                value = domain,
                onValueChange = { domain = it.trim() },
                placeholder = WhiteZiaL10n.profileDomainPlaceholder,
            )
            WhiteZiaTextField(
                label = WhiteZiaL10n.profileFieldEncryptionKey,
                value = encryptionKey,
                onValueChange = { encryptionKey = it.trim() },
                placeholder = WhiteZiaL10n.profileEncryptionKeyPlaceholder,
            )
            WhiteZiaDropdownField(
                label = WhiteZiaL10n.profileFieldEncryptionMethod,
                value = encryptionMethod,
                options = localizedEncryptionMethods(),
                onValueChange = { encryptionMethod = it },
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
                        onSave(
                            ConnectionProfile(
                                id = profile?.id.orEmpty(),
                                name = name.trim(),
                                serverMode = "custom",
                                customServerDomain = domain.trim().trimEnd('.'),
                                customServerEncryptionKey = encryptionKey.trim(),
                                customServerEncryptionMethod = encryptionMethod,
                                resolverProfileId = profile?.resolverProfileId.orEmpty(),
                                connectionMode = profile?.connectionMode ?: "proxy",
                            ),
                        )
                    },
                )
            }
        }
    }
}

private fun dragOffsetToProfileIndexOffset(
    offsetY: Float,
    itemHeightPx: Float,
): Int {
    if (itemHeightPx <= 0f) {
        return 0
    }
    return when {
        offsetY > 0f -> ((offsetY + itemHeightPx / 2f) / itemHeightPx).toInt()
        offsetY < 0f -> ((offsetY - itemHeightPx / 2f) / itemHeightPx).toInt()
        else -> 0
    }
}

private fun profileDragTranslationY(
    itemIndex: Int,
    draggedIndex: Int?,
    targetIndex: Int?,
    itemHeightPx: Float,
): Float {
    if (draggedIndex == null || targetIndex == null || itemHeightPx <= 0f) {
        return 0f
    }
    return when {
        draggedIndex < targetIndex && itemIndex in (draggedIndex + 1)..targetIndex -> -itemHeightPx
        draggedIndex > targetIndex && itemIndex in targetIndex until draggedIndex -> itemHeightPx
        else -> 0f
    }
}

@Composable
private fun ConnectionProfileRow(
    profile: ConnectionProfile,
    selected: Boolean,
    active: Boolean,
    canEdit: Boolean,
    canDelete: Boolean,
    canTest: Boolean,
    canDrag: Boolean,
    dragging: Boolean,
    serverTestResult: ServerTestResult?,
    serverTestScore: ServerTestScore?,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onExport: () -> Unit,
    onTest: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val domain = profile.customServerDomain.ifBlank { WhiteZiaL10n.profileDomainFallback }
    val activeStatus = WhiteZiaL10n.profileStatusActive
    val selectedStatus = WhiteZiaL10n.profileStatusSelected
    val connectionSummary = when {
        active -> "$domain - $activeStatus"
        selected -> "$domain - $selectedStatus"
        else -> domain
    }
    val displayName = if (profile.id == ConnectionProfile.DefaultId) WhiteZiaL10n.setupDefaultConnection else profile.name
    val testRating = serverTestResult?.let { result ->
        serverTestRatingDisplay(
            result = result,
            score = serverTestScore ?: ServerTestScore(ServerTestScoreBucket.Pending, 0f),
        )
    }
    val rowSurface = when {
        testRating != null -> testRating.surface
        selected -> WhiteZiaPalette.AccentSurface
        else -> WhiteZiaPalette.SurfaceAlt
    }
    val rowBorder = when {
        testRating != null -> testRating.color.copy(alpha = 0.24f)
        selected -> WhiteZiaPalette.Accent.copy(alpha = 0.18f)
        else -> WhiteZiaPalette.Border
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(11.dp))
            .background(rowSurface)
            .border(
                1.5.dp,
                rowBorder,
                RoundedCornerShape(11.dp),
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProfileDragHandle(
                enabled = canDrag,
                dragging = dragging,
                onDragStart = onDragStart,
                onDrag = onDrag,
                onDragEnd = onDragEnd,
                onDragCancel = onDragCancel,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.sp,
                        color = WhiteZiaPalette.Ink,
                        fontWeight = FontWeight.Medium,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(WhiteZiaSpacing.xs))
                Text(
                    text = connectionSummary,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 10.sp,
                        color = when {
                            active -> WhiteZiaPalette.Success
                            selected -> WhiteZiaPalette.AccentText
                            else -> WhiteZiaPalette.Muted
                        },
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            ProfileActionsMenu(
                contentDescription = WhiteZiaL10n.connectionProfileMenuActions,
                actions = listOf(
                    ProfileMenuAction(
                        label = WhiteZiaL10n.serverTestSingleButton,
                        icon = Icons.Rounded.Speed,
                        contentDescription = WhiteZiaL10n.serverTestSingleButton,
                        enabled = canTest,
                        onClick = onTest,
                    ),
                    ProfileMenuAction(
                        label = WhiteZiaL10n.profileMenuExport,
                        icon = Icons.Rounded.Link,
                        contentDescription = WhiteZiaL10n.exportConnectionProfileAction,
                        enabled = profile.customServerDomain.isNotBlank() && profile.customServerEncryptionKey.isNotBlank(),
                        onClick = onExport,
                    ),
                    ProfileMenuAction(
                        label = if (selected) WhiteZiaL10n.profileMenuUseSelected else WhiteZiaL10n.profileMenuEdit,
                        icon = Icons.Rounded.Edit,
                        contentDescription = WhiteZiaL10n.editConnectionProfileAction,
                        enabled = canEdit,
                        onClick = onEdit,
                    ),
                    ProfileMenuAction(
                        label = if (active) WhiteZiaL10n.profileMenuDelete else WhiteZiaL10n.profileMenuDelete,
                        icon = Icons.Rounded.Delete,
                        contentDescription = if (active) WhiteZiaL10n.deleteConnectionProfileBlockedAction else WhiteZiaL10n.deleteConnectionProfileAction,
                        enabled = canDelete,
                        onClick = onDelete,
                    ),
                ),
            )
        }
        if (serverTestResult != null && testRating != null) {
            Spacer(modifier = Modifier.height(WhiteZiaSpacing.sm))
            ServerTestInlineResult(rating = testRating)
        }
    }
}

@Composable
private fun ServerTestInlineResult(rating: ServerTestRatingDisplay) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .background(rating.color.copy(alpha = 0.12f))
            .border(1.5.dp, rating.color.copy(alpha = 0.2f), RoundedCornerShape(9.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        if (rating.loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(15.dp),
                color = rating.color,
                strokeWidth = 2.dp,
            )
        } else {
            Icon(
                imageVector = rating.icon,
                contentDescription = rating.label,
                tint = rating.color,
                modifier = Modifier.size(15.dp),
            )
        }
        Text(
            modifier = Modifier.weight(1f),
            text = WhiteZiaL10n.serverTestTitle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 9.sp,
                color = WhiteZiaPalette.Ink,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
            ),
        )
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(rating.color.copy(alpha = 0.13f))
                .padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(
                imageVector = rating.icon,
                contentDescription = rating.label,
                tint = rating.color,
                modifier = Modifier.size(13.dp),
            )
            Text(
                text = rating.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 8.sp,
                    color = rating.color,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.7.sp,
                ),
            )
        }
    }
}

private data class ProfileMenuAction(
    val label: String,
    val icon: ImageVector,
    val contentDescription: String,
    val enabled: Boolean,
    val onClick: () -> Unit,
)

@Composable
private fun ProfileActionsMenu(
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
private fun DeleteProfileConfirmationDialog(
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
private fun ProfileDragHandle(
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
private fun ProfileIconButton(
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
private fun CompactActionButton(
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

@Composable
private fun MtuSettingsGroup(
    settings: WhiteZiaSettings,
    onSettingsChange: (WhiteZiaSettings) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        WhiteZiaTextField(
            modifier = Modifier.weight(1f),
            label = WhiteZiaL10n.settingMinUpload,
            value = settings.minUploadMtu,
            onValueChange = {
                onSettingsChange(settings.copy(minUploadMtu = it.filter(Char::isDigit)))
            },
            placeholder = "40",
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                capitalization = KeyboardCapitalization.None,
            ),
        )
        WhiteZiaTextField(
            modifier = Modifier.weight(1f),
            label = WhiteZiaL10n.settingMinDownload,
            value = settings.minDownloadMtu,
            onValueChange = {
                onSettingsChange(settings.copy(minDownloadMtu = it.filter(Char::isDigit)))
            },
            placeholder = "300",
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                capitalization = KeyboardCapitalization.None,
            ),
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        WhiteZiaTextField(
            modifier = Modifier.weight(1f),
            label = WhiteZiaL10n.settingMaxUpload,
            value = settings.maxUploadMtu,
            onValueChange = {
                onSettingsChange(settings.copy(maxUploadMtu = it.filter(Char::isDigit)))
            },
            placeholder = "140",
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                capitalization = KeyboardCapitalization.None,
            ),
        )
        WhiteZiaTextField(
            modifier = Modifier.weight(1f),
            label = WhiteZiaL10n.settingMaxDownload,
            value = settings.maxDownloadMtu,
            onValueChange = {
                onSettingsChange(settings.copy(maxDownloadMtu = it.filter(Char::isDigit)))
            },
            placeholder = "3000",
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                capitalization = KeyboardCapitalization.None,
            ),
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        WhiteZiaTextField(
            modifier = Modifier.weight(1f),
            label = WhiteZiaL10n.settingResolverRetries,
            value = settings.mtuTestRetriesResolvers,
            onValueChange = {
                onSettingsChange(settings.copy(mtuTestRetriesResolvers = it.filter(Char::isDigit)))
            },
            placeholder = "3",
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                capitalization = KeyboardCapitalization.None,
            ),
        )
        WhiteZiaTextField(
            modifier = Modifier.weight(1f),
            label = WhiteZiaL10n.settingResolverTimeout,
            value = settings.mtuTestTimeoutResolvers,
            onValueChange = {
                onSettingsChange(settings.copy(mtuTestTimeoutResolvers = filterDecimalInput(it)))
            },
            placeholder = "2.0",
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                capitalization = KeyboardCapitalization.None,
            ),
        )
    }
    MtuParallelismSlider(
        parallelism = settings.mtuTestParallelismResolvers.toMtuParallelismSliderValue(),
        enabled = true,
        onParallelismChange = { parallelism ->
            onSettingsChange(settings.copy(mtuTestParallelismResolvers = parallelism.toString()))
        },
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        WhiteZiaTextField(
            modifier = Modifier.weight(1f),
            label = WhiteZiaL10n.settingLogsRetries,
            value = settings.mtuTestRetriesLogs,
            onValueChange = {
                onSettingsChange(settings.copy(mtuTestRetriesLogs = it.filter(Char::isDigit)))
            },
            placeholder = "5",
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                capitalization = KeyboardCapitalization.None,
            ),
        )
        WhiteZiaTextField(
            modifier = Modifier.weight(1f),
            label = WhiteZiaL10n.settingLogsTimeout,
            value = settings.mtuTestTimeoutLogs,
            onValueChange = {
                onSettingsChange(settings.copy(mtuTestTimeoutLogs = filterDecimalInput(it)))
            },
            placeholder = "2.0",
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                capitalization = KeyboardCapitalization.None,
            ),
        )
    }
    WhiteZiaTextField(
        label = WhiteZiaL10n.settingLogsParallel,
        value = settings.mtuTestParallelismLogs,
        onValueChange = {
            onSettingsChange(settings.copy(mtuTestParallelismLogs = it.filter(Char::isDigit)))
        },
        placeholder = "32",
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            capitalization = KeyboardCapitalization.None,
        ),
    )
}

@Composable
private fun RuntimeWorkersSettingsGroup(
    settings: WhiteZiaSettings,
    onSettingsChange: (WhiteZiaSettings) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        WhiteZiaTextField(
            modifier = Modifier.weight(1f),
            label = WhiteZiaL10n.settingRxTxWorkers,
            value = settings.rxTxWorkers,
            onValueChange = {
                onSettingsChange(settings.copy(rxTxWorkers = it.filter(Char::isDigit)))
            },
            placeholder = "4",
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                capitalization = KeyboardCapitalization.None,
            ),
        )
        WhiteZiaTextField(
            modifier = Modifier.weight(1f),
            label = WhiteZiaL10n.settingProcessWorkers,
            value = settings.tunnelProcessWorkers,
            onValueChange = {
                onSettingsChange(settings.copy(tunnelProcessWorkers = it.filter(Char::isDigit)))
            },
            placeholder = "4",
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                capitalization = KeyboardCapitalization.None,
            ),
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        WhiteZiaTextField(
            modifier = Modifier.weight(1f),
            label = WhiteZiaL10n.settingTunnelPacketTimeout,
            value = settings.tunnelPacketTimeoutSeconds,
            onValueChange = {
                onSettingsChange(settings.copy(tunnelPacketTimeoutSeconds = filterDecimalInput(it)))
            },
            placeholder = "10.0",
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                capitalization = KeyboardCapitalization.None,
            ),
        )
        WhiteZiaTextField(
            modifier = Modifier.weight(1f),
            label = WhiteZiaL10n.settingIdlePoll,
            value = settings.dispatcherIdlePollIntervalSeconds,
            onValueChange = {
                onSettingsChange(settings.copy(dispatcherIdlePollIntervalSeconds = filterDecimalInput(it)))
            },
            placeholder = "0.020",
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                capitalization = KeyboardCapitalization.None,
            ),
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        WhiteZiaTextField(
            modifier = Modifier.weight(1f),
            label = WhiteZiaL10n.settingTxChannel,
            value = settings.txChannelSize,
            onValueChange = {
                onSettingsChange(settings.copy(txChannelSize = it.filter(Char::isDigit)))
            },
            placeholder = "2048",
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                capitalization = KeyboardCapitalization.None,
            ),
        )
        WhiteZiaTextField(
            modifier = Modifier.weight(1f),
            label = WhiteZiaL10n.settingRxChannel,
            value = settings.rxChannelSize,
            onValueChange = {
                onSettingsChange(settings.copy(rxChannelSize = it.filter(Char::isDigit)))
            },
            placeholder = "2048",
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                capitalization = KeyboardCapitalization.None,
            ),
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        WhiteZiaTextField(
            modifier = Modifier.weight(1f),
            label = WhiteZiaL10n.settingUdpPool,
            value = settings.resolverUdpConnectionPoolSize,
            onValueChange = {
                onSettingsChange(settings.copy(resolverUdpConnectionPoolSize = it.filter(Char::isDigit)))
            },
            placeholder = "64",
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                capitalization = KeyboardCapitalization.None,
            ),
        )
        WhiteZiaTextField(
            modifier = Modifier.weight(1f),
            label = WhiteZiaL10n.settingStreamQueue,
            value = settings.streamQueueInitialCapacity,
            onValueChange = {
                onSettingsChange(settings.copy(streamQueueInitialCapacity = it.filter(Char::isDigit)))
            },
            placeholder = "128",
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                capitalization = KeyboardCapitalization.None,
            ),
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        WhiteZiaTextField(
            modifier = Modifier.weight(1f),
            label = WhiteZiaL10n.settingOrphanQueue,
            value = settings.orphanQueueInitialCapacity,
            onValueChange = {
                onSettingsChange(settings.copy(orphanQueueInitialCapacity = it.filter(Char::isDigit)))
            },
            placeholder = "32",
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                capitalization = KeyboardCapitalization.None,
            ),
        )
        WhiteZiaTextField(
            modifier = Modifier.weight(1f),
            label = WhiteZiaL10n.settingDnsFragments,
            value = settings.dnsResponseFragmentStoreCapacity,
            onValueChange = {
                onSettingsChange(settings.copy(dnsResponseFragmentStoreCapacity = it.filter(Char::isDigit)))
            },
            placeholder = "256",
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                capitalization = KeyboardCapitalization.None,
            ),
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        WhiteZiaTextField(
            modifier = Modifier.weight(1f),
            label = WhiteZiaL10n.settingSocksUdpTimeout,
            value = settings.socksUdpAssociateReadTimeoutSeconds,
            onValueChange = {
                onSettingsChange(settings.copy(socksUdpAssociateReadTimeoutSeconds = filterDecimalInput(it)))
            },
            placeholder = "30.0",
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                capitalization = KeyboardCapitalization.None,
            ),
        )
        WhiteZiaTextField(
            modifier = Modifier.weight(1f),
            label = WhiteZiaL10n.settingTerminalRetain,
            value = settings.clientTerminalStreamRetentionSeconds,
            onValueChange = {
                onSettingsChange(settings.copy(clientTerminalStreamRetentionSeconds = filterDecimalInput(it)))
            },
            placeholder = "45.0",
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                capitalization = KeyboardCapitalization.None,
            ),
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        WhiteZiaTextField(
            modifier = Modifier.weight(1f),
            label = WhiteZiaL10n.settingCancelledRetain,
            value = settings.clientCancelledSetupRetentionSeconds,
            onValueChange = {
                onSettingsChange(settings.copy(clientCancelledSetupRetentionSeconds = filterDecimalInput(it)))
            },
            placeholder = "120.0",
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                capitalization = KeyboardCapitalization.None,
            ),
        )
        WhiteZiaTextField(
            modifier = Modifier.weight(1f),
            label = WhiteZiaL10n.settingRetryBase,
            value = settings.sessionInitRetryBaseSeconds,
            onValueChange = {
                onSettingsChange(settings.copy(sessionInitRetryBaseSeconds = filterDecimalInput(it)))
            },
            placeholder = "1.0",
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                capitalization = KeyboardCapitalization.None,
            ),
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        WhiteZiaTextField(
            modifier = Modifier.weight(1f),
            label = WhiteZiaL10n.settingRetryStep,
            value = settings.sessionInitRetryStepSeconds,
            onValueChange = {
                onSettingsChange(settings.copy(sessionInitRetryStepSeconds = filterDecimalInput(it)))
            },
            placeholder = "1.0",
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                capitalization = KeyboardCapitalization.None,
            ),
        )
        WhiteZiaTextField(
            modifier = Modifier.weight(1f),
            label = WhiteZiaL10n.settingRetryLinear,
            value = settings.sessionInitRetryLinearAfter,
            onValueChange = {
                onSettingsChange(settings.copy(sessionInitRetryLinearAfter = it.filter(Char::isDigit)))
            },
            placeholder = "5",
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                capitalization = KeyboardCapitalization.None,
            ),
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        WhiteZiaTextField(
            modifier = Modifier.weight(1f),
            label = WhiteZiaL10n.settingRetryMax,
            value = settings.sessionInitRetryMaxSeconds,
            onValueChange = {
                onSettingsChange(settings.copy(sessionInitRetryMaxSeconds = filterDecimalInput(it)))
            },
            placeholder = "60.0",
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                capitalization = KeyboardCapitalization.None,
            ),
        )
        WhiteZiaTextField(
            modifier = Modifier.weight(1f),
            label = WhiteZiaL10n.settingBusyRetry,
            value = settings.sessionInitBusyRetryIntervalSeconds,
            onValueChange = {
                onSettingsChange(settings.copy(sessionInitBusyRetryIntervalSeconds = filterDecimalInput(it)))
            },
            placeholder = "60.0",
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                capitalization = KeyboardCapitalization.None,
            ),
        )
    }
}

@Composable
private fun HeaderCard(
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
                    .semantics { contentDescription = context.getString(R.string.cd_logo_telegram) }
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
                    .semantics { contentDescription = context.getString(R.string.cd_menu_button) }
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

private fun appVersionName(context: Context): String {
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
    val context = LocalContext.current
    Column {
        FieldLabel(label)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(WhiteZiaPalette.Input)
                .border(2.5.dp, WhiteZiaPalette.Divider, RoundedCornerShape(10.dp))
                .semantics { contentDescription = context.getString(R.string.cd_copy_address, address) }
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

@Composable
private fun NotificationPermissionBanner(onClick: () -> Unit) {
    val context = LocalContext.current
    val haptic = rememberHapticFeedback()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(WhiteZiaPalette.WarningSurface)
            .border(1.5.dp, WhiteZiaPalette.Warning.copy(alpha = 0.26f), RoundedCornerShape(16.dp))
            .padding(14.dp),
    ) {
        Text(
            text = WhiteZiaL10n.bannerNotificationBlockedTitle,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 10.sp,
                color = WhiteZiaPalette.WarningText,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.1.sp,
            ),
        )
        Spacer(modifier = Modifier.height(WhiteZiaSpacing.iconSpacing))
        Text(
            text = WhiteZiaL10n.bannerNotificationBlockedBody,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 11.sp,
                lineHeight = 15.sp,
                color = WhiteZiaPalette.WarningText,
            ),
        )
        Spacer(modifier = Modifier.height(WhiteZiaSpacing.inputSpacing))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(WhiteZiaPalette.Surface)
                .border(1.5.dp, WhiteZiaPalette.Warning.copy(alpha = 0.32f), RoundedCornerShape(10.dp))
                .semantics { contentDescription = context.getString(R.string.cd_enable_vpn_notification) }
                .clickable {
                    haptic.performMedium()
                    onClick()
                }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = WhiteZiaL10n.bannerEnableNotification,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 9.sp,
                    color = WhiteZiaPalette.WarningText,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                ),
            )
        }
    }
}

@Composable
private fun BatteryOptimizationBanner(
    onClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val haptic = rememberHapticFeedback()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(WhiteZiaPalette.WarningSurface)
            .border(1.5.dp, WhiteZiaPalette.Warning.copy(alpha = 0.26f), RoundedCornerShape(16.dp))
            .padding(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = WhiteZiaL10n.bannerBatteryTitle,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 10.sp,
                    color = WhiteZiaPalette.WarningText,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.1.sp,
                ),
            )
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
                    contentDescription = WhiteZiaL10n.cdDismissBatteryWarning,
                    tint = WhiteZiaPalette.WarningText,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(WhiteZiaSpacing.iconSpacing))
        Text(
            text = WhiteZiaL10n.bannerBatteryBody,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 11.sp,
                lineHeight = 15.sp,
                color = WhiteZiaPalette.WarningText,
            ),
        )
        Spacer(modifier = Modifier.height(WhiteZiaSpacing.inputSpacing))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(WhiteZiaPalette.Surface)
                .border(1.5.dp, WhiteZiaPalette.Warning.copy(alpha = 0.32f), RoundedCornerShape(10.dp))
                .semantics { contentDescription = context.getString(R.string.cd_allow_background_vpn) }
                .clickable {
                    haptic.performMedium()
                    onClick()
                }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = WhiteZiaL10n.bannerAllowBackground,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 9.sp,
                    color = WhiteZiaPalette.WarningText,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                ),
            )
        }
    }
}

@Composable
private fun FullVpnPerformanceWarning(onDismiss: () -> Unit) {
    val haptic = rememberHapticFeedback()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(WhiteZiaPalette.WarningSurface)
            .border(1.5.dp, WhiteZiaPalette.Warning.copy(alpha = 0.24f), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = Icons.Rounded.WarningAmber,
            contentDescription = stringResource(R.string.cd_icon_warning),
            tint = WhiteZiaPalette.WarningText,
            modifier = Modifier.size(18.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = WhiteZiaL10n.bannerFullVpnWarningTitle,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 9.sp,
                    color = WhiteZiaPalette.WarningText,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.9.sp,
                ),
            )
            Spacer(modifier = Modifier.height(WhiteZiaSpacing.xs))
            Text(
                text = WhiteZiaL10n.bannerFullVpnWarningBody,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                    color = WhiteZiaPalette.WarningText,
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
                contentDescription = WhiteZiaL10n.cdDismissVpnWarning,
                tint = WhiteZiaPalette.WarningText,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun SplitTunnelSettingsPanel(
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

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(11.dp))
            .semantics {
                contentDescription = context.getString(R.string.cd_split_tunnel_app_toggle, app.label)
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

@Composable
private fun ConnectButton(
    status: ConnectionStatus,
    progressState: ConnectionProgressState,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val haptic = rememberHapticFeedback()
    val ringColor by animateColorAsState(
        targetValue = when (status) {
            ConnectionStatus.DISCONNECTED -> if (enabled) WhiteZiaPalette.Accent else WhiteZiaPalette.Divider
            ConnectionStatus.CONNECTING -> WhiteZiaPalette.AccentPressed
            ConnectionStatus.CONNECTED -> WhiteZiaPalette.Success
        },
        animationSpec = tween(400),
        label = "connectRingColor",
    )
    val iconColor by animateColorAsState(
        targetValue = when (status) {
            ConnectionStatus.DISCONNECTED -> if (enabled) WhiteZiaPalette.Accent else WhiteZiaPalette.Disabled
            ConnectionStatus.CONNECTING -> WhiteZiaPalette.AccentPressed
            ConnectionStatus.CONNECTED -> WhiteZiaPalette.WarningText
        },
        animationSpec = tween(400),
        label = "connectIconColor",
    )
    val buttonScale by animateFloatAsState(
        targetValue = if (status == ConnectionStatus.CONNECTED) 1.03f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 300f),
        label = "connectButtonScale",
    )
    val infiniteTransition = rememberInfiniteTransition(label = "connectButtonMotion")
    val spinAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1_200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "connectSpinAngle",
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "connectPulseAlpha",
    )
    val progressFraction by animateFloatAsState(
        targetValue = when (status) {
            ConnectionStatus.CONNECTING -> progressState.fraction.coerceIn(0.03f, 0.99f)
            ConnectionStatus.CONNECTED -> 1f
            ConnectionStatus.DISCONNECTED -> 0f
        },
        animationSpec = tween(300),
        label = "connectProgressFraction",
    )
    val accentColor = WhiteZiaPalette.Accent
    val borderColor = WhiteZiaPalette.Border
    val successColor = WhiteZiaPalette.Success
    val circleSize = 156.dp
    val outerRingSize = 198.dp
    val connectStr = WhiteZiaL10n.btnConnect
    val connectingStr = WhiteZiaL10n.btnConnecting
    val stopStr = WhiteZiaL10n.btnStop
    val label = when (status) {
        ConnectionStatus.DISCONNECTED -> connectStr
        ConnectionStatus.CONNECTING -> connectingStr
        ConnectionStatus.CONNECTED -> stopStr
    }
    val labelColor = when (status) {
        ConnectionStatus.CONNECTED -> WhiteZiaPalette.WarningText
        ConnectionStatus.DISCONNECTED -> if (enabled) WhiteZiaPalette.Accent else WhiteZiaPalette.Disabled
        else -> WhiteZiaPalette.AccentPressed
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(outerRingSize)
                .scale(buttonScale),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.size(outerRingSize)) {
                val strokeWidth = 3.dp.toPx()
                val radius = (size.minDimension - strokeWidth) / 2f
                val color = if (status == ConnectionStatus.CONNECTING) {
                    accentColor.copy(alpha = pulseAlpha)
                } else {
                    ringColor.copy(alpha = if (status == ConnectionStatus.CONNECTED) 0.30f else 0.15f)
                }
                drawCircle(
                    color = color,
                    radius = radius,
                    style = Stroke(width = strokeWidth),
                )
            }

            Canvas(modifier = Modifier.size(circleSize + 14.dp)) {
                val strokeWidth = 5.dp.toPx()
                val arcSize = Size(
                    width = size.width - strokeWidth,
                    height = size.height - strokeWidth,
                )
                val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)

                when (status) {
                    ConnectionStatus.CONNECTING -> {
                        drawArc(
                            color = borderColor.copy(alpha = 0.65f),
                            startAngle = -90f,
                            sweepAngle = 360f,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = strokeWidth),
                        )
                        drawArc(
                            color = accentColor,
                            startAngle = -90f,
                            sweepAngle = 360f * progressFraction,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(
                                width = strokeWidth,
                                cap = StrokeCap.Round,
                            ),
                        )
                        rotate(spinAngle) {
                            drawArc(
                                color = accentColor.copy(alpha = 0.22f),
                                startAngle = 0f,
                                sweepAngle = 42f,
                                useCenter = false,
                                topLeft = topLeft,
                                size = arcSize,
                                style = Stroke(
                                    width = strokeWidth,
                                    cap = StrokeCap.Round,
                                ),
                            )
                        }
                    }
                    ConnectionStatus.CONNECTED -> {
                        drawArc(
                            color = successColor,
                            startAngle = -90f,
                            sweepAngle = 360f,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(
                                width = strokeWidth,
                                cap = StrokeCap.Round,
                            ),
                        )
                    }
                    ConnectionStatus.DISCONNECTED -> Unit
                }
            }

            Box(
                modifier = Modifier
                    .size(circleSize)
                    .clip(CircleShape)
                    .background(if (enabled) WhiteZiaPalette.Surface else WhiteZiaPalette.SurfaceAlt)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            haptic.performMedium()
                            onClick()
                        },
                    )
                    .semantics(mergeDescendants = true) {},
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = if (status == ConnectionStatus.CONNECTED) {
                            Icons.Rounded.Stop
                        } else {
                            Icons.Rounded.PowerSettingsNew
                        },
                        contentDescription = when (status) {
                            ConnectionStatus.DISCONNECTED -> WhiteZiaL10n.cdConnectButtonDisconnected
                            ConnectionStatus.CONNECTING -> WhiteZiaL10n.cdConnectButtonConnecting
                            ConnectionStatus.CONNECTED -> WhiteZiaL10n.cdConnectButtonConnected
                        },
                        tint = iconColor,
                        modifier = Modifier.size(if (status == ConnectionStatus.CONNECTED) 30.dp else 34.dp),
                    )
                    Spacer(modifier = Modifier.height(WhiteZiaSpacing.iconSpacing))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = labelColor,
                        ),
                    )
                    if (status == ConnectionStatus.CONNECTING) {
                        Spacer(modifier = Modifier.height(WhiteZiaSpacing.xs))
                        Text(
                            text = progressState.label,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Medium,
                                color = WhiteZiaPalette.Muted,
                            ),
                        )
                        Spacer(modifier = Modifier.height(WhiteZiaSpacing.xs))
                        Text(
                            text = "${progressState.percent.coerceIn(0, 99)}%",
                            maxLines = 1,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = WhiteZiaPalette.Accent,
                            ),
                        )
                    }
                }
            }
        }
    }
}

private enum class ResolverRuntimeDialogType {
    ACTIVE,
    VALID,
}

@Composable
private fun ResolverRuntimeSummary(
    resolverState: ResolverRuntimeState,
    progressState: ConnectionProgressState,
    connectionStatus: ConnectionStatus,
    modifier: Modifier = Modifier,
) {
    var selectedDialog by remember { mutableStateOf<ResolverRuntimeDialogType?>(null) }
    val activeResolverCount = resolverState.activeResolvers.size.takeIf { it > 0 }?.toString() ?: WhiteZiaL10n.resolverPending
    val backgroundMtuScanInProgress = connectionStatus == ConnectionStatus.CONNECTED &&
        progressState.phase.lowercase() == "mtu" &&
        progressState.total > 0 &&
        progressState.completed < progressState.total

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ResolverRuntimeValue(
                modifier = Modifier.weight(1f),
                label = WhiteZiaL10n.resolverActiveResolvers,
                value = activeResolverCount,
                onClick = { selectedDialog = ResolverRuntimeDialogType.ACTIVE },
            )
            ResolverRuntimeValue(
                modifier = Modifier.weight(1f),
                label = WhiteZiaL10n.resolverValidResolvers,
                value = resolverState.validResolvers.size.toString(),
                onClick = { selectedDialog = ResolverRuntimeDialogType.VALID },
            )
        }
        AnimatedVisibility(
            visible = backgroundMtuScanInProgress,
            enter = fadeIn(animationSpec = tween(180)) + expandVertically(animationSpec = tween(180)),
            exit = fadeOut(animationSpec = tween(120)) + shrinkVertically(animationSpec = tween(120)),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(WhiteZiaPalette.AccentSurface)
                    .border(1.5.dp, WhiteZiaPalette.Accent.copy(alpha = 0.20f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Enhanced progress indicator with percentage display
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(20.dp)
                ) {
                    if (progressState.total > 0 && progressState.completed > 0) {
                        // Determinate progress when total is known
                        CircularProgressIndicator(
                            progress = { progressState.completed.toFloat() / progressState.total.toFloat() },
                            modifier = Modifier.size(20.dp),
                            color = WhiteZiaPalette.Accent,
                            strokeWidth = 2.5.dp,
                        )
                        // Show percentage inside circle
                        Text(
                            text = "${(progressState.completed * 100 / progressState.total)}%",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 7.sp,
                                color = WhiteZiaPalette.Accent,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    } else {
                        // Indeterminate progress when total is unknown
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = WhiteZiaPalette.Accent,
                            strokeWidth = 2.5.dp,
                        )
                    }
                }
                Text(
                    text = if (progressState.completed > 0) {
                        "${WhiteZiaL10n.backgroundScanningInProgress} ${progressState.completed}/${progressState.total}"
                    } else {
                        WhiteZiaL10n.backgroundScanningInProgress
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 11.sp,
                        color = WhiteZiaPalette.AccentText,
                        fontWeight = FontWeight.Medium,
                    ),
                )
            }
        }
    }

    selectedDialog?.let { dialog ->
        val title = when (dialog) {
            ResolverRuntimeDialogType.ACTIVE -> WhiteZiaL10n.resolverActiveResolvers
            ResolverRuntimeDialogType.VALID -> WhiteZiaL10n.resolverValidResolvers
        }
        val resolvers = when (dialog) {
            ResolverRuntimeDialogType.ACTIVE -> resolverState.activeResolvers
            ResolverRuntimeDialogType.VALID -> resolverState.validResolvers
        }
        ResolverRuntimeDialog(
            title = title,
            resolvers = resolvers,
            onDismiss = { selectedDialog = null },
        )
    }
}

@Composable
private fun ResolverRuntimeValue(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(WhiteZiaPalette.Surface)
            .border(1.5.dp, WhiteZiaPalette.Border, RoundedCornerShape(10.dp))
            .semantics {
                contentDescription = context.getString(R.string.cd_stat_card_detail, label, value)
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        Text(
            text = label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 9.sp,
                color = WhiteZiaPalette.Muted,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp,
            ),
        )
        Spacer(modifier = Modifier.height(WhiteZiaSpacing.xs))
        Text(
            text = value,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 12.sp,
                color = WhiteZiaPalette.Ink,
                fontWeight = FontWeight.SemiBold,
            ),
        )
    }
}

@Composable
private fun ConnectionVerificationSummary(
    verification: ConnectionVerificationState,
    modifier: Modifier = Modifier,
) {
    val statusText = when (verification.status) {
        ConnectionVerificationStatus.Checking -> WhiteZiaL10n.verificationVerifying
        ConnectionVerificationStatus.Verified -> WhiteZiaL10n.verificationVerified
        ConnectionVerificationStatus.Failed -> WhiteZiaL10n.verificationNeedsAttention
        else -> WhiteZiaL10n.verificationPending
    }
    val message = verificationDisplayMessage(verification)
    val color = when (verification.status) {
        ConnectionVerificationStatus.Verified -> WhiteZiaPalette.Success
        ConnectionVerificationStatus.Failed -> WhiteZiaPalette.WarningText
        ConnectionVerificationStatus.Checking -> WhiteZiaPalette.Accent
        else -> WhiteZiaPalette.Muted
    }
    val surface = when (verification.status) {
        ConnectionVerificationStatus.Verified -> WhiteZiaPalette.SuccessSurface
        ConnectionVerificationStatus.Failed -> WhiteZiaPalette.WarningSurface
        ConnectionVerificationStatus.Checking -> WhiteZiaPalette.AccentSurface
        else -> WhiteZiaPalette.Surface
    }
    val icon = when (verification.status) {
        ConnectionVerificationStatus.Verified -> Icons.Rounded.Check
        ConnectionVerificationStatus.Failed -> Icons.Rounded.WarningAmber
        ConnectionVerificationStatus.Checking -> Icons.Rounded.Tune
        else -> Icons.Rounded.Link
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(surface)
            .border(1.5.dp, color.copy(alpha = 0.22f), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = stringResource(
                when (verification.status) {
                    ConnectionVerificationStatus.Verified -> R.string.cd_verification_success
                    ConnectionVerificationStatus.Failed -> R.string.cd_verification_failed
                    ConnectionVerificationStatus.Checking -> R.string.cd_verification_pending
                    else -> R.string.cd_icon_link
                }
            ),
            tint = color,
            modifier = Modifier.size(18.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = statusText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 10.sp,
                    color = color,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.7.sp,
                ),
            )
            Spacer(modifier = Modifier.height(WhiteZiaSpacing.xs))
            Text(
                text = message,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                    color = WhiteZiaPalette.Description,
                    fontWeight = FontWeight.Medium,
                ),
            )
        }
    }
}

@Composable
private fun verificationDisplayMessage(verification: ConnectionVerificationState): String {
    val rawMessage = verification.message
    if (rawMessage.isBlank()) {
        return if (verification.status == ConnectionVerificationStatus.Idle) {
            WhiteZiaL10n.verificationNotRunYet
        } else {
            WhiteZiaL10n.verificationCheckingRoute
        }
    }
    return when (rawMessage) {
        "Connection verified: proxy tunnel can reach the internet" -> WhiteZiaL10n.verificationProxyReachable
        "Connection verified: VPN tunnel can reach the internet" -> WhiteZiaL10n.verificationVpnReachable
        "Connection ready: proxy tunnel is active; outbound probe is still warming up" -> {
            WhiteZiaL10n.verificationProxyWarming
        }
        "Connection ready: VPN tunnel is active; outbound probe is still warming up" -> {
            WhiteZiaL10n.verificationVpnWarming
        }
        "Connection mode changed before verification finished" -> WhiteZiaL10n.verificationModeChanged
        "Connection verification failed: local SOCKS listener is not reachable" -> {
            WhiteZiaL10n.verificationSocksNotReachable
        }
        "Connection verification failed: VPN interface is not active" -> WhiteZiaL10n.verificationVpnInterfaceInactive
        else -> rawMessage
    }
}

@Composable
private fun ResolverRuntimeDialog(
    title: String,
    resolvers: List<String>,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val resolverText = resolvers.joinToString("\n")
    val whiteZiaResolversLabel = WhiteZiaL10n.whiteZiaResolversLabel

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
            Spacer(modifier = Modifier.height(WhiteZiaSpacing.md))
            WhiteZiaTextField(
                label = WhiteZiaL10n.profileFieldResolvers,
                value = resolverText,
                onValueChange = {},
                placeholder = WhiteZiaL10n.noResolversPlaceholder,
                singleLine = false,
                minLines = 6,
                maxLines = 12,
            )
            Spacer(modifier = Modifier.height(WhiteZiaSpacing.md))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CompactActionButton(
                    modifier = Modifier.weight(1f),
                    label = WhiteZiaL10n.btnClose,
                    emphasized = false,
                    enabled = true,
                    onClick = onDismiss,
                )
                CompactActionButton(
                    modifier = Modifier.weight(1f),
                    label = WhiteZiaL10n.btnCopy,
                    emphasized = true,
                    enabled = resolverText.isNotBlank(),
                    onClick = {
                        copyTextToClipboard(
                            context = context,
                            label = whiteZiaResolversLabel,
                            text = resolverText,
                            sensitive = false,
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun LiveSpeedStrip(
    stats: ConnectionStats,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(WhiteZiaPalette.Surface)
            .border(2.dp, WhiteZiaPalette.Border, RoundedCornerShape(18.dp))
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SpeedIndicator(
            icon = Icons.Filled.Download,
            iconContentDescription = stringResource(R.string.cd_icon_download),
            label = WhiteZiaL10n.speedDown,
            value = formatDataSpeed(stats.downloadSpeedBytesPerSecond),
            modifier = Modifier.weight(1f),
        )
        SpeedIndicator(
            icon = Icons.Filled.Upload,
            iconContentDescription = stringResource(R.string.cd_icon_upload),
            label = WhiteZiaL10n.speedUp,
            value = formatDataSpeed(stats.uploadSpeedBytesPerSecond),
            modifier = Modifier.weight(1f),
        )
        SpeedIndicator(
            icon = Icons.Filled.DataUsage,
            iconContentDescription = stringResource(R.string.cd_icon_data_usage),
            label = WhiteZiaL10n.speedTotalUsage,
            value = formatDataSize(stats.totalDataUsageBytes),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun serverTestDisplayMessage(state: ServerTestState): String {
    val completed = state.results.count {
        it.status == ServerTestStatus.Ready || it.status == ServerTestStatus.Failed
    }
    val ready = state.results.count { it.status == ServerTestStatus.Ready }
    val failed = state.results.count { it.status == ServerTestStatus.Failed }
    val total = state.results.size

    return when {
        state.isRunning && total > 0 -> WhiteZiaL10n.serverTestProgressTemplate.format(completed, total)
        total > 0 && completed == total -> WhiteZiaL10n.serverTestSummaryTemplate.format(ready, failed)
        state.message == "Server Test: no saved server profiles are configured" ->
            WhiteZiaL10n.serverTestNoSavedServers
        state.message == "Server Test: no connected resolvers are available" ->
            WhiteZiaL10n.serverTestNoConnectedResolvers
        state.message.startsWith("Server Test failed:") ->
            WhiteZiaL10n.serverTestFailedTemplate.format(state.message.substringAfter(':').trim())
        else -> state.message.ifBlank { WhiteZiaL10n.serverTestIdle }
    }
}

@Composable
private fun serverTestRatingDisplay(
    result: ServerTestResult,
    score: ServerTestScore,
): ServerTestRatingDisplay {
    return when {
        result.status == ServerTestStatus.Failed ->
            ServerTestRatingDisplay(
                label = WhiteZiaL10n.serverTestScoreUnavailable,
                icon = Icons.Rounded.Close,
                color = WhiteZiaPalette.Error,
                surface = WhiteZiaPalette.ErrorSurface,
            )
        score.bucket == ServerTestScoreBucket.Poor ->
            ServerTestRatingDisplay(
                label = WhiteZiaL10n.serverTestScorePoor,
                icon = Icons.Rounded.Close,
                color = WhiteZiaPalette.Error,
                surface = WhiteZiaPalette.ErrorSurface,
            )
        score.bucket == ServerTestScoreBucket.Good -> ServerTestRatingDisplay(
            label = WhiteZiaL10n.serverTestScoreGood,
            icon = Icons.Rounded.Check,
            color = WhiteZiaPalette.Success,
            surface = WhiteZiaPalette.SuccessSurface,
        )
        score.bucket == ServerTestScoreBucket.Fair -> ServerTestRatingDisplay(
            label = WhiteZiaL10n.serverTestScoreFair,
            icon = Icons.Rounded.WarningAmber,
            color = WhiteZiaPalette.Warning,
            surface = WhiteZiaPalette.WarningSurface,
        )
        else -> ServerTestRatingDisplay(
            label = when (result.status) {
                ServerTestStatus.Starting -> WhiteZiaL10n.serverTestStarting
                ServerTestStatus.Measuring -> WhiteZiaL10n.serverTestMeasuring
                else -> WhiteZiaL10n.serverTestPending
            },
            icon = Icons.Rounded.Tune,
            color = WhiteZiaPalette.Warning,
            surface = WhiteZiaPalette.WarningSurface,
            loading = result.status == ServerTestStatus.Starting || result.status == ServerTestStatus.Measuring,
        )
    }
}

private fun buildServerTestScores(results: List<ServerTestResult>): Map<String, ServerTestScore> {
    val measuredResults = results.filter { it.status == ServerTestStatus.Ready }
    val speeds = measuredResults
        .map { it.speedBytesPerSecond }
        .filter { it > 0L }
    val pings = measuredResults
        .mapNotNull { it.pingMillis }
        .filter { it > 0L }

    return results.associate { result ->
        val score = when (result.status) {
            ServerTestStatus.Failed -> ServerTestScore(ServerTestScoreBucket.Poor, 0f)
            ServerTestStatus.Ready -> {
                val metricScores = listOfNotNull(
                    result.speedBytesPerSecond
                        .takeIf { it > 0L }
                        ?.let { percentileScore(value = it, values = speeds, higherIsBetter = true) },
                    result.pingMillis
                        ?.takeIf { it > 0L }
                        ?.let { percentileScore(value = it, values = pings, higherIsBetter = false) },
                )
                val percentile = if (metricScores.isEmpty()) {
                    0f
                } else {
                    metricScores.average().toFloat().coerceIn(0f, 1f)
                }
                ServerTestScore(
                    bucket = serverTestScoreBucket(percentile),
                    percentile = percentile,
                )
            }
            else -> ServerTestScore(ServerTestScoreBucket.Pending, 0f)
        }
        result.serverId to score
    }
}

private fun percentileScore(
    value: Long,
    values: List<Long>,
    higherIsBetter: Boolean,
): Float {
    if (values.isEmpty()) {
        return 0f
    }
    val favorableCount = if (higherIsBetter) {
        values.count { value >= it }
    } else {
        values.count { value <= it }
    }
    return (favorableCount.toFloat() / values.size.toFloat()).coerceIn(0f, 1f)
}

private fun serverTestScoreBucket(percentile: Float): ServerTestScoreBucket {
    return when {
        percentile >= 0.67f -> ServerTestScoreBucket.Good
        percentile >= 0.34f -> ServerTestScoreBucket.Fair
        else -> ServerTestScoreBucket.Poor
    }
}

private data class ServerTestRatingDisplay(
    val label: String,
    val icon: ImageVector,
    val color: Color,
    val surface: Color,
    val loading: Boolean = false,
)

private data class ServerTestScore(
    val bucket: ServerTestScoreBucket,
    val percentile: Float,
)

private enum class ServerTestScoreBucket {
    Good,
    Fair,
    Poor,
    Pending,
}

@Composable
private fun SpeedIndicator(
    icon: ImageVector,
    iconContentDescription: String,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(13.dp))
            .background(WhiteZiaPalette.SuccessSurface)
            .padding(horizontal = 8.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = iconContentDescription,
            tint = WhiteZiaPalette.Success,
            modifier = Modifier.size(17.dp),
        )
        Column(
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 8.sp,
                    letterSpacing = 0.8.sp,
                    color = WhiteZiaPalette.Muted,
                ),
            )
            Text(
                text = value,
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
private fun ConnectionInfoCard(
    connectionProfileName: String,
    resolverProfileName: String,
    settingProfileName: String,
    listenAddress: String,
    httpProxyAddress: String,
    connectionMode: String,
    httpProxyEnabled: Boolean,
    protocol: String,
    socksAuthEnabled: Boolean,
    username: String,
    password: String,
    stats: ConnectionStats,
    autoTuneResults: List<AutoTuneTrialResult>,
    onSaveAutoTuneResult: (AutoTuneTrialResult) -> Unit,
    showProxyDetails: Boolean,
    splitTunnelMode: String,
    splitTunnelPackages: List<String>,
    splitTunnelAppLabels: Map<String, String>,
    canDownloadToml: Boolean,
    onDownloadToml: () -> Unit,
) {
    InfoCard(title = WhiteZiaL10n.infoCardConnection) {
        InfoRow(label = WhiteZiaL10n.infoLabelMode, value = connectionMode)
        if (showProxyDetails) {
            InfoRow(label = WhiteZiaL10n.infoLabelSocks5Proxy, value = listenAddress)
            if (httpProxyEnabled) {
                InfoRow(label = WhiteZiaL10n.infoLabelHttpProxy, value = httpProxyAddress)
            }
            ProtocolRow(protocol = protocol, showDivider = true)
            InfoRow(label = WhiteZiaL10n.infoLabelAuth, value = if (socksAuthEnabled) WhiteZiaL10n.infoLabelAuthOn else WhiteZiaL10n.infoLabelAuthOff)
            if (socksAuthEnabled) {
                InfoRow(label = WhiteZiaL10n.infoLabelUser, value = username)
                InfoRow(label = WhiteZiaL10n.infoLabelPass, value = password)
            }
        } else {
            ProtocolRow(protocol = protocol, showDivider = true)
            InfoRow(
                label = WhiteZiaL10n.infoLabelSplitTunnel,
                value = splitTunnelConnectionSummary(
                    mode = splitTunnelMode,
                    packageNames = splitTunnelPackages,
                    labelsByPackage = splitTunnelAppLabels,
                    allAppsLabel = WhiteZiaL10n.splitTunnelAllApps,
                    noAppsLabel = WhiteZiaL10n.splitTunnelNoApps,
                    onlyPrefix = WhiteZiaL10n.splitTunnelOnlyPrefix,
                    bypassPrefix = WhiteZiaL10n.splitTunnelBypassPrefix,
                ),
            )
        }
        if (autoTuneResults.isNotEmpty()) {
            AutoTuneResultsSection(
                results = autoTuneResults,
                onSaveResult = onSaveAutoTuneResult,
            )
        }
        Spacer(modifier = Modifier.height(WhiteZiaSpacing.sm))
        CompactMetricRow(
            metrics = listOf(
                CompactMetric(
                    icon = Icons.Filled.Apps,
                    iconContentDescription = stringResource(R.string.cd_icon_apps),
                    label = WhiteZiaL10n.infoLabelApps,
                    value = stats.connectedApps.toString(),
                ),
            ),
        )
        Spacer(modifier = Modifier.height(WhiteZiaSpacing.inputSpacing))
        InfoRow(label = WhiteZiaL10n.infoLabelConnectionProfile, value = connectionProfileName)
        InfoRow(label = WhiteZiaL10n.infoLabelResolverProfile, value = resolverProfileName)
        InfoRow(label = WhiteZiaL10n.infoLabelSettingProfile, value = settingProfileName)
        Spacer(modifier = Modifier.height(WhiteZiaSpacing.inputSpacing))
        ResolverActionButton(
            modifier = Modifier.fillMaxWidth(),
            label = WhiteZiaL10n.downloadTomlBtn,
            emphasized = false,
            enabled = canDownloadToml,
            onClick = onDownloadToml,
        )
    }
}

@Composable
private fun AutoTuneResultsSection(
    results: List<AutoTuneTrialResult>,
    onSaveResult: (AutoTuneTrialResult) -> Unit,
) {
    Spacer(modifier = Modifier.height(WhiteZiaSpacing.inputSpacing))
    GroupLabel(WhiteZiaL10n.groupParallelTestResults)
    results.forEachIndexed { index, result ->
        AutoTuneResultRow(
            result = result,
            onSaveResult = onSaveResult,
        )
        if (index < results.lastIndex) {
            Spacer(modifier = Modifier.height(WhiteZiaSpacing.sm))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.5.dp)
                    .background(WhiteZiaPalette.Divider),
            )
            Spacer(modifier = Modifier.height(WhiteZiaSpacing.sm))
        }
    }
}

@Composable
private fun AutoTuneResultRow(
    result: AutoTuneTrialResult,
    onSaveResult: (AutoTuneTrialResult) -> Unit,
) {
    val inProgress = result.isAutoTuneInProgress()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (result.selected) WhiteZiaPalette.AccentSurface else Color.Transparent)
            .border(
                1.5.dp,
                if (result.selected) WhiteZiaPalette.Accent.copy(alpha = 0.18f) else Color.Transparent,
                RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = autoTuneResultLabel(result),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 13.sp,
                    color = WhiteZiaPalette.Ink,
                    fontWeight = FontWeight.Medium,
                ),
            )
            if (inProgress) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = WhiteZiaPalette.Accent,
                    strokeWidth = 2.dp,
                )
            }
        }
        Spacer(modifier = Modifier.height(WhiteZiaSpacing.sm))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            AutoTuneMetricPill(
                metric = autoTuneMtuMetric(result),
                modifier = Modifier.weight(1f),
            )
            AutoTuneMetricPill(
                metric = AutoTuneDisplayMetric(
                    icon = Icons.Rounded.Speed,
                    iconContentDescription = WhiteZiaL10n.cdParallelTestSpeed,
                    label = WhiteZiaL10n.autoTuneSpeedLabel,
                    value = autoTuneSpeedValue(result),
                    color = WhiteZiaPalette.Success,
                    surface = WhiteZiaPalette.SuccessSurface,
                    loading = result.status == "measuring",
                ),
                modifier = Modifier.weight(1f),
            )
            AutoTuneMetricPill(
                metric = AutoTuneDisplayMetric(
                    icon = Icons.Rounded.NetworkPing,
                    iconContentDescription = WhiteZiaL10n.cdParallelTestPing,
                    label = WhiteZiaL10n.autoTunePingLabel,
                    value = result.pingMillis?.let { "${it}ms" } ?: "-",
                    color = WhiteZiaPalette.AccentText,
                    surface = WhiteZiaPalette.AccentSurface,
                    loading = false,
                ),
                modifier = Modifier.weight(1f),
            )
        }
        autoTuneStatusMessage(
            result = result,
            startingLabel = WhiteZiaL10n.autoTuneStartingTest,
            measuringLabel = WhiteZiaL10n.autoTuneMeasuringSpeed,
            failedFallback = WhiteZiaL10n.autoTuneFailedFallback,
            measuredKeyword = WhiteZiaL10n.autoTuneMeasuredKeyword,
        )?.let { message ->
            Spacer(modifier = Modifier.height(WhiteZiaSpacing.sm))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    color = if (result.status == "failed") WhiteZiaPalette.WarningText else WhiteZiaPalette.Muted,
                    fontWeight = if (result.status == "failed") FontWeight.Medium else FontWeight.Normal,
                ),
            )
        }
        Spacer(modifier = Modifier.height(WhiteZiaSpacing.sm))
        CompactActionButton(
            modifier = Modifier.fillMaxWidth(),
            label = WhiteZiaL10n.saveSettingAs,
            emphasized = result.selected,
            enabled = true,
            onClick = { onSaveResult(result) },
        )
    }
}

@Composable
private fun AutoTuneMetricPill(
    metric: AutoTuneDisplayMetric,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(metric.surface)
            .border(1.5.dp, metric.color.copy(alpha = 0.16f), RoundedCornerShape(10.dp))
            .padding(horizontal = 7.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (metric.loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                color = metric.color,
                strokeWidth = 2.dp,
            )
        } else {
            Icon(
                imageVector = metric.icon,
                contentDescription = metric.iconContentDescription,
                tint = metric.color,
                modifier = Modifier.size(14.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = metric.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 7.sp,
                    letterSpacing = 0.5.sp,
                    color = WhiteZiaPalette.Muted,
                ),
            )
            Text(
                text = metric.value,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = WhiteZiaPalette.Ink,
                ),
            )
        }
    }
}

private data class AutoTuneDisplayMetric(
    val icon: ImageVector,
    val iconContentDescription: String,
    val label: String,
    val value: String,
    val color: Color,
    val surface: Color,
    val loading: Boolean = false,
)

@Composable
private fun autoTuneMtuMetric(result: AutoTuneTrialResult): AutoTuneDisplayMetric {
    return when (result.status) {
        "failed" -> AutoTuneDisplayMetric(
            icon = Icons.Rounded.Close,
            iconContentDescription = WhiteZiaL10n.cdAutoTuneMtuFailed,
            label = WhiteZiaL10n.groupMtu,
            value = WhiteZiaL10n.autoTuneMtuFail,
            color = WhiteZiaPalette.Error,
            surface = WhiteZiaPalette.ErrorSurface,
        )
        "listening", "measuring", "ready" -> AutoTuneDisplayMetric(
            icon = Icons.Rounded.Check,
            iconContentDescription = WhiteZiaL10n.cdAutoTuneMtuPassed,
            label = WhiteZiaL10n.groupMtu,
            value = WhiteZiaL10n.autoTuneMtuPass,
            color = WhiteZiaPalette.Success,
            surface = WhiteZiaPalette.SuccessSurface,
        )
        else -> AutoTuneDisplayMetric(
            icon = Icons.Rounded.Tune,
            iconContentDescription = WhiteZiaL10n.cdAutoTuneMtuTesting,
            label = WhiteZiaL10n.groupMtu,
            value = WhiteZiaL10n.autoTuneMtuTest,
            color = WhiteZiaPalette.AccentText,
            surface = WhiteZiaPalette.AccentSurface,
            loading = true,
        )
    }
}

private fun autoTuneResultLabel(result: AutoTuneTrialResult): String {
    return if (result.selected) {
        "${result.label} - Selected"
    } else {
        result.label
    }
}

private fun autoTuneSpeedValue(result: AutoTuneTrialResult): String {
    return if (result.speedBytesPerSecond > 0L) {
        formatDataSpeed(result.speedBytesPerSecond)
    } else {
        "-"
    }
}

private fun autoTuneStatusMessage(
    result: AutoTuneTrialResult,
    startingLabel: String,
    measuringLabel: String,
    failedFallback: String,
    measuredKeyword: String,
): String? {
    return when (result.status) {
        "starting" -> startingLabel
        "listening" -> measuringLabel
        "measuring" -> measuringLabel
        "failed" -> result.message.ifBlank { failedFallback }
        else -> result.message.takeIf { it.isNotBlank() && it != measuredKeyword }
    }
}

private fun AutoTuneTrialResult.isAutoTuneInProgress(): Boolean {
    return status == "pending" || status == "starting" || status == "listening" || status == "measuring"
}

private data class CompactMetric(
    val icon: ImageVector,
    val iconContentDescription: String,
    val label: String,
    val value: String,
)

private data class SplitTunnelAppInfo(
    val packageName: String,
    val label: String,
)

@Composable
private fun CompactMetricRow(
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
private fun ProtocolRow(
    protocol: String,
    showDivider: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = WhiteZiaL10n.infoLabelProtocol,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 12.sp,
                color = WhiteZiaPalette.Muted,
            ),
        )
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(5.dp))
                .background(WhiteZiaPalette.AccentSurface)
                .border(1.5.dp, WhiteZiaPalette.Accent.copy(alpha = 0.15f), RoundedCornerShape(5.dp))
                .padding(horizontal = 10.dp, vertical = 3.dp),
        ) {
            Text(
                text = protocol,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = WhiteZiaPalette.AccentText,
                ),
            )
        }
    }
    if (showDivider) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.5.dp)
                .background(WhiteZiaPalette.Divider),
        )
    }
}

@Composable
private fun InfoCard(
    title: String,
    compact: Boolean = false,
    titleAction: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(if (compact) 12.dp else 14.dp)
    val borderWidth = if (compact) 2.dp else 2.5.dp
    val contentPadding = if (compact) 14.dp else 18.dp
    val titleBottomSpacing = if (compact) 9.dp else 14.dp

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(WhiteZiaPalette.Surface)
            .border(borderWidth, WhiteZiaPalette.Border, shape)
            .padding(contentPadding),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(WhiteZiaSpacing.sm),
        ) {
            Text(
                modifier = Modifier.weight(1f),
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = if (compact) 12.sp else 13.sp,
                    color = WhiteZiaPalette.SectionTitle,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.6.sp,
                ),
            )
            titleAction?.invoke()
        }
        Spacer(modifier = Modifier.height(titleBottomSpacing))
        content()
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
    showDivider: Boolean = true,
    multilineValue: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp),
        horizontalArrangement = if (multilineValue) Arrangement.spacedBy(12.dp) else Arrangement.SpaceBetween,
        verticalAlignment = if (multilineValue) Alignment.Top else Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = if (multilineValue) Modifier.width(72.dp) else Modifier,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 12.sp,
                color = WhiteZiaPalette.Muted,
            ),
        )
        Text(
            text = value,
            modifier = if (multilineValue) Modifier.weight(1f) else Modifier,
            maxLines = if (multilineValue) Int.MAX_VALUE else 1,
            overflow = if (multilineValue) TextOverflow.Clip else TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = WhiteZiaPalette.Ink,
            ),
        )
    }
    if (showDivider) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.5.dp)
                .background(WhiteZiaPalette.Divider),
        )
    }
}

@Composable
private fun ConnectionLogsBlock(
    uiState: WhiteZiaUiState,
    expanded: Boolean = false,
) {
    val logs = uiState.connectionLogs
    val visibleLogs = if (expanded) logs else logs.take(10)
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
                            text = logs.joinToString(separator = "\n"),
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

private fun shareProfileLink(
    context: Context,
    link: String,
    subject: String,
    chooserTitle: String,
) {
    shareTextExport(
        context = context,
        fileName = "whitezia-profile.txt",
        subject = subject,
        chooserTitle = chooserTitle,
        text = link,
    )
}

private fun shareClientConfigToml(
    context: Context,
    toml: String,
    chooserTitle: String,
) {
    shareTextExport(
        context = context,
        fileName = "client_config.toml",
        subject = "client_config.toml",
        chooserTitle = chooserTitle,
        text = toml,
    )
}

private fun shareAdvancedSettingsToml(
    context: Context,
    toml: String,
    chooserTitle: String,
) {
    shareTextExport(
        context = context,
        fileName = "advanced_settings.toml",
        subject = "advanced_settings.toml",
        chooserTitle = chooserTitle,
        text = toml,
    )
}

private fun shareTextExport(
    context: Context,
    fileName: String,
    subject: String,
    chooserTitle: String,
    text: String,
) {
    val exportFile = writeCacheExportFile(
        context = context,
        fileName = fileName,
        text = text,
    )
    shareCachedExportFile(
        context = context,
        exportFile = exportFile,
        subject = subject,
        chooserTitle = chooserTitle,
    )
}

private fun shareCachedExportFile(
    context: Context,
    exportFile: File,
    subject: String,
    chooserTitle: String,
) {
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        exportFile,
    )
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, chooserTitle))
}

private data class ResolverProfilesExportDialogState(
    val saving: Boolean = true,
    val resolverCount: Int = 0,
    val savedLocation: String = "",
    val shareFile: File? = null,
    val errorMessage: String? = null,
)

private data class ResolverProfilesExportResult(
    val resolverCount: Int,
    val savedLocation: String,
    val shareFile: File,
)

private data class DeviceExportFile(
    val fileName: String,
    val savedLocation: String,
)

private fun writeResolverProfilesExport(
    context: Context,
    settings: WhiteZiaSettings,
): ResolverProfilesExportResult {
    val resolverText = settings.exportAllResolverProfilesText()
    val resolverCount = resolverText
        .lineSequence()
        .count { it.isNotBlank() }
    val savedFile = writeDeviceDownloadsExportFile(
        context = context,
        fileName = ResolverProfilesExportFileName,
        text = resolverText,
    )
    val shareFile = writeCacheExportFile(
        context = context,
        fileName = savedFile.fileName,
        text = resolverText,
    )
    return ResolverProfilesExportResult(
        resolverCount = resolverCount,
        savedLocation = savedFile.savedLocation,
        shareFile = shareFile,
    )
}

private fun writeCacheExportFile(
    context: Context,
    fileName: String,
    text: String,
): File {
    val exportDir = File(context.cacheDir, CacheExportDirectory).apply {
        mkdirs()
    }
    cleanOldCacheExports(exportDir)
    val safeName = safeExportFileName(fileName)
    val exportFile = File(exportDir, safeName)
    exportFile.writeText(text, Charsets.UTF_8)
    return exportFile
}

private fun writeDeviceDownloadsExportFile(
    context: Context,
    fileName: String,
    text: String,
): DeviceExportFile {
    val safeName = safeExportFileName(fileName)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, safeName)
            put(MediaStore.Downloads.MIME_TYPE, "text/plain")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("Unable to create export file")
        runCatching {
            resolver.openOutputStream(uri)
                ?.bufferedWriter(Charsets.UTF_8)
                ?.use { writer -> writer.write(text) }
                ?: throw IllegalStateException("Unable to write export file")
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }.getOrElse { error ->
            runCatching { resolver.delete(uri, null, null) }
            throw error
        }
        return DeviceExportFile(
            fileName = safeName,
            savedLocation = "${Environment.DIRECTORY_DOWNLOADS}/$safeName",
        )
    }

    val exportDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        ?: File(context.filesDir, Environment.DIRECTORY_DOWNLOADS)
    exportDir.mkdirs()
    val exportFile = File(exportDir, safeName)
    exportFile.writeText(text, Charsets.UTF_8)
    return DeviceExportFile(
        fileName = safeName,
        savedLocation = exportFile.absolutePath,
    )
}

private fun safeExportFileName(fileName: String): String {
    return fileName
        .replace(Regex("""[^A-Za-z0-9._-]"""), "_")
        .ifBlank { "whitezia-export.txt" }
}

private fun cleanOldCacheExports(exportDir: File) {
    val now = System.currentTimeMillis()
    exportDir.listFiles()?.forEach { file ->
        if (file.isFile && now - file.lastModified() > CacheExportMaxAgeMillis) {
            runCatching { file.delete() }
        }
    }
}

private fun copyTextToClipboard(
    context: Context,
    label: String,
    text: String,
    sensitive: Boolean,
) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    val clip = ClipData.newPlainText(label, text)
    if (sensitive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        clip.description.extras = (clip.description.extras ?: android.os.PersistableBundle()).apply {
            putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
        }
    }
    clipboard.setPrimaryClip(clip)
}

private fun buildDiagnosticsText(
    context: Context,
    uiState: WhiteZiaUiState,
): String {
    val settings = uiState.settings.runtimeConnectionSettings()
    val resolvedSettings = settings.resolve()
    val selectedProfile = settings.selectedConnectionProfile()
    val resolverProfile = settings.selectedResolverProfile()
    val verification = uiState.connectionVerification
    val appVersion = appVersionName(context)

    return buildString {
        appendLine("WhiteZia diagnostics")
        appendLine("App version: ${appVersion.ifBlank { "unknown" }}")
        appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
        appendLine("Status: ${uiState.connectionStatus}")
        appendLine("Mode: ${WhiteZiaOptions.connectionModeLabel(resolvedSettings.connectionMode)}")
        appendLine("Profile: ${selectedProfile.name.ifBlank { selectedProfile.id }}")
        appendLine("Server: ${selectedProfile.customServerDomain.ifBlank { "not configured" }}")
        appendLine("Encryption key: ${selectedProfile.customServerEncryptionKey.ifBlank { "not configured" }}")
        appendLine("Resolver profile: ${resolverProfile?.name ?: "Manual resolvers"}")
        appendLine("Resolvers: ${resolvedSettings.resolverEntries.size}")
        appendLine("Split tunnel: ${WhiteZiaOptions.splitTunnelModeLabel(resolvedSettings.splitTunnelMode)}")
        appendLine("Split tunnel packages: ${resolvedSettings.splitTunnelPackages.size}")
        appendLine("Notifications enabled: ${uiState.notificationsEnabled}")
        appendLine("Battery optimization ignored: ${uiState.batteryOptimizationIgnored}")
        appendLine("Listen IP: ${resolvedSettings.listenIp}")
        appendLine("Listen port: ${resolvedSettings.listenPort}")
        appendLine("HTTP proxy: ${if (resolvedSettings.httpProxyEnabled) resolvedSettings.httpProxyPort else "off"}")
        appendLine("SOCKS auth: ${if (resolvedSettings.socks5Authentication) "on" else "off"}")
        appendLine("Traffic total: ${formatDataSize(uiState.connectionStats.totalDataUsageBytes)}")
        appendLine("Traffic down: ${formatDataSpeed(uiState.connectionStats.downloadSpeedBytesPerSecond)}")
        appendLine("Traffic up: ${formatDataSpeed(uiState.connectionStats.uploadSpeedBytesPerSecond)}")
        appendLine("Connected apps: ${uiState.connectionStats.connectedApps}")
        appendLine("Active resolvers: ${uiState.resolverRuntimeState.activeResolvers.size}")
        appendLine("Valid resolvers: ${uiState.resolverRuntimeState.validResolvers.size}")
        appendLine("Verification: ${verification.status}")
        if (verification.message.isNotBlank()) {
            appendLine("Verification message: ${verification.message}")
        }
        appendLine()
        appendLine("Recent logs:")
        uiState.connectionLogs.forEach { log ->
            appendLine(log)
        }
    }.trimEnd()
}

private fun readResolverTextFromUri(
    context: Context,
    uri: Uri,
    unableToOpenLabel: String,
    invalidResolverIpTemplate: String,
    noResolverEntriesLabel: String,
): Result<String> {
    return runCatching {
        val rawText = readTextFromUri(context, uri, unableToOpenLabel).getOrThrow()
        normalizeImportedResolverText(rawText, invalidResolverIpTemplate, noResolverEntriesLabel)
    }
}

private fun readTextFromUri(
    context: Context,
    uri: Uri,
    errorMessage: String,
): Result<String> {
    return runCatching {
        context.contentResolver.openInputStream(uri)
            ?.bufferedReader()
            ?.use { reader -> reader.readText() }
            ?: throw IllegalArgumentException(errorMessage)
    }
}

private fun normalizeImportedResolverText(
    rawText: String,
    invalidResolverIpTemplate: String,
    noResolverEntriesLabel: String,
): String {
    val validation = validateResolverText(rawText)
    if (validation.invalidEntries.isNotEmpty()) {
        throw IllegalArgumentException(invalidResolverIpTemplate.format(validation.invalidEntries.first()))
    }
    if (validation.normalizedText.isBlank()) {
        throw IllegalArgumentException(noResolverEntriesLabel)
    }
    return validation.normalizedText
}

private fun resolverValidationMessage(
    name: String,
    resolverText: String,
    invalidEntries: List<String>,
    validResolverCount: Int,
    invalidResolverIpTemplate: String,
    enterValidResolverIpLabel: String,
    enterProfileNameToSaveLabel: String,
    validResolverSingularTemplate: String,
    validResolverPluralTemplate: String,
): String? {
    return when {
        resolverText.isBlank() -> null
        invalidEntries.isNotEmpty() -> invalidResolverIpTemplate.format(invalidEntries.first())
        validResolverCount == 0 -> enterValidResolverIpLabel
        name.isBlank() -> enterProfileNameToSaveLabel
        else -> {
            val template = if (validResolverCount == 1) validResolverSingularTemplate else validResolverPluralTemplate
            template.format(validResolverCount)
        }
    }
}

private val ResolverImportMimeTypes = arrayOf(
    "text/*",
    "application/json",
    "application/octet-stream",
)

private val SettingsImportMimeTypes = arrayOf(
    "text/*",
    "application/toml",
    "application/octet-stream",
)

private const val QrBitmapSizePx = 768
private const val WhiteZiaTelegramUrl = "https://t.me/whitezia"

private data class DonationWallet(
    val label: String,
    val address: String,
)

private val DonationWallets = listOf(
    DonationWallet(
        label = "USDT (TON / Jetton)",
        address = "UQCVUC-eZzxNkVVewFp9pz43JKd0XIc55KCdC5gbwxJKiqoL",
    ),
    DonationWallet(
        label = "USDT (TRC20 / TRON)",
        address = "TNvdayQydF8t8bNHMuBctxVdgiaWeNKhmR",
    ),
    DonationWallet(
        label = "USDT (ERC20 / Ethereum)",
        address = "0x87519c886F79d3935b9A45519f821519272D9967",
    ),
    DonationWallet(
        label = "USDT (SPL / Solana)",
        address = "7zKyVVnJRBEiw6vL6vnX1VKUTEkw5QvXu696QV5qLS94",
    ),
)

@Composable
private fun ResolverActionButton(
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
private fun SectionCard(
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
                                    context.getString(R.string.cd_section_collapse, title)
                                } else {
                                    context.getString(R.string.cd_section_expand, title)
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
private fun GroupLabel(text: String) {
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
private fun SectionDivider() {
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
private fun ToggleRow(
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
private fun WhiteZiaTextField(
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
private fun FieldLabel(text: String) {
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
private fun localizedBalancingStrategies(): List<Choice<Int>> = listOf(
    Choice(1, WhiteZiaL10n.balancingStrategyRandom),
    Choice(2, WhiteZiaL10n.balancingStrategyRoundRobin),
    Choice(3, WhiteZiaL10n.balancingStrategyLeastLoss),
    Choice(4, WhiteZiaL10n.balancingStrategyLowestLatency),
)

@Composable
private fun localizedCompressionTypes(): List<Choice<Int>> = listOf(
    Choice(0, WhiteZiaL10n.compressionOff),
    Choice(1, WhiteZiaL10n.compressionZstd),
    Choice(2, WhiteZiaL10n.compressionLz4),
    Choice(3, WhiteZiaL10n.compressionZlib),
)

@Composable
private fun localizedSplitTunnelModes(): List<Choice<String>> = listOf(
    Choice(WhiteZiaOptions.SplitTunnelModeOff, WhiteZiaL10n.splitTunnelAllAppsChoice),
    Choice(WhiteZiaOptions.SplitTunnelModeInclude, WhiteZiaL10n.splitTunnelOnlySelectedChoice),
    Choice(WhiteZiaOptions.SplitTunnelModeExclude, WhiteZiaL10n.splitTunnelBypassSelectedChoice),
)

@Composable
private fun localizedEncryptionMethods(): List<Choice<Int>> = listOf(
    Choice(0, WhiteZiaL10n.encryptionMethodNone),
    Choice(1, WhiteZiaL10n.encryptionMethodXor),
    Choice(2, WhiteZiaL10n.encryptionMethodChacha20),
    Choice(3, WhiteZiaL10n.encryptionMethodAes128),
    Choice(4, WhiteZiaL10n.encryptionMethodAes192),
    Choice(5, WhiteZiaL10n.encryptionMethodAes256),
)

@Composable
private fun <T> WhiteZiaDropdownField(
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

private fun formatDataSpeed(bytesPerSecond: Long): String {
    return "${formatDataSize(bytesPerSecond)}/s"
}

private fun formatDataSize(bytes: Long): String {
    if (bytes <= 0) {
        return "0 B"
    }

    val units = listOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unitIndex = 0
    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex += 1
    }

    if (unitIndex == 0) {
        return "$bytes B"
    }

    val pattern = if (value >= 10.0) "%.0f %s" else "%.1f %s"
    return String.format(Locale.US, pattern, value, units[unitIndex])
}

private fun displayProxyIpAddress(
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
private fun loadSplitTunnelAppOptions(context: Context): List<SplitTunnelAppInfo> {
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

private fun splitTunnelConnectionSummary(
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
@Composable
private fun rememberHapticFeedback(): HapticFeedback {
    val view = LocalView.current
    return remember {
        object : HapticFeedback {
            override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
                when (hapticFeedbackType) {
                    HapticFeedbackType.LongPress -> view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    HapticFeedbackType.TextHandleMove -> view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                }
            }
        }
    }
}

/**
 * Performs a light haptic feedback.
 * Use for: tab switches, toggle interactions, minor actions.
 */
private fun HapticFeedback.performLight() {
    performHapticFeedback(HapticFeedbackType.TextHandleMove)
}

/**
 * Performs a medium haptic feedback.
 * Use for: button clicks, profile actions, important interactions.
 */
private fun HapticFeedback.performMedium() {
    performHapticFeedback(HapticFeedbackType.LongPress)
}

/**
 * Performs a heavy haptic feedback.
 * Use for: drag operations, destructive actions.
 */
private fun HapticFeedback.performHeavy() {
    performHapticFeedback(HapticFeedbackType.LongPress)
}

private fun compactAppLabelSummary(appLabels: List<String>, noAppsLabel: String): String {
    return when (appLabels.size) {
        0 -> noAppsLabel
        1 -> appLabels.first()
        2 -> appLabels.joinToString(", ")
        else -> "${appLabels.take(2).joinToString(", ")} +${appLabels.size - 2}"
    }
}

private fun filterDecimalInput(value: String): String {
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

private const val CacheExportDirectory = "exports"
private const val CacheExportMaxAgeMillis = 60L * 60L * 1_000L
private const val ResolverProfilesExportFileName = "client_resolvers.txt"
