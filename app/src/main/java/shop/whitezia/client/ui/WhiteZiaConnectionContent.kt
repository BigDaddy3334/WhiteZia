package shop.whitezia.client.ui


import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import shop.whitezia.client.model.AdvancedSettingsProfile
import shop.whitezia.client.model.AutoTuneTrialResult
import shop.whitezia.client.model.ConnectionProfile
import shop.whitezia.client.model.ConnectionStatus
import shop.whitezia.client.model.ResolverProfile
import shop.whitezia.client.model.WhiteZiaOptions
import shop.whitezia.client.model.WhiteZiaSettings
import shop.whitezia.client.model.WhiteZiaUiState
import shop.whitezia.client.model.applyResolverProfileToSelectedConnection
import shop.whitezia.client.model.matchesAdvancedProfile
import shop.whitezia.client.model.normalizedAdvancedProfiles
import shop.whitezia.client.model.normalizedConnectionProfiles
import shop.whitezia.client.model.normalizedResolverProfiles
import shop.whitezia.client.model.resolve
import shop.whitezia.client.model.runtimeConnectionSettings
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
import shop.whitezia.client.model.WhiteZiaParallelTest
import shop.whitezia.client.storm.StormDnsConfigRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun Modifier.whiteZiaPageBackground(): Modifier {
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
internal fun ConnectTabContent(
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
    val xrayOnlyEnabled = settings.transportMode == WhiteZiaOptions.TransportXray
    val hasXrayTransport = settings.xrayUri.isNotBlank() &&
        (settings.transportMode == WhiteZiaOptions.TransportAuto || settings.transportMode == WhiteZiaOptions.TransportXray)
    val hasResolvers = resolverValidation.isValid
    val canStartConnection = hasResolvers || hasXrayTransport
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
            if (settings.manualMode) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 420.dp)
                        .padding(horizontal = 20.dp)
                        .padding(top = 12.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(WhiteZiaPalette.SurfaceAlt)
                        .border(1.dp, WhiteZiaPalette.WarningText.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    ToggleRow(
                        label = "Только Xray",
                        enabled = xrayOnlyEnabled,
                        interactiveEnabled = uiState.connectionStatus == ConnectionStatus.DISCONNECTED && settings.xrayUri.isNotBlank(),
                        onToggle = {
                            onSettingsChange(
                                settings.copy(
                                    connectionMode = "vpn",
                                    transportMode = if (settings.transportMode == WhiteZiaOptions.TransportXray) {
                                        WhiteZiaOptions.TransportAuto
                                    } else {
                                        WhiteZiaOptions.TransportXray
                                    },
                                ),
                            )
                        },
                    )
                }
            }

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
                enabled = uiState.connectionStatus != ConnectionStatus.DISCONNECTED || canStartConnection,
                onClick = {
                    if (uiState.connectionStatus == ConnectionStatus.DISCONNECTED && !canStartConnection) {
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
                    !canStartConnection,
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
