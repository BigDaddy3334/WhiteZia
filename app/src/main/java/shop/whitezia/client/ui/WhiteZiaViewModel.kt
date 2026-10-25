package shop.whitezia.client.ui

import shop.whitezia.client.ui.connection.applyCurrentRuntimeUiEvent
import shop.whitezia.client.ui.connection.currentReadyRuntimeSnapshot

import shop.whitezia.client.model.runtimeProgressState

import shop.whitezia.client.diagnostics.redactDiagnostics
import shop.whitezia.client.model.proxyVerification
import shop.whitezia.client.model.ConnectionVerificationCoverage

import android.app.Application
import shop.whitezia.client.runtime.isLiveNetworkRecovery
import shop.whitezia.client.runtime.isLiveRuntimeStarting
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.TrafficStats
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.util.UUID
import shop.whitezia.client.model.ConnectionProfile
import shop.whitezia.client.model.ConnectionProgressState
import shop.whitezia.client.model.ConnectionStats
import shop.whitezia.client.model.ConnectionStatus
import shop.whitezia.client.model.ConnectionVerificationState
import shop.whitezia.client.model.ConnectionVerificationStatus
import shop.whitezia.client.model.ResolverRuntimeState
import shop.whitezia.client.model.ServerTestState
import shop.whitezia.client.model.StormDnsServerProfile
import shop.whitezia.client.model.SubscriptionProfileManager
import shop.whitezia.client.model.SubscriptionConnectionPreparer
import shop.whitezia.client.model.normalizeOperatorCode
import shop.whitezia.client.model.WhiteZiaOptions
import shop.whitezia.client.model.WhiteZiaRuntimeProxy
import shop.whitezia.client.model.WhiteZiaSettings
import shop.whitezia.client.model.selectServerProfile
import shop.whitezia.client.model.updateManualResolverText
import shop.whitezia.client.model.WhiteZiaSettingsStore
import shop.whitezia.client.model.WhiteZiaUiState
import shop.whitezia.client.model.clearActiveSubscriptionProfile
import shop.whitezia.client.model.normalizedConnectionProfiles
import shop.whitezia.client.model.resolve
import shop.whitezia.client.model.runtimeConnectionSettings
import shop.whitezia.client.model.selectedConnectionProfile
import shop.whitezia.client.model.selectedTransportMode
import shop.whitezia.client.model.syncSelectedConnectionProfileFields
import shop.whitezia.client.model.validateResolverText
import shop.whitezia.client.model.withForceDnsTunnel
import shop.whitezia.client.proxy.WhiteZiaProxyEvent
import shop.whitezia.client.proxy.WhiteZiaProxyEvents
import shop.whitezia.client.proxy.WhiteZiaProxyService
import shop.whitezia.client.resolver.ResolverCacheStore
import shop.whitezia.client.resolver.DnsResolverDiscovery
import shop.whitezia.client.resolver.DnsDiscoveryNetwork
import shop.whitezia.client.resolver.isCacheableLocalResolver
import shop.whitezia.client.resolver.registryReportableResolverOrNull
import shop.whitezia.client.resolver.ResolverBenchmarkSchedule
import shop.whitezia.client.runtime.StormDnsTrafficAccounting
import shop.whitezia.client.runtime.WhiteZiaRuntimeState
import shop.whitezia.client.runtime.WhiteZiaRuntimeStateStore
import shop.whitezia.client.runtime.WhiteZiaTrafficWarmup
import shop.whitezia.client.runtime.RuntimeLaunchRequestStore
import shop.whitezia.client.runtime.RuntimeStopGuard
import shop.whitezia.client.runtime.parseStormDnsConnectionProgressLine
import shop.whitezia.client.runtime.parseStormDnsResolverStateLine
import shop.whitezia.client.runtime.parseStormDnsTrafficStatsLine
import shop.whitezia.client.scan.WhiteZiaScanService
import shop.whitezia.client.scan.WhiteZiaScanSettingsStore
import shop.whitezia.client.scan.WhiteZiaScanStateStore
import shop.whitezia.client.scan.WhiteZiaScannerResultStore
import shop.whitezia.client.storm.StormDnsBuiltInPool
import shop.whitezia.client.storm.StormDnsProcessManager
import shop.whitezia.client.vpn.WhiteZiaVpnService
import shop.whitezia.client.vpn.WhiteZiaVpnEvent
import shop.whitezia.client.vpn.WhiteZiaVpnEvents

class WhiteZiaViewModel(
    application: Application,
) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    internal val connectionUiCoordinator by lazy {
        shop.whitezia.client.ui.connection.ConnectionUiCoordinator(
            viewModelScope,
            shop.whitezia.client.ui.connection.WhiteZiaConnectionUiActions(this),
        )
    }
    private val settingsStore = WhiteZiaSettingsStore(appContext)
    private val subscriptionProfiles = SubscriptionProfileManager()
    private val scanSettingsStore = WhiteZiaScanSettingsStore(appContext)
    private val resolverDiscovery = DnsResolverDiscovery()
    private val resolverCacheStore = ResolverCacheStore(appContext)
    private val resolverBenchmarkLaunchCount = resolverCacheStore.launchCount
    private val initialSettings = settingsStore.load()
    private val initialPersistedScanState = WhiteZiaScanStateStore.read(appContext)
    private val initialScanState = initialPersistedScanState
        .recoverIfStale(
            nowMillis = System.currentTimeMillis(),
            staleAfterMillis = StaleScanStateTimeoutMillis,
        )

    var uiState by mutableStateOf(
        WhiteZiaUiState(
            settings = initialSettings,
            serverPool = StormDnsBuiltInPool.profiles,
            networkIpAddress = findDeviceNetworkIpAddress(),
            batteryOptimizationIgnored = isIgnoringBatteryOptimizations(appContext),
            notificationsEnabled = areNotificationsEnabled(appContext),
            scanState = initialScanState,
            scanWorkerCount = scanSettingsStore.loadWorkerCount().toString(),
            scanConnectionProfileId = resolveScanConnectionProfileId(
                settings = initialSettings,
                requestedProfileId = scanSettingsStore.loadConnectionProfileId(),
            ),
        ),
    )
        private set

    private var connectJob: Job? = null
    private var runtimeStopJob: Job? = null
    private var statsJob: Job? = null
    private var runtimeRefreshJob: Job? = null
    private var batteryOptimizationRefreshJob: Job? = null
    private var verificationJob: Job? = null
    private var activeServerProfile: StormDnsServerProfile? = null
    private var activeRuntimeSessionId: String = ""
    private var connectionAttemptStartedAtMillis = 0L
    @Volatile
    private var runtimeRestoreSuppressed = false
    private var activeProxyListenPort: Int = WhiteZiaRuntimeProxy.ListenPortInt
    private var trafficBaseline = TrafficSnapshot.empty()
    private var lastTrafficSnapshot = TrafficSnapshot.empty()
    private val stormDnsTrafficAccounting = StormDnsTrafficAccounting()
    private var lastProgressUiUpdateMillis = 0L
    private var lastResolverUiUpdateMillis = 0L
    private var lastReportedRegistryResolversKey = ""
    private var lastRegistryReportSkipKey = ""
    private var forcedResolverBenchmarkLocalResolvers: List<String> = emptyList()
    private val socksStreamTrackerLock = Any()
    private val socksStreamLastSeenMillis = mutableMapOf<Int, Long>()
    private val proxyEventListener: (WhiteZiaProxyEvent) -> Unit = { event ->
        when (event) {
            is WhiteZiaProxyEvent.Log -> handleRuntimeLog(event.sessionId, event.message)
            is WhiteZiaProxyEvent.Ready -> handleRuntimeReady(event.sessionId, event.message, expectedConnectionMode = "proxy")
            is WhiteZiaProxyEvent.Failed -> handleProxyFailure(event.sessionId, event.message)
        }
    }
    private val vpnEventListener: (WhiteZiaVpnEvent) -> Unit = { event ->
        when (event) {
            is WhiteZiaVpnEvent.Log -> handleRuntimeLog(event.sessionId, event.message)
            is WhiteZiaVpnEvent.Ready -> handleRuntimeReady(event.sessionId, event.message, expectedConnectionMode = "vpn")
            is WhiteZiaVpnEvent.Failed -> handleVpnFailure(event.sessionId, event.message)
        }
    }
    private val proxyBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != WhiteZiaProxyService.BroadcastAction) {
                return
            }
            val message = intent.getStringExtra(WhiteZiaProxyService.BroadcastExtraMessage).orEmpty()
            val sessionId = intent.getStringExtra(WhiteZiaProxyService.BroadcastExtraSessionId).orEmpty()
            when (intent.getStringExtra(WhiteZiaProxyService.BroadcastExtraType)) {
                WhiteZiaProxyService.BroadcastTypeLog -> handleRuntimeLog(sessionId, message)
                WhiteZiaProxyService.BroadcastTypeReady -> handleRuntimeReady(sessionId, message, expectedConnectionMode = "proxy")
                WhiteZiaProxyService.BroadcastTypeFailed -> handleProxyFailure(sessionId, message)
            }
        }
    }
    private val vpnBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != WhiteZiaVpnService.BroadcastAction) {
                return
            }
            val message = intent.getStringExtra(WhiteZiaVpnService.BroadcastExtraMessage).orEmpty()
            val sessionId = intent.getStringExtra(WhiteZiaVpnService.BroadcastExtraSessionId).orEmpty()
            when (intent.getStringExtra(WhiteZiaVpnService.BroadcastExtraType)) {
                WhiteZiaVpnService.BroadcastTypeLog -> handleRuntimeLog(sessionId, message)
                WhiteZiaVpnService.BroadcastTypeReady -> handleRuntimeReady(sessionId, message, expectedConnectionMode = "vpn")
                WhiteZiaVpnService.BroadcastTypeProgress -> handleVpnReconnecting(sessionId, message,
                    intent.getStringExtra(WhiteZiaVpnService.BroadcastExtraPhase).orEmpty())
                WhiteZiaVpnService.BroadcastTypeReconnecting -> handleVpnReconnecting(sessionId, message)
                WhiteZiaVpnService.BroadcastTypeFailed -> handleVpnFailure(sessionId, message)
            }
        }
    }
    private val scanBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != WhiteZiaScanService.BroadcastAction) {
                return
            }
            refreshScanState()
        }
    }

    private val serverTests by lazy {
        ServerTestController(appContext, viewModelScope,
            readState = { uiState }, writeState = { uiState = it }, appendLog = ::appendLog)
    }

    private val scanUi by lazy {
        ScanUiController(appContext, viewModelScope, settingsStore, scanSettingsStore,
            readState = { uiState }, writeState = { uiState = it })
    }

    private val runtimeStopGuard by lazy {
        RuntimeStopGuard(
            readStates = { WhiteZiaRuntimeStateStore.readAll(appContext) },
            readPorts = ::runtimeListenPortsToCheck,
            probePort = ::canConnectToLocalPort,
            stopServices = ::stopAllRuntimeServices,
            log = ::appendLog,
            debug = ::appendDebugLog,
        )
    }

    init {
        if (initialScanState != initialPersistedScanState) {
            WhiteZiaScanStateStore.write(appContext, initialScanState)
        }
        WhiteZiaProxyEvents.addListener(proxyEventListener)
        WhiteZiaVpnEvents.addListener(vpnEventListener)
        registerRuntimeBroadcastReceivers()
        refreshRuntimeConnectionStatus()
        refreshScanState()
    }

    fun updateSettings(settings: WhiteZiaSettings) {
        val activeProfileId = uiState.activeConnectionProfileId
        val previousSettings = uiState.settings.syncSelectedConnectionProfileFields()
        if (
            activeProfileId != null &&
            uiState.connectionStatus != ConnectionStatus.DISCONNECTED &&
            uiState.settings.normalizedConnectionProfiles().any { it.id == activeProfileId } &&
            settings.normalizedConnectionProfiles().none { it.id == activeProfileId }
        ) {
            appendLog("Cannot delete the active connection profile")
            return
        }

        val syncedSettings = applySubscriptionLinkIfNeeded(
            settings = settings,
            previousSettings = previousSettings,
        ).syncSelectedConnectionProfileFields()
        val normalizedSettings = syncedSettings.copy(
            transportMode = syncedSettings.selectedTransportMode(),
            forceDnsTunnel = syncedSettings.manualMode && syncedSettings.forceDnsTunnel,
        )
        val scanConnectionProfileId = resolveScanConnectionProfileId(
            settings = normalizedSettings,
            requestedProfileId = uiState.scanConnectionProfileId,
        )
        if (scanConnectionProfileId != uiState.scanConnectionProfileId) {
            scanSettingsStore.saveConnectionProfileId(scanConnectionProfileId)
        }
        settingsStore.save(normalizedSettings)
        uiState = uiState.copy(
            settings = normalizedSettings,
            networkIpAddress = findDeviceNetworkIpAddress(),
            scanConnectionProfileId = scanConnectionProfileId,
        )
        if (previousSettings.customResolversEnabled && !normalizedSettings.customResolversEnabled) {
            // Custom input is preserved for editing, but never reused as the automatic resolver set.
            val automaticSettings = uiState.settings.updateManualResolverText("")
            settingsStore.save(automaticSettings)
            uiState = uiState.copy(settings = automaticSettings)
            applyCachedResolversForOperator(normalizedSettings.operatorCode)
            applyCachedResolverBenchmarkWinner()
            appendLog("Кастомные resolver'ы отключены: восстановлен автоматический выбор")
            if (uiState.activeTransportMode == WhiteZiaOptions.TransportDns && uiState.settings.resolverText.isBlank()) {
                appendLog("Автоматический набор resolver'ов пуст: останавливаю DNS канал для автопоиска")
                disconnect()
            }
        }
        if (shouldReconfigureActiveVpn(previousSettings, uiState.settings)) {
            reconfigureActiveVpnSettings(uiState.settings)
        }
    }

    fun validateSubscriptionLink(rawLink: String): Result<Unit> = runCatching {
        subscriptionProfiles.importProfile(uiState.settings, rawLink, rememberSubscriptionLink = true)
        Unit
    }

    fun updateSubscriptionLink(rawLink: String): Result<Unit> {
        return runCatching {
            val updatedSettings = subscriptionProfiles.importProfile(
                settings = uiState.settings,
                rawLink = rawLink,
                rememberSubscriptionLink = true,
            )
            settingsStore.save(updatedSettings)
            uiState = uiState.copy(
                settings = updatedSettings,
                networkIpAddress = findDeviceNetworkIpAddress(),
            )
        }.onFailure { error ->
            appendLog("Profile import failed: ${error.message ?: error::class.java.simpleName}")
        }
    }

    fun clearSubscriptionProfile() {
        val clearedSettings = uiState.settings.clearActiveSubscriptionProfile()
        settingsStore.save(clearedSettings)
        uiState = uiState.copy(
            settings = clearedSettings,
            networkIpAddress = findDeviceNetworkIpAddress(),
        )
    }

    private fun applySubscriptionLinkIfNeeded(
        settings: WhiteZiaSettings,
        previousSettings: WhiteZiaSettings,
        force: Boolean = false,
    ): WhiteZiaSettings {
        return runCatching {
            subscriptionProfiles.applySubscriptionIfNeeded(settings, previousSettings, force)
        }.getOrElse { error ->
            appendLog("Profile import failed: ${error.message ?: error::class.java.simpleName}")
            settings.copy(subscriptionLink = settings.subscriptionLink.trim())
        }
    }

    fun updateOperatorCode(operatorCode: String) {
        val normalizedOperatorCode = normalizeOperatorCode(operatorCode)
        val updatedSettings = uiState.settings.copy(operatorCode = normalizedOperatorCode)
        settingsStore.save(updatedSettings)
        uiState = uiState.copy(settings = updatedSettings)
    }

    fun setForceDnsTunnel(enabled: Boolean) {
        val updatedSettings = uiState.settings.withForceDnsTunnel(enabled)
        settingsStore.save(updatedSettings)
        uiState = uiState.copy(settings = updatedSettings)
    }

    fun resetConnectionLog(message: String = "Ready") {
        val cleanMessage = uiState.settings.redactDiagnostics(message).trim().ifBlank { "Ready" }
        val nextLogs = if (cleanMessage == "Ready" || cleanMessage == "Новая попытка подключения") {
            listOf(cleanMessage)
        } else {
            (uiState.connectionLogs + cleanMessage).takeLast(MaxConnectionLogs)
        }
        uiState = uiState.copy(connectionLogs = nextLogs)
    }

    fun appendConnectionLog(message: String) {
        appendLog(message)
    }

    fun applyCachedResolversForOperator(
        operatorCode: String,
        onLog: (String) -> Unit = {},
    ): Boolean {
        val customResolvers = validateResolverText(uiState.settings.customResolverText).normalizedResolvers
        if (uiState.settings.customResolversEnabled && customResolvers.isNotEmpty()) {
            val updatedSettings = uiState.settings.copy(
                resolverText = customResolvers.joinToString(separator = "\n"),
                customResolverText = customResolvers.joinToString(separator = "\n"),
                selectedResolverProfileId = "",
            ).syncSelectedConnectionProfileFields()
            settingsStore.save(updatedSettings)
            uiState = uiState.copy(
                settings = updatedSettings,
                networkIpAddress = findDeviceNetworkIpAddress(),
            )
            onLog("Использую кастомные resolver'ы: ${customResolvers.joinToString()}")
            prewarmStormDnsRuntime(updatedSettings)
            return true
        }
        val selectedOperatorCode = normalizeOperatorCode(operatorCode)
        val cachedResolvers = readCachedResolvers(selectedOperatorCode).take(TargetResolverCount)
        if (cachedResolvers.isEmpty()) {
            onLog("Cache resolver'ов пуст, нужен поиск DNS")
            return false
        }
        val updatedSettings = uiState.settings.copy(
            operatorCode = selectedOperatorCode,
            resolverText = cachedResolvers.joinToString(separator = "\n"),
            selectedResolverProfileId = "",
        ).syncSelectedConnectionProfileFields()
        settingsStore.save(updatedSettings)
        uiState = uiState.copy(
            settings = updatedSettings,
            networkIpAddress = findDeviceNetworkIpAddress(),
        )
        onLog("Resolver'ы взяты из cache: ${cachedResolvers.joinToString()}")
        prewarmStormDnsRuntime(updatedSettings)
        return true
    }

    fun importProfileLink(rawLink: String) {
        runCatching {
            subscriptionProfiles.importProfile(
                settings = uiState.settings,
                rawLink = rawLink,
                rememberSubscriptionLink = false,
            )
        }.onSuccess { importedSettings ->
            settingsStore.save(importedSettings)
            uiState = uiState.copy(
                settings = importedSettings,
                networkIpAddress = findDeviceNetworkIpAddress(),
            )
            appendLog("Imported connection profile")
        }.onFailure { error ->
            appendLog("Profile import failed: ${error.message ?: error::class.java.simpleName}")
        }
    }

    fun prepareSubscriptionConnection(
        rawLink: String,
        operatorCode: String,
        transportMode: String = uiState.settings.selectedTransportMode(),
        connectionMode: String = "vpn",
    ): String? = runCatching {
        val prepared = SubscriptionConnectionPreparer.prepare(
            settings = uiState.settings,
            rawLink = rawLink,
            operatorCode = operatorCode,
            transportMode = transportMode,
            connectionMode = connectionMode,
            cachedResolverEntries = readCachedResolvers(normalizeOperatorCode(operatorCode)),
        )
        settingsStore.save(prepared)
        uiState = uiState.copy(settings = prepared, networkIpAddress = findDeviceNetworkIpAddress())
        if (transportMode == WhiteZiaOptions.TransportDns) prewarmStormDnsRuntime(prepared)
        null
    }.getOrElse { error ->
        "Subscription failed: ${error.message ?: error::class.java.simpleName}"
    }

    suspend fun discoverAndApplyDnsResolvers(onLog: (String) -> Unit = {}): String? {
        return withContext(Dispatchers.IO) {
            runCatching {
                val customResolvers = validateResolverText(uiState.settings.customResolverText).normalizedResolvers
                if (uiState.settings.customResolversEnabled && customResolvers.isNotEmpty()) {
                    val resolverText = customResolvers.joinToString(separator = "\n")
                    val updatedSettings = uiState.settings.copy(
                        resolverText = resolverText,
                        customResolverText = resolverText,
                        selectedResolverProfileId = "",
                    ).syncSelectedConnectionProfileFields()
                    settingsStore.save(updatedSettings)
                    withContext(Dispatchers.Main.immediate) {
                        uiState = uiState.copy(
                            settings = updatedSettings,
                            networkIpAddress = findDeviceNetworkIpAddress(),
                        )
                    }
                    prewarmStormDnsRuntime(updatedSettings)
                    onLog("Поиск DNS пропущен: включены кастомные resolver'ы")
                    return@runCatching null
                }
                val selectedOperatorCode = normalizeOperatorCode(uiState.settings.operatorCode)
                val connectivityManager = appContext.getSystemService(ConnectivityManager::class.java)
                if (hasActiveExternalVpn(connectivityManager)) {
                    onLog("Поиск DNS пропущен: активен другой VPN")
                    return@runCatching "Отключите другой VPN перед поиском local DNS"
                }
                onLog("Ищу локальный DNS активной сети")
                val discoveryNetworks = resolverDiscovery.dnsDiscoveryNetworks(connectivityManager)
                val scannedResolvers = withTimeoutOrNull(DnsDiscoveryTimeoutMillis) {
                    resolverDiscovery.discoverDnsResolvers(discoveryNetworks, onLog)
                } ?: run {
                    onLog("Локальный scan DNS превысил timeout, перехожу к базе")
                    emptyList()
                }
                val registryResolvers = if (scannedResolvers.isEmpty()) {
                    onLog("Локальный scan не нашел resolver'ы, запрашиваю базу")
                    resolverDiscovery.discoverRegistryResolvers(
                        operatorCode = selectedOperatorCode,
                        discoveryNetworks = discoveryNetworks,
                        onLog = onLog,
                    )
                } else {
                    emptyList()
                }
                val discoveredResolvers = when {
                    scannedResolvers.isNotEmpty() -> scannedResolvers
                    registryResolvers.isNotEmpty() -> registryResolvers
                    else -> {
                        onLog("В базе тоже нет рабочих resolver'ов, подставляю Yandex DNS")
                        YandexDnsFallbackResolvers
                    }
                }
                val resolverText = discoveredResolvers.joinToString(separator = "\n")
                val updatedSettings = uiState.settings.copy(
                    resolverText = resolverText,
                    selectedResolverProfileId = "",
                ).syncSelectedConnectionProfileFields()
                settingsStore.save(updatedSettings)
                if (scannedResolvers.isNotEmpty()) {
                    cacheEmbeddedResolvers(scannedResolvers.joinToString(separator = "\n"), selectedOperatorCode)
                }
                withContext(Dispatchers.Main.immediate) {
                    uiState = uiState.copy(
                        settings = updatedSettings,
                        networkIpAddress = findDeviceNetworkIpAddress(),
                    )
                }
                prewarmStormDnsRuntime(updatedSettings)
                onLog("DNS resolver'ы применены: ${discoveredResolvers.joinToString()}")
                null
            }.getOrElse { error ->
                "DNS discovery failed: ${error.message ?: error::class.java.simpleName}"
            }
        }
    }

    fun currentResolverEntries(): List<String> {
        return validateResolverText(uiState.settings.resolverText).normalizedResolvers
    }

    private fun resolverCacheWritesDisabled(): Boolean {
        return uiState.settings.customResolversEnabled
    }

    fun isYandexResolverSet(resolvers: List<String> = currentResolverEntries()): Boolean {
        return resolvers.isNotEmpty() && resolvers.all { it in YandexDnsFallbackResolvers }
    }

    fun shouldRunResolverBenchmark(): Boolean {
        if (resolverCacheWritesDisabled()) {
            return false
        }
        val localResolvers = currentResolverBenchmarkLocalResolvers()
        if (localResolvers.isEmpty()) {
            return false
        }
        if (forcedResolverBenchmarkLocalResolvers == localResolvers) {
            return true
        }
        val operatorCode = uiState.settings.operatorCode
        val lastBenchmarkBucket = readResolverBenchmarkLastLaunchBucket(operatorCode, localResolvers)
        if (lastBenchmarkBucket == null && readResolverBenchmarkWinnerId(operatorCode, localResolvers) != null) {
            // A winner saved by an older version has no launch metadata yet.
            return false
        }
        return ResolverBenchmarkSchedule.isDue(
            lastBenchmarkBucket = lastBenchmarkBucket,
            launchCount = resolverBenchmarkLaunchCount,
        )
    }

    fun applyCachedResolverBenchmarkWinner(onLog: (String) -> Unit = {}): Boolean {
        if (resolverCacheWritesDisabled()) {
            return false
        }
        val localResolvers = currentResolverBenchmarkLocalResolvers()
        if (localResolvers.isEmpty()) {
            return false
        }
        val operatorCode = uiState.settings.operatorCode
        val winnerId = readResolverBenchmarkWinnerId(operatorCode, localResolvers)
        if (
            winnerId != null &&
            readResolverBenchmarkLastLaunchBucket(operatorCode, localResolvers) == null
        ) {
            markResolverBenchmarkAttempted(localResolvers)
        }
        if (shouldRunResolverBenchmark()) {
            forcedResolverBenchmarkLocalResolvers = localResolvers
            if (currentResolverEntries() != localResolvers) {
                applyResolverEntriesForReconnect(localResolvers)
            }
            onLog(
                if (winnerId == null) {
                    "Первое подключение: сравню local и Yandex resolver'ы"
                } else {
                    "Каждые 10 запусков: повторно сравню local и Yandex resolver'ы"
                },
            )
            return false
        }
        winnerId ?: return false
        val winnerResolvers = when (winnerId) {
            ResolverBenchmarkWinnerYandex -> YandexDnsFallbackResolvers
            ResolverBenchmarkWinnerLocal -> localResolvers
            else -> validateResolverText(winnerId).normalizedResolvers
        }
        if (winnerResolvers.isEmpty() || winnerResolvers == localResolvers) {
            return false
        }
        applyResolverEntriesForReconnect(winnerResolvers)
        onLog("Применен быстрый набор resolver'ов: ${resolverBenchmarkLabel(winnerId)}")
        return true
    }

    fun applyResolverEntriesForReconnect(resolvers: List<String>) {
        val normalizedResolvers = validateResolverText(resolvers.joinToString(separator = "\n")).normalizedResolvers
        if (normalizedResolvers.isEmpty()) {
            return
        }
        val updatedSettings = uiState.settings.copy(
            resolverText = normalizedResolvers.joinToString(separator = "\n"),
            selectedResolverProfileId = "",
        ).syncSelectedConnectionProfileFields()
        settingsStore.save(updatedSettings)
        uiState = uiState.copy(settings = updatedSettings)
    }

    fun markResolverBenchmarkAttempted(localResolvers: List<String>) {
        if (resolverCacheWritesDisabled()) {
            return
        }
        val normalizedLocalResolvers = validateResolverText(localResolvers.joinToString(separator = "\n"))
            .normalizedResolvers
        if (normalizedLocalResolvers.isEmpty()) {
            return
        }
        val operatorCode = uiState.settings.operatorCode
        resolverCacheStore.markBenchmarkAttempted(operatorCode, normalizedLocalResolvers)
        if (forcedResolverBenchmarkLocalResolvers == normalizedLocalResolvers) {
            forcedResolverBenchmarkLocalResolvers = emptyList()
        }
    }

    suspend fun reportCurrentResolversToRegistry(onLog: (String) -> Unit = {}) {
        val stateSnapshot = uiState
        val operatorCode = normalizeOperatorCode(stateSnapshot.settings.operatorCode)
        val runtimeResolvers = WhiteZiaScannerResultStore.normalizeResolverEntries(
            stateSnapshot.resolverRuntimeState.activeResolvers + stateSnapshot.resolverRuntimeState.validResolvers,
        )
            .distinct()
        val rawResolvers = runtimeResolvers.ifEmpty {
            validateResolverText(stateSnapshot.settings.resolverText).normalizedResolvers
        }
        val resolvers = rawResolvers
            .mapNotNull(::registryReportableResolverOrNull)
            .distinct()
            .take(TargetResolverCount)
        if (resolvers.isEmpty()) {
            onLog(
                if (rawResolvers.isEmpty()) {
                    "Registry report skipped: нет resolver'ов"
                } else {
                    "Registry report skipped: resolver'ы отфильтрованы (${rawResolvers.joinToString()})"
                },
            )
            return
        }
        val reportKey = registryReportKey(operatorCode, resolvers)
        if (lastReportedRegistryResolversKey == reportKey) {
            onLog("Registry report skipped: resolver'ы уже отправлены")
            return
        }
        onLog("Registry report: operator=$operatorCode, resolvers=${resolvers.joinToString()}")
        val result = withContext(Dispatchers.IO) {
            val discoveryNetworks = resolverDiscovery.dnsDiscoveryNetworks(appContext.getSystemService(ConnectivityManager::class.java))
            runCatching {
                resolverDiscovery.postRegistryResolvers(
                    operatorCode = operatorCode,
                    resolvers = resolvers,
                    discoveryNetworks = discoveryNetworks,
                    socksProxyPort = stateSnapshot.settings.resolve().listenPort,
                )
            }
        }
        result
            .onSuccess { message ->
                lastReportedRegistryResolversKey = reportKey
                onLog(message)
            }
            .onFailure { error -> onLog("Registry report failed: ${error.readableNetworkMessage()}") }
    }

    fun refreshBatteryOptimizationStatus() {
        uiState = uiState.copy(
            batteryOptimizationIgnored = isIgnoringBatteryOptimizations(appContext),
        )
    }

    fun refreshBatteryOptimizationStatusWithRetry() {
        batteryOptimizationRefreshJob?.cancel()
        batteryOptimizationRefreshJob = viewModelScope.launch {
            repeat(BatteryOptimizationRefreshAttempts) { attempt ->
                refreshBatteryOptimizationStatus()
                if (uiState.batteryOptimizationIgnored) {
                    return@launch
                }
                if (attempt < BatteryOptimizationRefreshAttempts - 1) {
                    delay(BatteryOptimizationRefreshRetryDelayMillis)
                }
            }
        }
    }

    fun refreshNotificationStatus() {
        uiState = uiState.copy(
            notificationsEnabled = areNotificationsEnabled(appContext),
        )
    }

    fun refreshRuntimeConnectionStatus() {
        runtimeRefreshJob?.cancel()
        runtimeRefreshJob = viewModelScope.launch {
            if (runtimeRestoreSuppressed) {
                return@launch
            }
            val activeRuntimeState = withContext(Dispatchers.IO) {
                findActiveRuntimeState()
            }
            if (activeRuntimeState != null) {
                if (!isSameConnectedRuntime(activeRuntimeState)) {
                    restoreRuntimeConnection(activeRuntimeState)
                }
                return@launch
            }
            if (uiState.connectionStatus == ConnectionStatus.CONNECTED) {
                markRuntimeDisconnected("VPN or proxy runtime is no longer running")
                // A later sticky restart may still publish a live session. Only an
                // explicit user Stop suppresses that restoration.
                runtimeRestoreSuppressed = false
                return@launch
            }
            if (
                uiState.connectionStatus == ConnectionStatus.CONNECTING &&
                !isCurrentConnectionAttemptFresh()
            ) {
                appendLog("Cleared stale connection state after runtime check")
                withContext(Dispatchers.IO) {
                    stopAllRuntimeServicesAndAwait()
                }
                markRuntimeDisconnected("Stale connection state was cleared")
            }
        }
    }

    fun beginConnection(): Boolean {
        if (uiState.connectionStatus != ConnectionStatus.DISCONNECTED) {
            appendDebugLog("Connect request ignored: status=${uiState.connectionStatus}")
            return false
        }
        runtimeRestoreSuppressed = false
        connectionAttemptStartedAtMillis = System.currentTimeMillis()

        connectJob?.cancel()
        statsJob?.cancel()
        runtimeRefreshJob?.cancel()
        verificationJob?.cancel()
        serverTests.cancel()
        val sessionId = UUID.randomUUID().toString()
        activeRuntimeSessionId = sessionId
        val requestedSettings = uiState.settings.copy(transportMode = uiState.settings.selectedTransportMode())
        val initialLogs = buildList {
            add("[INFO] Starting WhiteZia")
            if (requestedSettings.logLevel == "DEBUG") {
                add(
                    "[DEBUG] Connect request: session=${debugSessionId(sessionId)}, " +
                        "manual=${requestedSettings.manualMode}, transport=${requestedSettings.transportMode}, " +
                        "forceDns=${requestedSettings.forceDnsTunnel}, mode=${requestedSettings.connectionMode}, " +
                        "candidates=awg:${requestedSettings.amneziaWgCandidates.size}/xray:${requestedSettings.xrayCandidates.size}/stormdns:${requestedSettings.stormDnsCandidates.size}",
                )
            }
        }
        uiState = uiState.copy(
            settings = requestedSettings,
            connectionStatus = ConnectionStatus.CONNECTING,
            activeTransportMode = WhiteZiaOptions.TransportAuto,
            connectionStats = ConnectionStats(),
            resolverRuntimeState = ResolverRuntimeState(),
            connectionProgress = ConnectionProgressState(phase = "preparing", percent = 3),
            connectionVerification = ConnectionVerificationState(),
            autoTuneTrialResults = emptyList(),
            serverTestState = ServerTestState(),
            connectionLogs = initialLogs,
        )
        resetTrafficAccounting()
        trafficBaseline = currentTrafficSnapshot()
        lastTrafficSnapshot = trafficBaseline
        resetSocksStreamTracker()
        resetRuntimeUiThrottles()

        connectJob = viewModelScope.launch {
            if (!withContext(Dispatchers.IO) { prepareRuntimeForConnection() }) {
                if (
                    uiState.connectionStatus == ConnectionStatus.CONNECTING &&
                    activeRuntimeSessionId == sessionId
                ) {
                    markRuntimeStartBlocked()
                }
                return@launch
            }
            if (
                uiState.connectionStatus != ConnectionStatus.CONNECTING ||
                activeRuntimeSessionId != sessionId
            ) {
                return@launch
            }
            withContext(Dispatchers.IO) {
                stopServerTestManagers()
            }
            val settings = uiState.settings.syncSelectedConnectionProfileFields()
            val canStartWithAmnezia = false
            val canStartWithXray = settings.xrayUri.isNotBlank() &&
                (settings.transportMode == WhiteZiaOptions.TransportAuto ||
                    settings.transportMode == WhiteZiaOptions.TransportXray)
            appendDebugLog(
                "Connection plan: session=${debugSessionId(sessionId)}, transport=${settings.transportMode}, " +
                    "awgAvailable=$canStartWithAmnezia, xrayAvailable=$canStartWithXray, " +
                    "resolverCount=${settings.resolve().resolverEntries.size}, " +
                    "nodes=awg:${debugNodeId(settings.activeAmneziaWgNodeId)}/" +
                    "xray:${debugNodeId(settings.activeXrayNodeId)}/stormdns:${debugNodeId(settings.activeStormDnsNodeId)}",
            )
            val servicePreparesVpnResolvers = settings.connectionMode == "vpn"
            if (!servicePreparesVpnResolvers && !canStartWithAmnezia && !canStartWithXray && settings.resolve().resolverEntries.isEmpty()) {
                appendLog("AWG config, Xray URI, or CottenDNS resolvers are required to connect")
                activeRuntimeSessionId = ""
                runtimeRestoreSuppressed = true
                uiState = uiState.copy(
                    connectionStatus = ConnectionStatus.DISCONNECTED,
                    resolverRuntimeState = ResolverRuntimeState(),
                    connectionProgress = ConnectionProgressState(),
                    connectionVerification = ConnectionVerificationState(),
                    autoTuneTrialResults = emptyList(),
                    serverTestState = ServerTestState(),
                )
                return@launch
            }
            val connectionProfile = settings.selectedConnectionProfile()
            val serverProfile = selectServerProfile(settings)
            val stormDnsProfileRequired = settings.resolve().connectionMode != "vpn" ||
                settings.transportMode == WhiteZiaOptions.TransportDns
            if (stormDnsProfileRequired && serverProfile == null) {
                appendLog(
                    if (connectionProfile.serverMode == "custom") {
                        "Custom CottenDNS domain and encryption key are required"
                    } else {
                        "No CottenDNS server profile configured"
                    },
                )
                activeRuntimeSessionId = ""
                runtimeRestoreSuppressed = true
                uiState = uiState.copy(
                    connectionStatus = ConnectionStatus.DISCONNECTED,
                    resolverRuntimeState = ResolverRuntimeState(),
                    connectionProgress = ConnectionProgressState(),
                    connectionVerification = ConnectionVerificationState(),
                    autoTuneTrialResults = emptyList(),
                    serverTestState = ServerTestState(),
                )
                return@launch
            }

            activeServerProfile = serverProfile
            val runtimeSettings = settings.runtimeConnectionSettings()
            uiState = uiState.copy(
                settings = settings,
                activeConnectionProfileId = connectionProfile.id,
            )
            val started = launchRuntime(
                sessionId = sessionId,
                connectionProfile = connectionProfile,
                serverProfile = serverProfile,
                runtimeSettings = runtimeSettings,
            )

            if (started) {
                uiState = uiState.copy(
                    networkIpAddress = findDeviceNetworkIpAddress(),
                    activeConnectionProfileId = connectionProfile.id,
                )
            } else {
                withContext(Dispatchers.IO) {
                    stopAllRuntimeServicesAndAwait()
                }
                activeProxyListenPort = WhiteZiaRuntimeProxy.ListenPortInt
                activeRuntimeSessionId = ""
                runtimeRestoreSuppressed = true
                resetTrafficAccounting()
                resetSocksStreamTracker()
                resetRuntimeUiThrottles()
                appendLog("Connection failed")
                uiState = uiState.copy(
                    connectionStatus = ConnectionStatus.DISCONNECTED,
                    connectionStats = ConnectionStats(),
                    resolverRuntimeState = ResolverRuntimeState(),
                    connectionProgress = ConnectionProgressState(),
                    connectionVerification = ConnectionVerificationState(),
                    networkIpAddress = findDeviceNetworkIpAddress(),
                    serverTestState = ServerTestState(),
                    activeConnectionProfileId = null,
                )
            }
        }
        return true
    }

    private suspend fun launchRuntime(
        sessionId: String,
        connectionProfile: ConnectionProfile,
        serverProfile: StormDnsServerProfile?,
        runtimeSettings: WhiteZiaSettings,
    ): Boolean {
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val resolvedSettings = runtimeSettings.resolve()
                activeProxyListenPort = resolvedSettings.listenPort
                val modeLabel = if (resolvedSettings.connectionMode == "vpn") {
                    "Full System VPN"
                } else {
                    "Proxy Only"
                }
                appendDebugLog(
                    "Runtime launch: session=${debugSessionId(sessionId)}, transport=${runtimeSettings.transportMode}, " +
                        "mode=${resolvedSettings.connectionMode}, listen=${resolvedSettings.listenIp}:${resolvedSettings.listenPort}, " +
                        "profile=${connectionProfile.id}, stormdnsProfile=${serverProfile != null}",
                )
                appendLog(
                    if (connectionProfile.serverMode == "custom") {
                        "Using custom CottenDNS server"
                    } else {
                        "Using configured CottenDNS server"
                    },
                )
                appendLog("Connection mode: $modeLabel")
                if (resolvedSettings.connectionMode == "vpn") {
                    appendLog("Starting full-device VPN service")
                    WhiteZiaVpnService.start(
                        context = getApplication<Application>().applicationContext,
                        sessionId = sessionId,
                        serverProfile = serverProfile,
                        settings = runtimeSettings,
                    )
                } else {
                    val requiredServerProfile = requireNotNull(serverProfile) {
                        "CottenDNS server profile is required for proxy mode"
                    }
                    appendLog("Starting local proxy service")
                    WhiteZiaProxyService.start(
                        context = getApplication<Application>().applicationContext,
                        sessionId = sessionId,
                        serverProfile = requiredServerProfile,
                        settings = runtimeSettings,
                    )
                }
                true
            }
        }

        return result.getOrElse { error ->
            appendLog("Launch failed: ${error.message ?: error::class.java.simpleName}")
            false
        }
    }

    fun beginServerTest(serverProfileId: String? = null) = serverTests.beginServerTest(serverProfileId)
    private fun stopServerTestManagers() = serverTests.stopManagers()

    fun disconnect() {
        appendDebugLog(
            "Disconnect requested: session=${debugSessionId(activeRuntimeSessionId)}, " +
                "status=${uiState.connectionStatus}, transport=${uiState.activeTransportMode}, " +
                "connectJobActive=${connectJob?.isActive == true}, stopJobActive=${runtimeStopJob?.isActive == true}, " +
                "proxyPort=$activeProxyListenPort",
        )
        runtimeRestoreSuppressed = true
        connectionAttemptStartedAtMillis = 0L
        val connectionJobToStop = connectJob
        connectionJobToStop?.cancel()
        statsJob?.cancel()
        runtimeRefreshJob?.cancel()
        verificationJob?.cancel()
        serverTests.cancel()
        val previousStopJob = runtimeStopJob
        runtimeStopJob = viewModelScope.launch(Dispatchers.IO) {
            appendDebugLog("Runtime stop job started: waitingPrevious=${previousStopJob?.isActive == true}")
            previousStopJob?.join()
            connectionJobToStop?.join()
            stopServerTestManagers()
            val stopped = stopAllRuntimeServicesAndAwait()
            appendDebugLog("Runtime stop job finished: stopped=$stopped")
        }
        activeProxyListenPort = WhiteZiaRuntimeProxy.ListenPortInt
        activeRuntimeSessionId = ""
        resetTrafficAccounting()
        resetSocksStreamTracker()
        resetRuntimeUiThrottles()
        appendLog("Disconnected")
        uiState = uiState.copy(
            connectionStatus = ConnectionStatus.DISCONNECTED,
            activeTransportMode = WhiteZiaOptions.TransportAuto,
            connectionStats = ConnectionStats(),
            resolverRuntimeState = ResolverRuntimeState(),
            connectionProgress = ConnectionProgressState(),
            connectionVerification = ConnectionVerificationState(),
            autoTuneTrialResults = emptyList(),
            serverTestState = ServerTestState(),
            activeConnectionProfileId = null,
        )
    }

    fun updateScanWorkerCount(rawValue: String) = scanUi.updateScanWorkerCount(rawValue)
    fun updateScanConnectionProfile(profileId: String) = scanUi.updateScanConnectionProfile(profileId)
    fun beginScanFromFile(uri: Uri) = scanUi.beginScanFromFile(uri)
    fun beginScanFromDefaultResolvers() = scanUi.beginScanFromDefaultResolvers()
    fun startPreparedScan() = scanUi.startPreparedScan()
    fun stopScan() = scanUi.stopScan()
    fun refreshScanState() = scanUi.refreshScanState()
    fun resumeScan() = scanUi.resumeScan()

    private fun startStatsMonitor() {
        statsJob?.cancel()
        statsJob = viewModelScope.launch {
            var runtimeCheckTicks = 0
            while (isActive && uiState.connectionStatus == ConnectionStatus.CONNECTED) {
                delay(1_000)
                val stats = withContext(Dispatchers.IO) {
                    buildConnectionStats()
                }
                uiState = uiState.copy(
                    connectionStats = stats,
                )
                if (++runtimeCheckTicks % 5 == 0) refreshRuntimeConnectionStatus()
            }
        }
    }

    override fun onCleared() {
        connectJob?.cancel()
        statsJob?.cancel()
        runtimeRefreshJob?.cancel()
        verificationJob?.cancel()
        serverTests.cancel()
        scanUi.close()
        viewModelScope.launch(Dispatchers.IO) {
            stopServerTestManagers()
        }
        WhiteZiaProxyEvents.removeListener(proxyEventListener)
        WhiteZiaVpnEvents.removeListener(vpnEventListener)
        unregisterRuntimeBroadcastReceivers()
        super.onCleared()
    }

    private fun registerRuntimeBroadcastReceivers() {
        ContextCompat.registerReceiver(
            appContext,
            proxyBroadcastReceiver,
            IntentFilter(WhiteZiaProxyService.BroadcastAction),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        ContextCompat.registerReceiver(
            appContext,
            vpnBroadcastReceiver,
            IntentFilter(WhiteZiaVpnService.BroadcastAction),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        ContextCompat.registerReceiver(
            appContext,
            scanBroadcastReceiver,
            IntentFilter(WhiteZiaScanService.BroadcastAction),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    private fun unregisterRuntimeBroadcastReceivers() {
        runCatching {
            appContext.unregisterReceiver(proxyBroadcastReceiver)
        }
        runCatching {
            appContext.unregisterReceiver(vpnBroadcastReceiver)
        }
        runCatching {
            appContext.unregisterReceiver(scanBroadcastReceiver)
        }
    }

    private fun handleRuntimeLog(sessionId: String, message: String) {
        if (isStaleRuntimeEvent(sessionId)) return
        viewModelScope.launch(Dispatchers.Main.immediate) {
            applyCurrentRuntimeUiEvent(sessionId, { !isStaleRuntimeEvent(it) }) event@ {
                val trafficStats = parseStormDnsTrafficStatsLine(message)
                val progressState = parseStormDnsConnectionProgressLine(message)
                val resolverState = parseStormDnsResolverStateLine(message)
                if (trafficStats != null) stormDnsTrafficAccounting.record(trafficStats)
                trackSocksStreamLogLine(message)
                val isTelemetry = trafficStats != null || progressState != null || resolverState != null ||
                    message.contains("WD_PROGRESS") || message.contains("WD_RESOLVERS")
                if (progressState == null && resolverState == null && isTelemetry) return@event
                progressState?.let(::updateConnectionProgressOnMain)
                resolverState?.let(::updateResolverStateOnMain)
                if (!isTelemetry) appendLogOnMain(message)
            }
        }
    }

    private fun handleVpnReconnecting(sessionId: String, message: String, phase: String = "reconnecting") {
        if (isStaleRuntimeEvent(sessionId)) return
        viewModelScope.launch(Dispatchers.Main.immediate) {
            applyCurrentRuntimeUiEvent(sessionId, { !isStaleRuntimeEvent(it) }) event@ {
                if (!shouldHandleRuntimeEvent(WhiteZiaRuntimeStateStore.ModeVpn)) return@event
                statsJob?.cancel()
                verificationJob?.cancel()
                connectionAttemptStartedAtMillis = System.currentTimeMillis()
                appendLogOnMain(message)
                uiState = uiState.copy(
                    connectionStatus = ConnectionStatus.CONNECTING,
                    connectionProgress = runtimeProgressState(phase, uiState.settings.redactDiagnostics(message)),
                    connectionVerification = ConnectionVerificationState(),
                )
            }
        }
    }

    private fun handleRuntimeReady(sessionId: String, message: String, expectedConnectionMode: String) {
        if (isStaleRuntimeEvent(sessionId)) {
            return
        }
        viewModelScope.launch(Dispatchers.Main.immediate) {
            applyCurrentRuntimeUiEvent(sessionId, { !isStaleRuntimeEvent(it) }) event@ {
                if (uiState.connectionStatus == ConnectionStatus.DISCONNECTED) {
                    val activeRuntimeState = withContext(Dispatchers.IO) {
                        findActiveRuntimeState()?.takeIf { it.mode == expectedConnectionMode }
                    }
                    if (isStaleRuntimeEvent(sessionId)) return@event
                    if (activeRuntimeState != null) {
                        restoreRuntimeConnection(activeRuntimeState)
                    }
                    return@event
                }
                if (uiState.connectionStatus != ConnectionStatus.CONNECTING) {
                    return@event
                }
                if (uiState.settings.resolve().connectionMode != expectedConnectionMode) {
                    return@event
                }
                val readySnapshot = withContext(Dispatchers.IO) {
                    currentReadyRuntimeSnapshot(WhiteZiaRuntimeStateStore.read(appContext, expectedConnectionMode),
                        sessionId, expectedConnectionMode)
                } ?: return@event
                val activeTransportMode = transportModeForRuntimeReady(
                    expectedConnectionMode = expectedConnectionMode,
                    message = message,
                    runtimeTransport = readySnapshot.transportMode,
                )
                if (isStaleRuntimeEvent(sessionId)) return@event
                appendLogOnMain(message)
                connectionAttemptStartedAtMillis = 0L
                uiState = uiState.copy(
                    activeTransportMode = activeTransportMode,
                    connectionStatus = ConnectionStatus.CONNECTED,
                    connectionStats = ConnectionStats(),
                    connectionProgress = ConnectionProgressState(phase = "connected", percent = 100),
                    connectionVerification = if (expectedConnectionMode == WhiteZiaRuntimeStateStore.ModeVpn &&
                        activeTransportMode != WhiteZiaOptions.TransportAuto) {
                        proxyVerification("Интернет через SOCKS-прокси проверен; TUN/FakeDNS отдельно не проверены", System.currentTimeMillis())
                    } else ConnectionVerificationState(),
                    networkIpAddress = findDeviceNetworkIpAddress(),
                )
                reportSuccessfulRegistryResolvers(uiState.resolverRuntimeState)
                trafficBaseline = currentTrafficSnapshot()
                lastTrafficSnapshot = trafficBaseline
                startStatsMonitor()
                startConnectionVerification(expectedConnectionMode)
            }
        }
    }

    private fun handleProxyFailure(sessionId: String, message: String) {
        if (isStaleRuntimeEvent(sessionId)) {
            return
        }
        viewModelScope.launch(Dispatchers.Main.immediate) {
            applyCurrentRuntimeUiEvent(sessionId, { !isStaleRuntimeEvent(it) }) event@ {
                if (!shouldHandleRuntimeEvent(WhiteZiaRuntimeStateStore.ModeProxy)) {
                    return@event
                }
                appendLogOnMain(message)
                connectJob?.cancel()
                statsJob?.cancel()
                verificationJob?.cancel()
                serverTests.cancel()
                withContext(Dispatchers.IO) {
                    stopServerTestManagers()
                    stopAllRuntimeServicesAndAwait()
                }
                if (isStaleRuntimeEvent(sessionId)) return@event
                activeProxyListenPort = WhiteZiaRuntimeProxy.ListenPortInt
                activeRuntimeSessionId = ""
                connectionAttemptStartedAtMillis = 0L
                runtimeRestoreSuppressed = true
                resetTrafficAccounting()
                resetSocksStreamTracker()
                resetRuntimeUiThrottles()
                uiState = uiState.copy(
                    connectionStatus = ConnectionStatus.DISCONNECTED,
                    activeTransportMode = WhiteZiaOptions.TransportAuto,
                    connectionStats = ConnectionStats(),
                    resolverRuntimeState = ResolverRuntimeState(),
                    connectionProgress = ConnectionProgressState(),
                    connectionVerification = ConnectionVerificationState(
                        status = ConnectionVerificationStatus.Failed,
                        message = uiState.settings.redactDiagnostics(message),
                        checkedAtMillis = System.currentTimeMillis(),
                    ),
                    networkIpAddress = findDeviceNetworkIpAddress(),
                    serverTestState = ServerTestState(),
                    activeConnectionProfileId = null,
                )
            }
        }
    }

    private fun handleVpnFailure(sessionId: String, message: String) {
        if (isStaleRuntimeEvent(sessionId)) {
            return
        }
        viewModelScope.launch(Dispatchers.Main.immediate) {
            applyCurrentRuntimeUiEvent(sessionId, { !isStaleRuntimeEvent(it) }) event@ {
                if (!shouldHandleRuntimeEvent(WhiteZiaRuntimeStateStore.ModeVpn)) {
                    return@event
                }
                appendLogOnMain(message)
                connectJob?.cancel()
                statsJob?.cancel()
                verificationJob?.cancel()
                serverTests.cancel()
                withContext(Dispatchers.IO) {
                    stopServerTestManagers()
                    stopAllRuntimeServicesAndAwait()
                }
                if (isStaleRuntimeEvent(sessionId)) return@event
                activeProxyListenPort = WhiteZiaRuntimeProxy.ListenPortInt
                activeRuntimeSessionId = ""
                connectionAttemptStartedAtMillis = 0L
                runtimeRestoreSuppressed = true
                resetTrafficAccounting()
                resetSocksStreamTracker()
                resetRuntimeUiThrottles()
                uiState = uiState.copy(
                    connectionStatus = ConnectionStatus.DISCONNECTED,
                    activeTransportMode = WhiteZiaOptions.TransportAuto,
                    connectionStats = ConnectionStats(),
                    resolverRuntimeState = ResolverRuntimeState(),
                    connectionProgress = ConnectionProgressState(),
                    connectionVerification = ConnectionVerificationState(
                        status = ConnectionVerificationStatus.Failed,
                        message = uiState.settings.redactDiagnostics(message),
                        checkedAtMillis = System.currentTimeMillis(),
                    ),
                    networkIpAddress = findDeviceNetworkIpAddress(),
                    serverTestState = ServerTestState(),
                    activeConnectionProfileId = null,
                )
            }
        }
    }

    private fun shouldHandleRuntimeEvent(expectedConnectionMode: String): Boolean {
        return uiState.connectionStatus != ConnectionStatus.DISCONNECTED &&
            uiState.settings.resolve().connectionMode == expectedConnectionMode
    }

    private fun isStaleRuntimeEvent(sessionId: String): Boolean {
        return runtimeRestoreSuppressed ||
            (activeRuntimeSessionId.isNotBlank() && sessionId != activeRuntimeSessionId)
    }

    private fun findActiveRuntimeState(): WhiteZiaRuntimeState? {
        return WhiteZiaRuntimeStateStore.readAll(appContext)
            .asSequence()
            .filter { state ->
                state.status == WhiteZiaRuntimeStateStore.StatusReady ||
                    state.status == WhiteZiaRuntimeStateStore.StatusStarting
            }
            .sortedByDescending { it.updatedAtMillis }
            .firstOrNull(::isRuntimeStateHealthy)
    }

    private fun isRuntimeStateHealthy(state: WhiteZiaRuntimeState): Boolean {
        if (state.isLiveRuntimeStarting() || state.isLiveNetworkRecovery()) return true
        return when (state.mode) {
            WhiteZiaRuntimeStateStore.ModeProxy -> state.listenPort > 0 && canConnectToLocalPort(state.listenPort)
            WhiteZiaRuntimeStateStore.ModeVpn -> {
                val amneziaRuntime = isAmneziaRuntimeState(state)
                val amneziaSettings = if (amneziaRuntime) {
                    RuntimeLaunchRequestStore.load(appContext, state.sessionId)?.settings ?: uiState.settings
                } else {
                    null
                }
                val vpnInterfaceExists = findVpnTrafficInterfaceName(amneziaSettings) != null
                if (amneziaRuntime) {
                    vpnInterfaceExists
                } else {
                    state.listenPort > 0 && vpnInterfaceExists && canConnectToLocalPort(state.listenPort)
                }
            }
            else -> false
        }
    }

    private fun isAmneziaRuntimeState(state: WhiteZiaRuntimeState): Boolean {
        return state.mode == WhiteZiaRuntimeStateStore.ModeVpn &&
            (state.transportMode == WhiteZiaRuntimeStateStore.TransportAmneziaWg ||
                (state.transportMode.isBlank() && state.message.contains("AmneziaWG", ignoreCase = true)))
    }

    private fun isXrayRuntimeState(state: WhiteZiaRuntimeState): Boolean {
        return state.mode == WhiteZiaRuntimeStateStore.ModeVpn &&
            (state.transportMode == WhiteZiaOptions.TransportXray ||
                (state.transportMode.isBlank() && state.message.contains("Xray", ignoreCase = true)))
    }

    private fun isSameConnectedRuntime(state: WhiteZiaRuntimeState): Boolean {
        val activeProfileId = state.connectionProfileId.takeIf(String::isNotBlank)
        return uiState.connectionStatus == ConnectionStatus.CONNECTED &&
            state.status == WhiteZiaRuntimeStateStore.StatusReady &&
            (state.sessionId.isBlank() || activeRuntimeSessionId == state.sessionId) &&
            uiState.settings.resolve().connectionMode == state.mode &&
            (activeProfileId == null || uiState.activeConnectionProfileId == activeProfileId)
    }

    private fun restoreRuntimeConnection(state: WhiteZiaRuntimeState) {
        val recovering = state.isLiveNetworkRecovery()
        val starting = state.status == WhiteZiaRuntimeStateStore.StatusStarting
        if (recovering || starting) {
            statsJob?.cancel()
            verificationJob?.cancel()
        }
        val profileId = state.connectionProfileId.takeIf(String::isNotBlank)
        activeRuntimeSessionId = state.sessionId
        connectionAttemptStartedAtMillis = 0L
        val restoredSettings = uiState.settings
            .copy(
                selectedConnectionProfileId = profileId ?: uiState.settings.selectedConnectionProfileId,
                connectionMode = state.mode,
            )
            .syncSelectedConnectionProfileFields()
        val restoredTransportMode = when {
            isXrayRuntimeState(state) -> WhiteZiaOptions.TransportXray
            state.mode == WhiteZiaRuntimeStateStore.ModeVpn && !isAmneziaRuntimeState(state) ->
                WhiteZiaOptions.TransportDns
            else -> WhiteZiaOptions.TransportAuto
        }
        activeProxyListenPort = state.listenPort.takeIf { it > 0 }
            ?: restoredSettings.runtimeConnectionSettings().resolve().listenPort
        resetTrafficAccounting()
        resetSocksStreamTracker()
        resetRuntimeUiThrottles()
        val modeLabel = if (state.mode == WhiteZiaRuntimeStateStore.ModeVpn) {
            "VPN"
        } else {
            "proxy"
        }
        uiState = uiState.copy(
            settings = restoredSettings,
            activeTransportMode = restoredTransportMode,
            connectionStatus = if (recovering || starting) ConnectionStatus.CONNECTING else ConnectionStatus.CONNECTED,
            connectionStats = ConnectionStats(),
            resolverRuntimeState = ResolverRuntimeState(),
            connectionProgress = if (recovering || starting) {
                ConnectionProgressState(phase = if (recovering) "reconnecting" else "preparing", percent = 0)
            } else {
                ConnectionProgressState(phase = "connected", percent = 100)
            },
            connectionVerification = ConnectionVerificationState(),
            networkIpAddress = findDeviceNetworkIpAddress(),
            activeConnectionProfileId = restoredSettings.selectedConnectionProfile().id,
            connectionLogs = prependConnectionLog("Restored active $modeLabel connection"),
        )
        trafficBaseline = currentTrafficSnapshot()
        lastTrafficSnapshot = trafficBaseline
        if (!recovering && !starting) {
            startStatsMonitor()
            startConnectionVerification(state.mode)
        }
    }

    private fun markRuntimeDisconnected(message: String) {
        connectJob?.cancel()
        statsJob?.cancel()
        verificationJob?.cancel()
        serverTests.cancel()
        stopServerTestManagers()
        activeProxyListenPort = WhiteZiaRuntimeProxy.ListenPortInt
        activeRuntimeSessionId = ""
        connectionAttemptStartedAtMillis = 0L
        runtimeRestoreSuppressed = true
        resetTrafficAccounting()
        resetSocksStreamTracker()
        resetRuntimeUiThrottles()
        uiState = uiState.copy(
            connectionStatus = ConnectionStatus.DISCONNECTED,
            activeTransportMode = WhiteZiaOptions.TransportAuto,
            connectionStats = ConnectionStats(),
            resolverRuntimeState = ResolverRuntimeState(),
            connectionProgress = ConnectionProgressState(),
            connectionVerification = ConnectionVerificationState(),
            networkIpAddress = findDeviceNetworkIpAddress(),
            serverTestState = ServerTestState(),
            activeConnectionProfileId = null,
            connectionLogs = prependConnectionLog(message),
        )
    }

    private fun prependConnectionLog(message: String): List<String> {
        val cleanMessage = uiState.settings.redactDiagnostics(message)
            .trim()
        if (cleanMessage.isEmpty()) {
            return uiState.connectionLogs
        }
        return (uiState.connectionLogs + cleanMessage).takeLast(MaxConnectionLogs)
    }

    private fun transportModeForRuntimeReady(
        expectedConnectionMode: String,
        message: String,
        runtimeTransport: String = "",
    ): String {
        if (expectedConnectionMode != WhiteZiaRuntimeStateStore.ModeVpn) {
            return WhiteZiaOptions.TransportDns
        }
        when (runtimeTransport) {
            WhiteZiaRuntimeStateStore.TransportAmneziaWg -> return WhiteZiaOptions.TransportAuto
            WhiteZiaOptions.TransportXray -> return WhiteZiaOptions.TransportXray
            WhiteZiaOptions.TransportDns -> return WhiteZiaOptions.TransportDns
        }
        if (message.contains("AmneziaWG", ignoreCase = true)) {
            return WhiteZiaOptions.TransportAuto
        }
        if (message.contains("Xray", ignoreCase = true)) {
            return WhiteZiaOptions.TransportXray
        }
        return WhiteZiaOptions.TransportDns
    }

    private fun shouldReconfigureActiveVpn(
        previousSettings: WhiteZiaSettings,
        nextSettings: WhiteZiaSettings,
    ): Boolean {
        if (uiState.connectionStatus != ConnectionStatus.CONNECTED) {
            return false
        }
        if (previousSettings.resolve().connectionMode != "vpn" || nextSettings.resolve().connectionMode != "vpn") {
            return false
        }
        return previousSettings.splitTunnelMode != nextSettings.splitTunnelMode ||
            previousSettings.splitTunnelPackages != nextSettings.splitTunnelPackages ||
            (uiState.activeTransportMode == WhiteZiaOptions.TransportDns &&
                ((previousSettings.resolverText != nextSettings.resolverText && nextSettings.resolverText.isNotBlank()) ||
                    previousSettings.cottenDnsMode != nextSettings.cottenDnsMode ||
                    previousSettings.cottenDnsResolverTransport != nextSettings.cottenDnsResolverTransport))
    }

    private fun reconfigureActiveVpnSettings(settings: WhiteZiaSettings) {
        val sessionId = activeRuntimeSessionId
        if (sessionId.isBlank()) return
        val runtimeSettings = settings.runtimeConnectionSettings().copy(
            transportMode = uiState.activeTransportMode,
            forceDnsTunnel = uiState.activeTransportMode == WhiteZiaOptions.TransportDns,
        )
        viewModelScope.launch(Dispatchers.IO) {
            if (activeRuntimeSessionId != sessionId || uiState.connectionStatus != ConnectionStatus.CONNECTED) return@launch
            val resolvedSettings = runtimeSettings.resolve()
            if (resolvedSettings.connectionMode != "vpn") {
                return@launch
            }
            runCatching {
                WhiteZiaVpnService.start(
                    context = getApplication<Application>().applicationContext,
                    sessionId = sessionId,
                    serverProfile = activeServerProfile,
                    settings = runtimeSettings,
                    reconfigureOnly = true,
                )
            }.onSuccess {
                appendLog("Настройки активного VPN обновлены")
            }.onFailure { error ->
                handleVpnFailure(
                    activeRuntimeSessionId,
                    "Failed to update VPN settings: ${error.message ?: error::class.java.simpleName}",
                )
            }
        }
    }

    private fun stopAllRuntimeServices() {
        WhiteZiaVpnService.stop(appContext)
        WhiteZiaProxyService.stop(appContext)
    }

    private fun isCurrentConnectionAttemptFresh(nowMillis: Long = System.currentTimeMillis()): Boolean {
        if (connectJob?.isActive == true) {
            return true
        }
        val startedAtMillis = connectionAttemptStartedAtMillis
        return startedAtMillis > 0L &&
            nowMillis - startedAtMillis in 0..ConnectionAttemptStaleTimeoutMillis
    }

    suspend fun awaitRuntimeStopCompletion(): Boolean {
        return withContext(Dispatchers.IO) {
            val startedAtNanos = System.nanoTime()
            appendDebugLog("Awaiting runtime stop completion: stopJobActive=${runtimeStopJob?.isActive == true}")
            runtimeStopJob?.join()
            val stopped = awaitRuntimeFullyStopped()
            val elapsedMillis = (System.nanoTime() - startedAtNanos) / 1_000_000L
            appendDebugLog("Runtime stop completion confirmed: stopped=$stopped, elapsed=${elapsedMillis}ms")
            stopped
        }
    }

    private fun runtimeListenPortsToCheck(): Set<Int> {
        return buildSet {
            add(WhiteZiaRuntimeProxy.ListenPortInt)
            add(activeProxyListenPort)
            add(uiState.settings.runtimeConnectionSettings().resolve().listenPort)
            WhiteZiaRuntimeStateStore.readAll(appContext).forEach { state ->
                add(state.listenPort)
            }
        }.filterTo(mutableSetOf()) { port -> port in 1..65_535 }
    }

    private suspend fun prepareRuntimeForConnection(): Boolean {
        runtimeStopJob?.join()
        return runtimeStopGuard.prepare()
    }

    private suspend fun stopAllRuntimeServicesAndAwait(): Boolean = runtimeStopGuard.stopAndAwait()
    private suspend fun awaitRuntimeFullyStopped(): Boolean = runtimeStopGuard.awaitStopped()

    private fun markRuntimeStartBlocked() {
        appendLog("Connection start blocked: previous runtime is still active")
        activeRuntimeSessionId = ""
        runtimeRestoreSuppressed = true
        uiState = uiState.copy(
            connectionStatus = ConnectionStatus.DISCONNECTED,
            activeTransportMode = WhiteZiaOptions.TransportAuto,
            resolverRuntimeState = ResolverRuntimeState(),
            connectionProgress = ConnectionProgressState(),
            connectionVerification = ConnectionVerificationState(),
            autoTuneTrialResults = emptyList(),
            serverTestState = ServerTestState(),
        )
    }

    private fun Throwable.readableNetworkMessage(): String {
        return message?.takeIf(String::isNotBlank) ?: javaClass.simpleName
    }


    private fun cacheEmbeddedResolvers(
        embeddedResolverText: String,
        operatorCode: String = uiState.settings.operatorCode,
    ) {
        val embeddedResolvers = validateResolverText(embeddedResolverText).normalizedResolvers
        mergeCachedResolvers(embeddedResolvers, operatorCode)
    }

    private fun mergeCachedResolvers(
        resolvers: List<String>,
        operatorCode: String = uiState.settings.operatorCode,
    ) {
        if (!resolverCacheWritesDisabled()) {
            resolverCacheStore.mergeResolvers(resolvers, operatorCode, ::isCacheableLocalResolver)
        }
    }

    private fun readCachedResolvers(operatorCode: String): List<String> {
        return resolverCacheStore.readCachedResolvers(operatorCode, ::isCacheableLocalResolver)
    }

    private fun readResolverBenchmarkWinnerId(operatorCode: String, localResolvers: List<String>): String? {
        return resolverCacheStore.readBenchmarkWinner(operatorCode, localResolvers)
    }

    fun currentResolverBenchmarkLocalResolvers(): List<String> {
        val currentResolvers = currentResolverEntries()
        if (currentResolvers.isEmpty()) {
            return emptyList()
        }
        return if (isYandexResolverSet(currentResolvers)) {
            readResolverBenchmarkLocalResolvers(uiState.settings.operatorCode)
                .ifEmpty {
                    readCachedResolvers(uiState.settings.operatorCode).take(TargetResolverCount)
                }
        } else {
            currentResolvers
        }
    }

    private fun readResolverBenchmarkLocalResolvers(operatorCode: String): List<String> {
        return resolverCacheStore.readBenchmarkLocalResolvers(operatorCode)
    }

    private fun readResolverBenchmarkLastLaunchBucket(
        operatorCode: String,
        localResolvers: List<String>,
    ): Int? {
        return resolverCacheStore.readBenchmarkLastLaunchBucket(operatorCode, localResolvers)
    }

    private fun resolverBenchmarkLabel(winnerId: String): String {
        return when (winnerId) {
            ResolverBenchmarkWinnerYandex -> "Yandex DNS"
            ResolverBenchmarkWinnerLocal -> "local DNS"
            else -> "custom DNS"
        }
    }

    private fun prewarmStormDnsRuntime(settings: WhiteZiaSettings) {
        val serverProfile = selectServerProfile(settings) ?: return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                StormDnsProcessManager(appContext)
                    .prepareLaunch(
                        serverProfile = serverProfile,
                        settings = settings.runtimeConnectionSettings(),
                    )
            }.onSuccess { launchSpec ->
                runCatching { launchSpec.configFile.delete() }
                runCatching { launchSpec.resolversFile.delete() }
                appendLog("Runtime prewarmed")
            }.onFailure { error ->
                appendLog("Runtime prewarm skipped: ${error.message ?: error::class.java.simpleName}")
            }
        }
    }

    private fun rememberFastResolver(resolverState: ResolverRuntimeState) {
        if (resolverCacheWritesDisabled()) {
            return
        }
        val resolver = (resolverState.activeResolvers + resolverState.validResolvers)
            .firstOrNull()
            ?.let { validateResolverText(it).normalizedResolvers.firstOrNull() }
            ?: return
        if (!isCacheableLocalResolver(resolver)) {
            return
        }
        val operatorCode = normalizeOperatorCode(uiState.settings.operatorCode)
        if (resolver !in readCachedResolvers(operatorCode)) {
            return
        }
        if (resolverCacheStore.rememberSuccessfulResolver(operatorCode, resolver)) {
            appendLog("Cached fast resolver: $resolver")
        }
    }

    private fun reportSuccessfulRegistryResolvers(resolverState: ResolverRuntimeState) {
        if (uiState.connectionStatus != ConnectionStatus.CONNECTED) {
            return
        }
        val runtimeResolvers = WhiteZiaScannerResultStore.normalizeResolverEntries(
            resolverState.activeResolvers + resolverState.validResolvers,
        )
            .distinct()
        val rawResolvers = runtimeResolvers.ifEmpty {
            validateResolverText(uiState.settings.resolverText).normalizedResolvers
        }
        if (rawResolvers.isEmpty()) {
            appendRegistryReportSkipLog(
                "no-resolvers",
                "Registry report skipped: нет active/valid resolver'ов и resolverText пустой",
            )
            return
        }
        val resolvers = rawResolvers
            .mapNotNull(::registryReportableResolverOrNull)
            .distinct()
            .take(TargetResolverCount)
        if (resolvers.isEmpty()) {
            appendRegistryReportSkipLog(
                "filtered:${rawResolvers.joinToString(",")}",
                "Registry report skipped: resolver'ы отфильтрованы (${rawResolvers.joinToString()})",
            )
            return
        }
        val operatorCode = normalizeOperatorCode(uiState.settings.operatorCode)
        val reportKey = registryReportKey(operatorCode, resolvers)
        if (lastReportedRegistryResolversKey == reportKey) {
            return
        }
        lastReportedRegistryResolversKey = reportKey
        val discoveryNetworks = resolverDiscovery.dnsDiscoveryNetworks(appContext.getSystemService(ConnectivityManager::class.java))
        reportRegistryResolvers(
            operatorCode = operatorCode,
            resolvers = resolvers,
            discoveryNetworks = discoveryNetworks,
        )
        appendLog("Registry report: operator=$operatorCode, resolvers=${resolvers.joinToString()}")
    }

    private fun reportRegistryResolvers(
        operatorCode: String,
        resolvers: List<String>,
        discoveryNetworks: List<DnsDiscoveryNetwork>,
    ) {
        val normalizedResolvers = resolvers
            .mapNotNull(::registryReportableResolverOrNull)
            .distinct()
            .take(TargetResolverCount)
        if (normalizedResolvers.isEmpty()) {
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                resolverDiscovery.postRegistryResolvers(
                    operatorCode = operatorCode,
                    resolvers = normalizedResolvers,
                    discoveryNetworks = discoveryNetworks,
                    socksProxyPort = uiState.settings.resolve().listenPort,
                )
            }
                .onSuccess(::appendLog)
                .onFailure { error -> appendLog("Registry report failed: ${error.readableNetworkMessage()}") }
        }
    }

    private fun registryReportKey(operatorCode: String, resolvers: List<String>): String {
        return "${normalizeOperatorCode(operatorCode)}:${resolvers.distinct().joinToString(",")}"
    }

    private fun appendRegistryReportSkipLog(key: String, message: String) {
        if (lastRegistryReportSkipKey == key) {
            return
        }
        lastRegistryReportSkipKey = key
        appendLog(message)
    }

    private fun hasActiveExternalVpn(connectivityManager: ConnectivityManager): Boolean {
        return connectivityManager.allNetworks.any { network ->
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return@any false
            capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN) &&
                capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }
    }

    private fun hasActiveVpnTransport(connectivityManager: ConnectivityManager): Boolean {
        return connectivityManager.allNetworks.any { network ->
            connectivityManager.getNetworkCapabilities(network)
                ?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN) == true
        }
    }

    private fun startConnectionVerification(expectedConnectionMode: String) {
        verificationJob?.cancel()
        if (expectedConnectionMode == WhiteZiaRuntimeStateStore.ModeVpn) return
        uiState = uiState.copy(
            connectionVerification = ConnectionVerificationState(
                status = ConnectionVerificationStatus.Checking,
                message = "Проверяю интернет через локальный SOCKS-прокси",
                coverage = ConnectionVerificationCoverage.SocksProxy,
            ),
        )
        verificationJob = viewModelScope.launch {
            delay(VerificationStartDelayMillis)
            val result = withContext(Dispatchers.IO) {
                verifyActiveConnection(expectedConnectionMode)
            }
            if (
                uiState.connectionStatus != ConnectionStatus.CONNECTED ||
                uiState.settings.resolve().connectionMode != expectedConnectionMode
            ) {
                return@launch
            }
            uiState = uiState.copy(connectionVerification = result)
            appendLog(result.message)
        }
    }

    private suspend fun verifyActiveConnection(expectedConnectionMode: String): ConnectionVerificationState {
        val resolvedSettings = uiState.settings
            .runtimeConnectionSettings()
            .resolve()
            .copy(listenPort = activeProxyListenPort)
        if (resolvedSettings.connectionMode != expectedConnectionMode) {
            return failedVerification("Connection mode changed before verification finished")
        }
        val activeTransport = uiState.activeTransportMode
        val usesLocalSocks = expectedConnectionMode != WhiteZiaRuntimeStateStore.ModeVpn ||
            activeTransport == WhiteZiaOptions.TransportXray ||
            activeTransport == WhiteZiaOptions.TransportDns
        if (!usesLocalSocks) {
            return ConnectionVerificationState(
                status = ConnectionVerificationStatus.Verified,
                message = "Connection verified: AmneziaWG handshake completed",
                checkedAtMillis = System.currentTimeMillis(),
            )
        }
        if (!canConnectToLocalPort(activeProxyListenPort)) {
            return failedVerification("Connection verification failed: local SOCKS listener is not reachable")
        }
        if (expectedConnectionMode == WhiteZiaRuntimeStateStore.ModeVpn && findVpnTrafficInterfaceName() == null) {
            return failedVerification("Connection verification failed: VPN interface is not active")
        }

        val probePassed = repeatBooleanAttempt(VerificationProbeAttempts) {
            WhiteZiaTrafficWarmup.verifySocksRoute(resolvedSettings)
        }
        if (!probePassed) {
            return failedVerification("Connection verification failed: SOCKS route cannot reach the internet")
        }
        return proxyVerification(
            message = "Интернет через SOCKS-прокси проверен; TUN/FakeDNS отдельно не проверены",
            checkedAtMillis = System.currentTimeMillis(),
        )
    }

    private fun failedVerification(message: String): ConnectionVerificationState {
        return ConnectionVerificationState(
            status = ConnectionVerificationStatus.Failed,
            message = message,
            checkedAtMillis = System.currentTimeMillis(),
        )
    }

    private suspend fun repeatBooleanAttempt(
        attempts: Int,
        block: () -> Boolean,
    ): Boolean {
        repeat(attempts.coerceAtLeast(1)) { attempt ->
            if (block()) {
                return true
            }
            if (attempt < attempts - 1) {
                delay(VerificationProbeRetryDelayMillis)
            }
        }
        return false
    }

    private fun buildConnectionStats(): ConnectionStats {
        val connectedApps = countTrackedSocksStreams()
        stormDnsTrafficAccounting.latest()?.let { stats ->
            val peakSpeed = maxOf(
                uiState.connectionStats.peakSpeedBytesPerSecond,
                stats.downloadSpeedBytesPerSecond + stats.uploadSpeedBytesPerSecond,
            )
            return ConnectionStats(
                downloadBytes = stats.downloadBytes,
                uploadBytes = stats.uploadBytes,
                totalDataUsageBytes = stats.downloadBytes + stats.uploadBytes,
                downloadSpeedBytesPerSecond = stats.downloadSpeedBytesPerSecond,
                uploadSpeedBytesPerSecond = stats.uploadSpeedBytesPerSecond,
                peakSpeedBytesPerSecond = peakSpeed,
                connectedApps = connectedApps,
            )
        }

        val previous = lastTrafficSnapshot
        val current = currentTrafficSnapshot()
        if (
            current.sourceKey != previous.sourceKey ||
            current.sourceKey != trafficBaseline.sourceKey ||
            current.rxBytes < previous.rxBytes ||
            current.txBytes < previous.txBytes ||
            current.rxBytes < trafficBaseline.rxBytes ||
            current.txBytes < trafficBaseline.txBytes
        ) {
            trafficBaseline = current
            lastTrafficSnapshot = current
            return ConnectionStats(
                connectedApps = connectedApps,
            )
        }
        lastTrafficSnapshot = current

        val elapsedMillis = (current.timestampMillis - previous.timestampMillis).coerceAtLeast(1)
        val downloadBytes = (current.rxBytes - trafficBaseline.rxBytes).coerceAtLeast(0)
        val uploadBytes = (current.txBytes - trafficBaseline.txBytes).coerceAtLeast(0)
        val downloadSpeed = (((current.rxBytes - previous.rxBytes).coerceAtLeast(0)) * 1_000) / elapsedMillis
        val uploadSpeed = (((current.txBytes - previous.txBytes).coerceAtLeast(0)) * 1_000) / elapsedMillis
        val peakSpeed = maxOf(
            uiState.connectionStats.peakSpeedBytesPerSecond,
            downloadSpeed + uploadSpeed,
        )

        return ConnectionStats(
            downloadBytes = downloadBytes,
            uploadBytes = uploadBytes,
            totalDataUsageBytes = downloadBytes + uploadBytes,
            downloadSpeedBytesPerSecond = downloadSpeed,
            uploadSpeedBytesPerSecond = uploadSpeed,
            peakSpeedBytesPerSecond = peakSpeed,
            connectedApps = connectedApps,
        )
    }

    private fun currentTrafficSnapshot(): TrafficSnapshot {
        return currentUidTrafficSnapshot()
    }

    private fun currentUidTrafficSnapshot(): TrafficSnapshot {
        val uid = getApplication<Application>().applicationInfo.uid
        val rxBytes = TrafficStats.getUidRxBytes(uid).normalizeTrafficCounter()
        val txBytes = TrafficStats.getUidTxBytes(uid).normalizeTrafficCounter()
        return TrafficSnapshot(
            rxBytes = rxBytes,
            txBytes = txBytes,
            timestampMillis = System.currentTimeMillis(),
            sourceKey = "$UidTrafficSourcePrefix$uid",
        )
    }

    private fun findVpnTrafficInterfaceName(amneziaSettings: WhiteZiaSettings? = null): String? {
        val expectedAddresses = if (amneziaSettings != null) {
            configuredAmneziaVpnAddresses(
                listOf(amneziaSettings.amneziaWgConfig, uiState.settings.amneziaWgConfig) +
                    amneziaSettings.amneziaWgCandidates.map { it.config },
            )
        } else {
            setOf(InetAddress.getByName(WhiteZiaVpnService.TunIpv4Address))
        }
        if (expectedAddresses.isEmpty()) return null
        val connectivityManager = appContext.getSystemService(ConnectivityManager::class.java)
        return connectivityManager.allNetworks
            .asSequence()
            .mapNotNull { network ->
                val capabilities = connectivityManager.getNetworkCapabilities(network)
                val linkProperties = connectivityManager.getLinkProperties(network)
                if (
                    capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true &&
                    linkProperties?.linkAddresses?.any { linkAddress ->
                        linkAddress.address in expectedAddresses
                    } == true
                ) {
                    linkProperties?.interfaceName
                } else {
                    null
                }
            }
            .firstOrNull()
    }

    private fun canConnectToLocalPort(port: Int): Boolean {
        if (port !in 1..65535) {
            return false
        }
        return runCatching {
            Socket().use { socket ->
                socket.connect(InetSocketAddress("127.0.0.1", port), 300)
            }
            true
        }.getOrDefault(false)
    }

    private fun updateConnectionProgressOnMain(progressState: ConnectionProgressState) {
        val currentProgress = uiState.connectionProgress
        if (progressState == currentProgress) {
            return
        }
        val now = System.currentTimeMillis()
        val phaseOrPercentChanged = progressState.phase != currentProgress.phase ||
            progressState.percent != currentProgress.percent
        val shouldUpdate = phaseOrPercentChanged ||
            now - lastProgressUiUpdateMillis >= RuntimeProgressUiUpdateIntervalMillis
        if (!shouldUpdate) {
            return
        }
        lastProgressUiUpdateMillis = now
        uiState = uiState.copy(connectionProgress = progressState)
    }

    private fun updateResolverStateOnMain(resolverState: ResolverRuntimeState) {
        rememberFastResolver(resolverState)
        val currentState = uiState.resolverRuntimeState
        val nextState = resolverState.withMergedValidResolvers(currentState.validResolvers)
        if (nextState == currentState) {
            reportSuccessfulRegistryResolvers(nextState)
            return
        }
        val now = System.currentTimeMillis()
        val firstVisibleState = currentState == ResolverRuntimeState()
        if (!firstVisibleState && now - lastResolverUiUpdateMillis < RuntimeResolverUiUpdateIntervalMillis) {
            return
        }
        lastResolverUiUpdateMillis = now
        uiState = uiState.copy(resolverRuntimeState = nextState)
        reportSuccessfulRegistryResolvers(nextState)
    }

    private fun ResolverRuntimeState.withMergedValidResolvers(
        currentValidResolvers: List<String>,
    ): ResolverRuntimeState {
        if (currentValidResolvers.isEmpty()) {
            return this
        }
        val mergedValidResolvers = (currentValidResolvers + validResolvers).distinct()
        return copy(validResolvers = mergedValidResolvers)
    }

    private fun trackSocksStreamLogLine(line: String) {
        val now = System.currentTimeMillis()
        socksStreamOpenedRegex.find(line)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { streamId ->
            synchronized(socksStreamTrackerLock) {
                socksStreamLastSeenMillis[streamId] = now
                pruneTrackedSocksStreamsLocked(now)
            }
            return
        }

        val closeMatch = socksStreamClosedRegex.find(line)
        val streamId = closeMatch?.groupValues?.getOrNull(1)?.toIntOrNull() ?: return
        synchronized(socksStreamTrackerLock) {
            socksStreamLastSeenMillis.remove(streamId)
        }
    }

    private fun countTrackedSocksStreams(): Int {
        val now = System.currentTimeMillis()
        return synchronized(socksStreamTrackerLock) {
            pruneTrackedSocksStreamsLocked(now)
            socksStreamLastSeenMillis.size
        }
    }

    private fun resetSocksStreamTracker() {
        synchronized(socksStreamTrackerLock) {
            socksStreamLastSeenMillis.clear()
        }
    }

    private fun resetRuntimeUiThrottles() {
        lastProgressUiUpdateMillis = 0L
        lastResolverUiUpdateMillis = 0L
    }

    private fun resetTrafficAccounting() {
        stormDnsTrafficAccounting.reset()
    }

    private fun pruneTrackedSocksStreamsLocked(now: Long) {
        socksStreamLastSeenMillis.entries.removeAll { (_, lastSeenMillis) ->
            now - lastSeenMillis > SocksStreamTrackingTtlMillis
        }
    }

    private fun Long.normalizeTrafficCounter(): Long {
        return if (this == TrafficStats.UNSUPPORTED.toLong()) 0 else coerceAtLeast(0)
    }

    private fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val powerManager = context.getSystemService(PowerManager::class.java) ?: return true
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    private fun areNotificationsEnabled(context: Context): Boolean {
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private fun findDeviceNetworkIpAddress(): String {
        val connectivityManager = appContext.getSystemService(ConnectivityManager::class.java)
        return connectivityManager.allNetworks
            .asSequence()
            .filter { network ->
                val capabilities = connectivityManager.getNetworkCapabilities(network)
                capabilities != null &&
                    !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            }
            .flatMap { network ->
                connectivityManager.getLinkProperties(network)
                    ?.linkAddresses
                    .orEmpty()
                    .asSequence()
            }
            .map { it.address }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { !it.isLoopbackAddress && !it.isLinkLocalAddress }
            ?.hostAddress
            ?: "127.0.0.1"
    }

    private fun appendLog(message: String) {
        viewModelScope.launch(Dispatchers.Main.immediate) {
            appendLogOnMain(message)
        }
    }

    private fun appendDebugLog(message: String) {
        if (uiState.settings.logLevel == "DEBUG") {
            appendLog("[DEBUG] $message")
        }
    }

    private fun debugSessionId(sessionId: String): String =
        sessionId.take(8).ifBlank { "none" }

    private fun debugNodeId(nodeId: String): String =
        nodeId.trim().take(32).ifBlank { "default" }

    private fun appendLogOnMain(message: String) {
        val cleanMessage = uiState.settings.redactDiagnostics(message)
            .trim()
        if (cleanMessage.isEmpty()) {
            return
        }
        val leveledMessage = if (DiagnosticLogLevelRegex.containsMatchIn(cleanMessage)) {
            cleanMessage
        } else {
            "[INFO] $cleanMessage"
        }
        val nextLogs = (uiState.connectionLogs + leveledMessage).takeLast(MaxConnectionLogs)
        uiState = uiState.copy(connectionLogs = nextLogs)
    }

    private companion object {
        const val MaxConnectionLogs = 300
        val DiagnosticLogLevelRegex = Regex(
            pattern = "\\[(DEBUG|INFO|WARN|WARNING|ERROR)]",
            option = RegexOption.IGNORE_CASE,
        )
        const val RuntimeProgressUiUpdateIntervalMillis = 250L
        const val RuntimeResolverUiUpdateIntervalMillis = 500L
        const val ResolverBenchmarkWinnerLocal = "local"
        const val ResolverBenchmarkWinnerYandex = "yandex"
        const val TargetResolverCount = 4
        val YandexDnsFallbackResolvers = shop.whitezia.client.resolver.ResolverDnsPolicy.Yandex
        val PublicDnsResolvers = shop.whitezia.client.resolver.ResolverDnsPolicy.Public
        const val DnsDiscoveryTimeoutMillis = 40_000L
        const val SuccessfulSpeedThresholdMbps = 1.5
        const val RuntimeHealthRetryDelayMillis = 1_000L
        const val ConnectionAttemptStaleTimeoutMillis = 30_000L
        const val SocksStreamTrackingTtlMillis = 120_000L
        const val EmptyTrafficSource = "none"
        const val BatteryOptimizationRefreshAttempts = 8
        const val BatteryOptimizationRefreshRetryDelayMillis = 500L
        const val VerificationStartDelayMillis = 700L
        const val VerificationProbeAttempts = 2
        const val VerificationProbeRetryDelayMillis = 750L
        const val StaleScanStateTimeoutMillis = 15_000L
        const val UidTrafficSourcePrefix = "uid:"
        val socksStreamOpenedRegex = Regex("""New SOCKS\d TCP CONNECT .*Stream ID:\s*(\d+)""")
        val socksStreamClosedRegex = Regex("""ARQ Stream Closed .*Stream:\s*(\d+)""")
    }

    private data class TrafficSnapshot(
        val rxBytes: Long,
        val txBytes: Long,
        val timestampMillis: Long,
        val sourceKey: String,
    ) {
        companion object {
            fun empty(): TrafficSnapshot {
                return TrafficSnapshot(
                    rxBytes = 0,
                    txBytes = 0,
                    timestampMillis = System.currentTimeMillis(),
                    sourceKey = EmptyTrafficSource,
                )
            }
        }
    }

}
