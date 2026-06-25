package shop.whitedns.client.ui

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Uri
import android.net.TrafficStats
import android.os.PowerManager
import android.provider.OpenableColumns
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedInputStream
import java.io.File
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.HttpURLConnection
import java.net.NetworkInterface
import java.net.Proxy
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.util.Collections
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import org.json.JSONArray
import org.json.JSONObject
import shop.whitedns.client.model.AdvancedSettingsProfile
import shop.whitedns.client.model.AutoTuneTrialResult
import shop.whitedns.client.model.ConnectionProfile
import shop.whitedns.client.model.ConnectionProgressState
import shop.whitedns.client.model.ConnectionStats
import shop.whitedns.client.model.ConnectionStatus
import shop.whitedns.client.model.ConnectionVerificationState
import shop.whitedns.client.model.ConnectionVerificationStatus
import shop.whitedns.client.model.ResolverProfile
import shop.whitedns.client.model.ResolverRuntimeState
import shop.whitedns.client.model.ServerTestResult
import shop.whitedns.client.model.ServerTestState
import shop.whitedns.client.model.ServerTestStatus
import shop.whitedns.client.model.StormDnsServerProfile
import shop.whitedns.client.model.WhiteDnsOptions
import shop.whitedns.client.model.WhiteDnsScanDefaults
import shop.whitedns.client.model.WhiteDnsScanState
import shop.whitedns.client.model.WhiteDnsScanStatus
import shop.whitedns.client.model.WhiteDnsRuntimeProxy
import shop.whitedns.client.model.WhiteDnsSettings
import shop.whitedns.client.model.WhiteDnsSettingsStore
import shop.whitedns.client.model.WhiteDnsUiState
import shop.whitedns.client.model.WhiteDnsAutoTunePresets
import shop.whitedns.client.model.WhiteDnsParallelTest
import shop.whitedns.client.model.applyAdvancedProfile
import shop.whitedns.client.model.applyAutoTunePreset
import shop.whitedns.client.model.importStormDnsProfileLink
import shop.whitedns.client.model.normalizedAdvancedProfiles
import shop.whitedns.client.model.normalizedConnectionProfiles
import shop.whitedns.client.model.normalizedResolverProfiles
import shop.whitedns.client.model.resolve
import shop.whitedns.client.model.runtimeConnectionSettings
import shop.whitedns.client.model.selectedConnectionProfile
import shop.whitedns.client.model.syncSelectedConnectionProfileFields
import shop.whitedns.client.model.validateResolverText
import shop.whitedns.client.proxy.WhiteDnsProxyEvent
import shop.whitedns.client.proxy.WhiteDnsProxyEvents
import shop.whitedns.client.proxy.WhiteDnsProxyService
import shop.whitedns.client.runtime.StormDnsTrafficAccounting
import shop.whitedns.client.runtime.WhiteDnsRuntimeState
import shop.whitedns.client.runtime.WhiteDnsRuntimeStateStore
import shop.whitedns.client.runtime.WhiteDnsTrafficWarmup
import shop.whitedns.client.runtime.RuntimeLaunchRequestStore
import shop.whitedns.client.runtime.formatTrafficSpeed
import shop.whitedns.client.runtime.parseStormDnsConnectionProgressLine
import shop.whitedns.client.runtime.parseStormDnsResolverStateLine
import shop.whitedns.client.runtime.parseStormDnsTrafficStatsLine
import shop.whitedns.client.scan.WhiteDnsScanLaunchRequest
import shop.whitedns.client.scan.WhiteDnsScanRequestStore
import shop.whitedns.client.scan.WhiteDnsScanService
import shop.whitedns.client.scan.WhiteDnsScanSettingsStore
import shop.whitedns.client.scan.WhiteDnsScanStateStore
import shop.whitedns.client.scan.WhiteDnsScannerResultStore
import shop.whitedns.client.storm.StormDnsBuiltInPool
import shop.whitedns.client.storm.StormDnsProcessManager
import shop.whitedns.client.vpn.WhiteDnsVpnService
import shop.whitedns.client.vpn.WhiteDnsVpnEvent
import shop.whitedns.client.vpn.WhiteDnsVpnEvents

data class ResolverBenchmarkScore(
    val label: String,
    val speedBytesPerSecond: Long,
    val speedSuccessfulSamples: Int,
    val healthSuccesses: Int,
    val resolverSuccesses: Int,
    val resolverAttempts: Int,
    val averageResolverLatencyMillis: Long,
) {
    val isUsable: Boolean
        get() = healthSuccesses > 0 && (speedBytesPerSecond > 0L || resolverSuccesses > 0)

    val resolverSuccessRatePercent: Int
        get() = if (resolverAttempts <= 0) {
            0
        } else {
            (resolverSuccesses * 100 / resolverAttempts).coerceIn(0, 100)
        }
}

class WhiteDnsViewModel(
    application: Application,
) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val settingsStore = WhiteDnsSettingsStore(appContext)
    private val scanSettingsStore = WhiteDnsScanSettingsStore(appContext)
    private val fastResolverStore = appContext.getSharedPreferences(FastResolverPreferencesName, Context.MODE_PRIVATE)
    private val initialSettings = settingsStore.load()
    private val initialPersistedScanState = WhiteDnsScanStateStore.read(appContext)
    private val initialScanState = initialPersistedScanState
        .recoverIfStale(
            nowMillis = System.currentTimeMillis(),
            staleAfterMillis = StaleScanStateTimeoutMillis,
        )

    var uiState by mutableStateOf(
        WhiteDnsUiState(
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
    private var statsJob: Job? = null
    private var runtimeRefreshJob: Job? = null
    private var batteryOptimizationRefreshJob: Job? = null
    private var verificationJob: Job? = null
    private var scanLaunchJob: Job? = null
    private var scanStateRefreshJob: Job? = null
    private var lastScannerResultProfileText = ""
    private var activeServerProfile: StormDnsServerProfile? = null
    private var activeRuntimeSessionId: String = ""
    private var activeProxyListenPort: Int = WhiteDnsRuntimeProxy.ListenPortInt
    private var trafficBaseline = TrafficSnapshot.empty()
    private var lastTrafficSnapshot = TrafficSnapshot.empty()
    private var activeVpnTrafficInterfaceName: String? = null
    private val stormDnsTrafficAccounting = StormDnsTrafficAccounting()
    private val autoTuneTrialManagersLock = Any()
    private var autoTuneTrialManagers: List<StormDnsProcessManager> = emptyList()
    private var lastAutoTuneWinnerConfigId = ""
    private var serverTestJob: Job? = null
    private val serverTestManagersLock = Any()
    private var serverTestManagers: List<StormDnsProcessManager> = emptyList()
    private var lastProgressUiUpdateMillis = 0L
    private var lastResolverUiUpdateMillis = 0L
    private var lastReportedRegistryResolversKey = ""
    private var lastRegistryReportSkipKey = ""
    private val socksStreamTrackerLock = Any()
    private val socksStreamLastSeenMillis = mutableMapOf<Int, Long>()
    private val proxyEventListener: (WhiteDnsProxyEvent) -> Unit = { event ->
        when (event) {
            is WhiteDnsProxyEvent.Log -> handleRuntimeLog(event.sessionId, event.message)
            is WhiteDnsProxyEvent.Ready -> handleRuntimeReady(event.sessionId, event.message, expectedConnectionMode = "proxy")
            is WhiteDnsProxyEvent.Failed -> handleProxyFailure(event.sessionId, event.message)
        }
    }
    private val vpnEventListener: (WhiteDnsVpnEvent) -> Unit = { event ->
        when (event) {
            is WhiteDnsVpnEvent.Log -> handleRuntimeLog(event.sessionId, event.message)
            is WhiteDnsVpnEvent.Ready -> handleRuntimeReady(event.sessionId, event.message, expectedConnectionMode = "vpn")
            is WhiteDnsVpnEvent.Failed -> handleVpnFailure(event.sessionId, event.message)
        }
    }
    private val proxyBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != WhiteDnsProxyService.BroadcastAction) {
                return
            }
            val message = intent.getStringExtra(WhiteDnsProxyService.BroadcastExtraMessage).orEmpty()
            val sessionId = intent.getStringExtra(WhiteDnsProxyService.BroadcastExtraSessionId).orEmpty()
            when (intent.getStringExtra(WhiteDnsProxyService.BroadcastExtraType)) {
                WhiteDnsProxyService.BroadcastTypeLog -> handleRuntimeLog(sessionId, message)
                WhiteDnsProxyService.BroadcastTypeReady -> handleRuntimeReady(sessionId, message, expectedConnectionMode = "proxy")
                WhiteDnsProxyService.BroadcastTypeFailed -> handleProxyFailure(sessionId, message)
            }
        }
    }
    private val vpnBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != WhiteDnsVpnService.BroadcastAction) {
                return
            }
            val message = intent.getStringExtra(WhiteDnsVpnService.BroadcastExtraMessage).orEmpty()
            val sessionId = intent.getStringExtra(WhiteDnsVpnService.BroadcastExtraSessionId).orEmpty()
            when (intent.getStringExtra(WhiteDnsVpnService.BroadcastExtraType)) {
                WhiteDnsVpnService.BroadcastTypeLog -> handleRuntimeLog(sessionId, message)
                WhiteDnsVpnService.BroadcastTypeReady -> handleRuntimeReady(sessionId, message, expectedConnectionMode = "vpn")
                WhiteDnsVpnService.BroadcastTypeFailed -> handleVpnFailure(sessionId, message)
            }
        }
    }
    private val scanBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != WhiteDnsScanService.BroadcastAction) {
                return
            }
            refreshScanState()
        }
    }

    init {
        if (initialScanState != initialPersistedScanState) {
            WhiteDnsScanStateStore.write(appContext, initialScanState)
        }
        WhiteDnsProxyEvents.addListener(proxyEventListener)
        WhiteDnsVpnEvents.addListener(vpnEventListener)
        registerRuntimeBroadcastReceivers()
        refreshRuntimeConnectionStatus()
        refreshScanState()
    }

    fun updateSettings(settings: WhiteDnsSettings) {
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

        val syncedSettings = settings.syncSelectedConnectionProfileFields()
        val normalizedSettings = syncedSettings
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
        if (shouldReconfigureActiveVpn(previousSettings, normalizedSettings)) {
            reconfigureActiveVpnSplitTunnel(normalizedSettings)
        }
    }

    fun updateSubscriptionLink(rawLink: String) {
        val updatedSettings = uiState.settings.copy(subscriptionLink = rawLink)
        settingsStore.save(updatedSettings)
        uiState = uiState.copy(settings = updatedSettings)
    }

    fun updateOperatorCode(operatorCode: String) {
        val normalizedOperatorCode = normalizeOperatorCode(operatorCode)
        val previousOperatorCode = normalizeOperatorCode(uiState.settings.operatorCode)
        val updatedSettings = uiState.settings.copy(operatorCode = normalizedOperatorCode)
        settingsStore.save(updatedSettings)
        if (normalizedOperatorCode != previousOperatorCode) {
            lastAutoTuneWinnerConfigId = readCachedAutoTuneWinnerConfigId(normalizedOperatorCode).orEmpty()
        }
        uiState = uiState.copy(settings = updatedSettings)
    }

    fun resetConnectionLog(message: String = "Ready") {
        uiState = uiState.copy(connectionLogs = listOf(message))
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
            uiState.settings
                .importStormDnsProfileLink(rawLink)
                .syncSelectedConnectionProfileFields()
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
        transportMode: String = WhiteDnsOptions.TransportAuto,
    ): String? {
        return runCatching {
            val importedSettings = uiState.settings
                .importStormDnsProfileLink(rawLink)
                .syncSelectedConnectionProfileFields()
            val selectedProfileId = importedSettings.selectedConnectionProfileId
            val vpnProfiles = importedSettings.normalizedConnectionProfiles().map { profile ->
                if (profile.id == selectedProfileId) {
                    profile.copy(connectionMode = "vpn")
                } else {
                    profile
                }
            }
            val selectedOperatorCode = normalizeOperatorCode(operatorCode)
            val cachedResolvers = readCachedResolvers(selectedOperatorCode).take(TargetResolverCount)
            val cachedResolverText = cachedResolvers
                .joinToString(separator = "\n")
            val previousSettings = uiState.settings
            val customResolvers = validateResolverText(previousSettings.customResolverText).normalizedResolvers
            val customResolverText = customResolvers.joinToString(separator = "\n")
            val useCustomResolvers = previousSettings.customResolversEnabled && customResolvers.isNotEmpty()
            val resolverText = when {
                useCustomResolvers -> customResolverText
                importedSettings.resolverText.isNotBlank() -> importedSettings.resolverText
                else -> cachedResolverText
            }
            val preparedBaseSettings = importedSettings.copy(
                connectionProfiles = vpnProfiles,
                connectionMode = "vpn",
                protocolType = "SOCKS5",
                resolverText = resolverText,
                customResolversEnabled = previousSettings.customResolversEnabled,
                customResolverText = previousSettings.customResolverText,
                customConnectionSettingsEnabled = previousSettings.customConnectionSettingsEnabled,
                selectedResolverProfileId = "",
                subscriptionLink = rawLink.trim(),
                transportMode = transportMode,
                amneziaWgConfig = importedSettings.amneziaWgConfig,
                operatorCode = selectedOperatorCode,
                listenIp = "127.0.0.1",
                listenPort = "10886",
                httpProxyEnabled = true,
                httpProxyPort = "10887",
                socks5Authentication = false,
                socksUsername = "",
                socksPassword = "",
                localDnsEnabled = false,
                localDnsPort = "10888",
                balancingStrategy = 3,
                uploadDuplication = "3",
                downloadDuplication = "7",
                uploadCompression = 2,
                downloadCompression = 2,
                baseEncodeData = false,
                minUploadMtu = "40",
                minDownloadMtu = "300",
                maxUploadMtu = "140",
                maxDownloadMtu = "3000",
                mtuTestRetriesResolvers = "3",
                mtuTestTimeoutResolvers = "2.0",
                mtuTestParallelismResolvers = "100",
                mtuTestRetriesLogs = "5",
                mtuTestTimeoutLogs = "2.0",
                mtuTestParallelismLogs = "32",
                rxTxWorkers = "4",
                tunnelProcessWorkers = "4",
                tunnelPacketTimeoutSeconds = "10.0",
                dispatcherIdlePollIntervalSeconds = "0.02",
                txChannelSize = "2048",
                rxChannelSize = "2048",
                resolverUdpConnectionPoolSize = "64",
                streamQueueInitialCapacity = "128",
                orphanQueueInitialCapacity = "32",
                dnsResponseFragmentStoreCapacity = "256",
                maxActiveStreams = "2048",
                localHandshakeTimeoutSeconds = "5.0",
                socksUdpAssociateReadTimeoutSeconds = "30.0",
                clientTerminalStreamRetentionSeconds = "45.0",
                clientCancelledSetupRetentionSeconds = "120.0",
                sessionInitRetryBaseSeconds = "1.0",
                sessionInitRetryStepSeconds = "1.0",
                sessionInitRetryLinearAfter = "5",
                sessionInitRetryMaxSeconds = "60.0",
                sessionInitBusyRetryIntervalSeconds = "60.0",
                autoTuneEnabled = false,
                parallelTestSelectedConfigIds = WhiteDnsParallelTest.defaultConfigIds,
                parallelTestAggressivePresetsEnabled = false,
                startupMode = "resolvers",
                pingWatchdogSeconds = "300",
                trafficWarmupEnabled = false,
                trafficKeepaliveIntervalSeconds = "5",
                logLevel = "WARN",
                splitTunnelMode = importedSettings.splitTunnelMode
                    .takeIf {
                        it == WhiteDnsOptions.SplitTunnelModeInclude ||
                            it == WhiteDnsOptions.SplitTunnelModeExclude ||
                            it == WhiteDnsOptions.SplitTunnelModeOff
                    }
                    ?: WhiteDnsOptions.SplitTunnelModeOff,
                splitTunnelPackages = importedSettings.splitTunnelPackages,
            )
            val preparedSettings = if (previousSettings.customConnectionSettingsEnabled) {
                preparedBaseSettings
                    .copyStormRuntimeSettingsFrom(previousSettings)
                    .syncSelectedConnectionProfileFields()
            } else {
                preparedBaseSettings
                    .applyOperatorRuntimeSettings(selectedOperatorCode)
                    .applyFastStartupRuntimeSettings(useCachedResolvers = cachedResolvers.isNotEmpty())
                    .syncSelectedConnectionProfileFields()
            }

            settingsStore.save(preparedSettings)
            uiState = uiState.copy(
                settings = preparedSettings,
                networkIpAddress = findDeviceNetworkIpAddress(),
            )
            prewarmStormDnsRuntime(preparedSettings)
            null
        }.getOrElse { error ->
            "Subscription failed: ${error.message ?: error::class.java.simpleName}"
        }
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
                onLog("Ищу локальный DNS активной сети")
                val discoveryNetworks = dnsDiscoveryNetworks(
                    appContext.getSystemService(ConnectivityManager::class.java),
                )
                val scannedResolvers = withTimeoutOrNull(DnsDiscoveryTimeoutMillis) {
                    discoverDnsResolvers(discoveryNetworks, onLog)
                } ?: run {
                    onLog("Локальный scan DNS превысил timeout, перехожу к базе")
                    emptyList()
                }
                val registryResolvers = if (scannedResolvers.isEmpty()) {
                    onLog("Локальный scan не нашел resolver'ы, запрашиваю базу")
                    discoverRegistryResolvers(
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
                if (scannedResolvers.isNotEmpty() || registryResolvers.isNotEmpty()) {
                    cacheEmbeddedResolvers(resolverText, selectedOperatorCode)
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

    suspend fun runAmneziaPostConnectionCheck(onLog: (String) -> Unit = {}): Boolean {
        return withContext(Dispatchers.IO) {
            onLog("Проверяю AmneziaWG через быстрый health-check")
            delay(AmneziaPostConnectStabilizationDelayMillis)
            val healthSuccesses = measurePostConnectHttpHealthScore(
                onLog = onLog,
                logPrefix = "Amnezia HTTP",
                connectTimeoutMillis = AmneziaPostConnectHealthConnectTimeoutMillis,
                readTimeoutMillis = AmneziaPostConnectHealthReadTimeoutMillis,
            )
            if (healthSuccesses >= PostConnectHealthSuccessThreshold) {
                onLog("AmneziaWG health-check пройден")
                return@withContext true
            }

            onLog("AmneziaWG health-check не прошел, пробую короткий Cloudflare download")
            val speedBytesPerSecond = measureCloudflarePostConnectBytesPerSecond(
                onLog = onLog,
                socksProxyPort = null,
                downloadBytes = AmneziaQuickDownloadBytes,
                attempts = AmneziaQuickDownloadAttempts,
                connectTimeoutMillis = AmneziaQuickDownloadConnectTimeoutMillis,
                readTimeoutMillis = AmneziaQuickDownloadReadTimeoutMillis,
                logPrefix = "Amnezia quick",
            )
            val ok = speedBytesPerSecond > 0L
            onLog(
                if (ok) {
                    "AmneziaWG quick download пройден: ${formatTrafficSpeed(speedBytesPerSecond)}"
                } else {
                    "AmneziaWG check не пройден"
                },
            )
            ok
        }
    }

    suspend fun runDnsPostConnectionCheck(onLog: (String) -> Unit = {}): Boolean {
        return withContext(Dispatchers.IO) {
            onLog("Жду стабилизацию туннеля")
            delay(PostConnectStabilizationDelayMillis)
            repeat(PostConnectHealthAttempts) { attemptIndex ->
                onLog("Health-check ${attemptIndex + 1}/$PostConnectHealthAttempts")
                if (runPostConnectHttpHealthCheck(onLog)) {
                    onLog("Health-check пройден")
                    return@withContext true
                }

                val speedBytesPerSecond = measureCloudflarePostConnectBytesPerSecond(
                    onLog = onLog,
                    socksProxyPort = null,
                )
                if (speedBytesPerSecond > 0L) {
                    onLog("Cloudflare-check пройден: ${formatTrafficSpeed(speedBytesPerSecond)}")
                    return@withContext true
                }
                onLog("Раунд проверки не прошел")
                if (attemptIndex < PostConnectHealthAttempts - 1) {
                    delay(PostConnectHealthRetryDelayMillis)
                }
            }
            onLog("Health-check не пройден: endpoints и Cloudflare недоступны")
            false
        }
    }

    suspend fun measureCloudflareTunnelSpeedBytesPerSecond(onLog: (String) -> Unit = {}): Long {
        val socksPort = uiState.settings.resolve().listenPort
        return withContext(Dispatchers.IO) {
            onLog("Тест скорости Cloudflare через туннель")
            measureCloudflareSpeedBytesPerSecond(onLog, socksProxyPort = socksPort)
        }
    }

    suspend fun measureResolverBenchmarkScore(
        label: String,
        onLog: (String) -> Unit = {},
    ): ResolverBenchmarkScore {
        val settingsSnapshot = uiState.settings
        val resolvers = validateResolverText(settingsSnapshot.resolverText).normalizedResolvers
        val socksPort = settingsSnapshot.resolve().listenPort
        return withContext(Dispatchers.IO) {
            onLog("$label: проверка HTTP endpoint'ов")
            val healthSuccesses = measurePostConnectHttpHealthScore(
                onLog = onLog,
                logPrefix = "$label HTTP",
            )
            onLog("$label: длинный Cloudflare speedtest")
            val speed = measureCloudflareBenchmarkSpeedSummary(
                onLog = onLog,
                logPrefix = label,
                socksProxyPort = socksPort,
            )
            val resolverProbe = measureResolverSetProbe(resolvers, onLog, label)
            ResolverBenchmarkScore(
                label = label,
                speedBytesPerSecond = speed.bestBytesPerSecond,
                speedSuccessfulSamples = speed.successfulSamples,
                healthSuccesses = healthSuccesses,
                resolverSuccesses = resolverProbe.successes,
                resolverAttempts = resolverProbe.attempts,
                averageResolverLatencyMillis = resolverProbe.averageLatencyMillis,
            ).also { score ->
                onLog(
                    "$label score: speed=${formatTrafficSpeed(score.speedBytesPerSecond)}, " +
                        "http=$healthSuccesses/${PostConnectHealthUrls.size}, " +
                        "dns=${score.resolverSuccesses}/${score.resolverAttempts}, " +
                        "lat=${score.averageResolverLatencyMillis}ms",
                )
            }
        }
    }

    fun shouldPreferYandexResolverScore(
        local: ResolverBenchmarkScore,
        yandex: ResolverBenchmarkScore,
        onLog: (String) -> Unit = {},
    ): Boolean {
        if (!local.isUsable) {
            val yandexWins = yandex.isUsable
            onLog(
                if (yandexWins) {
                    "Local resolver'ы нестабильны, выбираю Yandex"
                } else {
                    "Yandex тоже нестабилен, оставляю local для повторной проверки"
                },
            )
            return yandexWins
        }
        if (!yandex.isUsable) {
            onLog("Yandex resolver'ы не прошли стабильность, оставляю local")
            return false
        }

        val clearSpeedAdvantage = yandex.speedBytesPerSecond >=
            (local.speedBytesPerSecond * ResolverBenchmarkYandexSpeedMultiplier)
        val notLessStable = yandex.healthSuccesses >= local.healthSuccesses &&
            yandex.speedSuccessfulSamples >= local.speedSuccessfulSamples &&
            yandex.resolverSuccessRatePercent >= local.resolverSuccessRatePercent
        val latencyAcceptable = local.averageResolverLatencyMillis <= 0L ||
            yandex.averageResolverLatencyMillis <= 0L ||
            yandex.averageResolverLatencyMillis <=
            (local.averageResolverLatencyMillis * ResolverBenchmarkYandexLatencyMultiplier)

        onLog(
            "Resolver decision: yandexSpeedX=" +
                if (local.speedBytesPerSecond > 0L) {
                    "%.2f".format(Locale.US, yandex.speedBytesPerSecond.toDouble() / local.speedBytesPerSecond)
                } else {
                    "inf"
                } +
                ", stable=$notLessStable, latencyOk=$latencyAcceptable",
        )
        return clearSpeedAdvantage && notLessStable && latencyAcceptable
    }

    fun currentResolverEntries(): List<String> {
        return validateResolverText(uiState.settings.resolverText).normalizedResolvers
    }

    fun yandexResolverEntries(): List<String> = YandexDnsFallbackResolvers

    fun isYandexResolverSet(resolvers: List<String> = currentResolverEntries()): Boolean {
        return resolvers.isNotEmpty() && resolvers.all { it in YandexDnsFallbackResolvers }
    }

    fun shouldRunResolverBenchmark(): Boolean {
        val resolvers = currentResolverEntries()
        if (resolvers.isEmpty() || resolvers == YandexDnsFallbackResolvers) {
            return false
        }
        return readResolverBenchmarkWinnerId(uiState.settings.operatorCode, resolvers) == null
    }

    fun applyCachedResolverBenchmarkWinner(onLog: (String) -> Unit = {}): Boolean {
        val localResolvers = currentResolverEntries()
        if (localResolvers.isEmpty() || localResolvers == YandexDnsFallbackResolvers) {
            return false
        }
        val winnerId = readResolverBenchmarkWinnerId(uiState.settings.operatorCode, localResolvers) ?: return false
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
        prewarmStormDnsRuntime(updatedSettings)
    }

    fun cacheResolverBenchmarkWinner(
        localResolvers: List<String>,
        winnerId: String,
        winnerResolvers: List<String>,
        onLog: (String) -> Unit = {},
    ) {
        val normalizedLocalResolvers = validateResolverText(localResolvers.joinToString(separator = "\n")).normalizedResolvers
        val normalizedWinnerResolvers = validateResolverText(winnerResolvers.joinToString(separator = "\n")).normalizedResolvers
        if (normalizedLocalResolvers.isEmpty() || normalizedWinnerResolvers.isEmpty()) {
            return
        }
        fastResolverStore.edit()
            .putString(
                resolverBenchmarkWinnerKey(uiState.settings.operatorCode, normalizedLocalResolvers),
                winnerId,
            )
            .putString(
                resolverBenchmarkWinnerResolversKey(uiState.settings.operatorCode, normalizedLocalResolvers),
                normalizedWinnerResolvers.joinToString(separator = "\n"),
            )
            .apply()
        onLog("Сохранен лучший набор resolver'ов: ${resolverBenchmarkLabel(winnerId)}")
    }

    suspend fun reportCurrentResolversToRegistry(onLog: (String) -> Unit = {}) {
        val stateSnapshot = uiState
        val operatorCode = normalizeOperatorCode(stateSnapshot.settings.operatorCode)
        val runtimeResolvers = WhiteDnsScannerResultStore.normalizeResolverEntries(
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
            val discoveryNetworks = dnsDiscoveryNetworks(appContext.getSystemService(ConnectivityManager::class.java))
            runCatching {
                postRegistryResolvers(
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

    private fun postRegistryResolvers(
        operatorCode: String,
        resolvers: List<String>,
        discoveryNetworks: List<DnsDiscoveryNetwork>,
        socksProxyPort: Int,
    ): String {
        val normalizedOperatorCode = normalizeOperatorCode(operatorCode)
        val normalizedResolvers = resolvers
            .mapNotNull(::registryReportableResolverOrNull)
            .distinct()
            .take(TargetResolverCount)
        if (normalizedResolvers.isEmpty()) {
            return "Registry report skipped: нет resolver'ов для отправки"
        }
        val payload = JSONObject()
            .put("operator", normalizedOperatorCode)
            .put("resolvers", JSONArray(normalizedResolvers))
            .put("local_dns", JSONArray(discoveryNetworks.flatMap { it.dnsResolvers }.distinct()))
            .put("seed_ips", JSONArray(discoveryNetworks.flatMap { it.seedIps }.distinct().take(16)))
        val socksProxy = Proxy(
            Proxy.Type.SOCKS,
            InetSocketAddress("127.0.0.1", socksProxyPort),
        )
        val connection = (URL(ResolverRegistryReportUrl).openConnection(socksProxy) as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = RegistryConnectTimeoutMillis
            readTimeout = RegistryReadTimeoutMillis
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", RegistryUserAgent)
        }
        try {
            connection.outputStream.use { stream ->
                stream.write(payload.toString().toByteArray(Charsets.UTF_8))
            }
            val responseCode = connection.responseCode
            val responseBody = runCatching {
                val stream = if (responseCode in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream ?: connection.inputStream
                }
                stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            }.getOrDefault("")
            if (responseCode !in 200..299) {
                error("HTTP $responseCode ${responseBody.take(120)}".trim())
            }
        } finally {
            connection.disconnect()
        }
        return "Registry report accepted via tunnel: operator=$normalizedOperatorCode, resolvers=${normalizedResolvers.joinToString()}"
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
            if (uiState.connectionStatus == ConnectionStatus.CONNECTING) {
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
        }
    }

    fun beginConnection() {
        if (uiState.connectionStatus != ConnectionStatus.DISCONNECTED) {
            return
        }

        connectJob?.cancel()
        statsJob?.cancel()
        runtimeRefreshJob?.cancel()
        verificationJob?.cancel()
        serverTestJob?.cancel()
        val sessionId = UUID.randomUUID().toString()
        activeRuntimeSessionId = sessionId
        uiState = uiState.copy(
            connectionStatus = ConnectionStatus.CONNECTING,
            connectionStats = ConnectionStats(),
            resolverRuntimeState = ResolverRuntimeState(),
            connectionProgress = ConnectionProgressState(phase = "preparing", percent = 3),
            connectionVerification = ConnectionVerificationState(),
            autoTuneTrialResults = emptyList(),
            serverTestState = ServerTestState(),
            connectionLogs = listOf("Starting WhiteZia"),
        )
        activeVpnTrafficInterfaceName = null
        resetTrafficAccounting()
        trafficBaseline = currentTrafficSnapshot()
        lastTrafficSnapshot = trafficBaseline
        resetSocksStreamTracker()
        resetRuntimeUiThrottles()

        connectJob = viewModelScope.launch {
            withContext(Dispatchers.IO) {
                stopAutoTuneTrialManagers()
                stopServerTestManagers()
            }
            val settings = uiState.settings.syncSelectedConnectionProfileFields()
            val canStartWithAmnezia = settings.transportMode == WhiteDnsOptions.TransportAuto &&
                settings.amneziaWgConfig.isNotBlank()
            if (!canStartWithAmnezia && settings.resolve().resolverEntries.isEmpty()) {
                appendLog("Resolvers are required to connect")
                activeRuntimeSessionId = ""
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
            if (serverProfile == null) {
                appendLog(
                    if (connectionProfile.serverMode == "custom") {
                        "Custom StormDNS domain and encryption key are required"
                    } else {
                        "No StormDNS server profile configured"
                    },
                )
                activeRuntimeSessionId = ""
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
            val resolvedRuntimeSettings = runtimeSettings.resolve()
            val useParallelTest = false
            uiState = uiState.copy(
                settings = settings,
                activeConnectionProfileId = connectionProfile.id,
            )
            val started = if (useParallelTest) {
                runParallelTestConnection(
                    sessionId = sessionId,
                    baseSettings = settings,
                    connectionProfile = connectionProfile,
                    serverProfile = serverProfile,
                )
            } else {
                launchRuntime(
                    sessionId = sessionId,
                    connectionProfile = connectionProfile,
                    serverProfile = serverProfile,
                    runtimeSettings = runtimeSettings,
                )
            }

            if (started) {
                uiState = uiState.copy(
                    networkIpAddress = findDeviceNetworkIpAddress(),
                    activeConnectionProfileId = connectionProfile.id,
                )
            } else {
                withContext(Dispatchers.IO) {
                    stopAllRuntimeServices()
                }
                activeProxyListenPort = WhiteDnsRuntimeProxy.ListenPortInt
                activeRuntimeSessionId = ""
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
    }

    private suspend fun launchRuntime(
        sessionId: String,
        connectionProfile: ConnectionProfile,
        serverProfile: StormDnsServerProfile,
        runtimeSettings: WhiteDnsSettings,
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
                appendLog(
                    if (connectionProfile.serverMode == "custom") {
                        "Using custom StormDNS server"
                    } else {
                        "Using configured StormDNS server"
                    },
                )
                appendLog("Connection mode: $modeLabel")
                if (resolvedSettings.connectionMode == "vpn") {
                    appendLog("Starting full-device VPN service")
                    WhiteDnsVpnService.start(
                        context = getApplication<Application>().applicationContext,
                        sessionId = sessionId,
                        serverProfile = serverProfile,
                        settings = runtimeSettings,
                    )
                } else {
                    appendLog("Starting local proxy service")
                    WhiteDnsProxyService.start(
                        context = getApplication<Application>().applicationContext,
                        sessionId = sessionId,
                        serverProfile = serverProfile,
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

    private suspend fun runParallelTestConnection(
        sessionId: String,
        baseSettings: WhiteDnsSettings,
        connectionProfile: ConnectionProfile,
        serverProfile: StormDnsServerProfile,
    ): Boolean {
        return runParallelProxyTestConnection(
            sessionId = sessionId,
            baseSettings = baseSettings,
            connectionProfile = connectionProfile,
            serverProfile = serverProfile,
        )
    }

    private suspend fun runParallelProxyTestConnection(
        sessionId: String,
        baseSettings: WhiteDnsSettings,
        connectionProfile: ConnectionProfile,
        serverProfile: StormDnsServerProfile,
    ): Boolean = coroutineScope {
        val finalConnectionMode = baseSettings.runtimeConnectionSettings().resolve().connectionMode
        val finalModeLabel = if (finalConnectionMode == WhiteDnsRuntimeStateStore.ModeVpn) {
            "Full VPN"
        } else {
            "Proxy Mode"
        }
        val operatorCode = normalizeOperatorCode(baseSettings.operatorCode)
        val previousSelectedConfigId = lastAutoTuneWinnerConfigId.ifBlank {
            readCachedAutoTuneWinnerConfigId(operatorCode).orEmpty()
        }.ifBlank {
            uiState.autoTuneTrialResults
                .firstOrNull { it.selected }
                ?.configId
                .orEmpty()
        }.ifBlank { null }
        val selectedConfigs = buildParallelTestConfigs(baseSettings)
        if (selectedConfigs.isEmpty()) {
            appendLog("Parallel Test: no configuration selected")
            uiState = uiState.copy(
                connectionProgress = ConnectionProgressState(),
                connectionVerification = ConnectionVerificationState(
                    status = ConnectionVerificationStatus.Failed,
                    message = "Parallel Test failed: no configuration selected",
                    checkedAtMillis = System.currentTimeMillis(),
                ),
            )
            return@coroutineScope false
        }

        val trialPlans = selectedConfigs.map { config ->
            AutoTuneTrialPlan(
                config = config,
                settings = config.userSettings
                    .copy(
                        connectionMode = WhiteDnsRuntimeStateStore.ModeProxy,
                        listenIp = WhiteDnsRuntimeProxy.ListenIp,
                        listenPort = AutoTuneUnassignedPort.toString(),
                        httpProxyEnabled = false,
                        localDnsEnabled = false,
                        trafficWarmupEnabled = false,
                        autoTuneEnabled = true,
                    )
                    .syncSelectedConnectionProfileFields(),
                result = AutoTuneTrialResult(
                    configId = config.id,
                    label = config.label,
                    listenIp = WhiteDnsRuntimeProxy.ListenIp,
                    listenPort = AutoTuneUnassignedPort,
                    status = "pending",
                    message = "Waiting",
                ),
            )
        }
        val trialManagers = trialPlans.associate { plan ->
            plan.config.id to StormDnsProcessManager(appContext)
        }

        setAutoTuneTrialManagers(trialManagers.values.toList())
        uiState = uiState.copy(
            settings = baseSettings,
            connectionStatus = ConnectionStatus.CONNECTING,
            connectionStats = ConnectionStats(),
            resolverRuntimeState = ResolverRuntimeState(),
            connectionProgress = ConnectionProgressState(
                phase = "autotune",
                percent = 5,
                completed = 0,
                total = trialPlans.size,
            ),
            connectionVerification = ConnectionVerificationState(
                status = ConnectionVerificationStatus.Checking,
                message = "Parallel Test: testing ${trialPlans.size} SOCKS configurations before $finalModeLabel",
            ),
            autoTuneTrialResults = trialPlans.map { it.result },
            activeConnectionProfileId = connectionProfile.id,
        )
        appendLog(
            "Parallel Test: testing ${trialPlans.size} SOCKS configurations " +
                "in batches of $AutoTuneMaxConcurrentTrials before $finalModeLabel",
        )

        try {
            val resolverDiscoveryPlan = selectResolverDiscoveryPlan(trialPlans)
            val resolverDiscoveryResult = runParallelAutoTuneResolverDiscoveryTrial(
                plan = resolverDiscoveryPlan,
                manager = trialManagers.getValue(resolverDiscoveryPlan.config.id),
                serverProfile = serverProfile,
            )
            val resolverSubset = resolverDiscoveryResult.resolverEntries
            val remainingTrialPlans = trialPlans.filterNot { it.config.id == resolverDiscoveryPlan.config.id }
            val remainingTrialPlansWithResolvers = if (resolverSubset.isNotEmpty()) {
                appendLog(
                    "Parallel Test: testing remaining configs with ${resolverSubset.size} " +
                        "resolvers from ${resolverDiscoveryPlan.config.label}",
                )
                remainingTrialPlans.map { plan -> plan.withResolverEntries(resolverSubset) }
            } else {
                appendLog("Parallel Test: no reusable resolver subset found; using selected resolver list")
                remainingTrialPlans
            }
            val lowerUsagePlans = remainingTrialPlansWithResolvers.filterNot { it.config.highUsage }
            val highUsagePlans = remainingTrialPlansWithResolvers.filter { it.config.highUsage }
            val trialResults = buildList {
                add(resolverDiscoveryResult.result)
                if (lowerUsagePlans.isNotEmpty()) {
                    addAll(
                        runParallelAutoTunePlanGroup(
                            plans = lowerUsagePlans,
                            trialManagers = trialManagers,
                            serverProfile = serverProfile,
                            groupLabel = "conservative",
                        ),
                    )
                }

                if (highUsagePlans.isNotEmpty()) {
                    addAll(
                        runParallelAutoTunePlanGroup(
                            plans = highUsagePlans,
                            trialManagers = trialManagers,
                            serverProfile = serverProfile,
                            groupLabel = "high-usage",
                        ),
                    )
                }
            }
            val selectedResult = selectParallelAutoTuneResult(
                trialResults = trialResults,
                previousSelectedConfigId = previousSelectedConfigId,
            ) ?: run {
                    appendLog("Parallel Test: no SOCKS configuration became ready")
                    uiState = uiState.copy(
                        connectionProgress = ConnectionProgressState(),
                        connectionVerification = ConnectionVerificationState(
                            status = ConnectionVerificationStatus.Failed,
                            message = "Parallel Test failed: no SOCKS configuration became ready",
                            checkedAtMillis = System.currentTimeMillis(),
                        ),
                    )
                    return@coroutineScope false
                }

            val selectedUserSettings = selectedResult.config.userSettings
                .copy(
                    connectionMode = finalConnectionMode,
                    autoTuneEnabled = false,
                )
                .syncSelectedConnectionProfileFields()
            val selectedRuntimeSettings = selectedUserSettings.runtimeConnectionSettings()
            activeRuntimeSessionId = sessionId
            activeProxyListenPort = selectedRuntimeSettings.resolve().listenPort
            lastAutoTuneWinnerConfigId = selectedResult.config.id
            cacheAutoTuneWinnerConfigId(operatorCode, selectedResult.config.id)
            uiState = uiState.copy(
                settings = baseSettings,
                connectionStatus = ConnectionStatus.CONNECTING,
                connectionStats = ConnectionStats(),
                resolverRuntimeState = ResolverRuntimeState(),
                connectionProgress = ConnectionProgressState(phase = "preparing", percent = 3),
                connectionVerification = ConnectionVerificationState(),
                autoTuneTrialResults = uiState.autoTuneTrialResults.map { result ->
                    result.copy(selected = result.configId == selectedResult.config.id)
                },
                activeConnectionProfileId = connectionProfile.id,
            )
            appendLog(
                "Parallel Test: selected ${selectedResult.config.label} for this connection " +
                    "(${formatTrafficSpeed(selectedResult.scoreBytesPerSecond)}, " +
                    "ping ${formatAutoTuneLatency(selectedResult.pingMillis)}); starting $finalModeLabel",
            )
            launchRuntime(
                sessionId = sessionId,
                connectionProfile = connectionProfile,
                serverProfile = serverProfile,
                runtimeSettings = selectedRuntimeSettings,
            )
        } finally {
            withContext(Dispatchers.IO) {
                stopAutoTuneTrialManagers()
            }
        }
    }

    private fun buildParallelTestConfigs(baseSettings: WhiteDnsSettings): List<AutoTuneTrialConfig> {
        val advancedProfiles = baseSettings.normalizedAdvancedProfiles()
        val selectedConfigIds = WhiteDnsParallelTest.normalizeConfigIds(
            configIds = baseSettings.parallelTestSelectedConfigIds,
            advancedProfiles = advancedProfiles,
            includeAggressive = baseSettings.parallelTestAggressivePresetsEnabled,
        )
        val aggressiveConfigIds = WhiteDnsParallelTest.aggressiveConfigIds.toSet()
        return selectedConfigIds.mapNotNull { configId ->
            WhiteDnsParallelTest.presetIdFromConfigId(configId)?.let { presetId ->
                val preset = WhiteDnsAutoTunePresets.all.firstOrNull { it.id == presetId } ?: return@mapNotNull null
                val presetSettings = baseSettings
                    .applyAutoTunePreset(preset)
                    .copy(
                        autoTuneEnabled = true,
                        parallelTestSelectedConfigIds = selectedConfigIds,
                    )
                    .syncSelectedConnectionProfileFields()
                return@mapNotNull AutoTuneTrialConfig(
                    id = configId,
                    label = preset.label,
                    userSettings = presetSettings,
                    highUsage = configId in aggressiveConfigIds,
                )
            }

            WhiteDnsParallelTest.settingProfileIdFromConfigId(configId)?.let { profileId ->
                val profile = advancedProfiles.firstOrNull { it.id == profileId } ?: return@mapNotNull null
                val profileSettings = baseSettings
                    .applyAdvancedProfile(profile)
                    .copy(
                        autoTuneEnabled = true,
                        parallelTestSelectedConfigIds = selectedConfigIds,
                    )
                    .syncSelectedConnectionProfileFields()
                return@mapNotNull AutoTuneTrialConfig(
                    id = configId,
                    label = profile.name.ifBlank { "Setting" },
                    userSettings = profileSettings,
                    highUsage = profileSettings.isHighUsageParallelConfig(),
                )
            }

            null
        }.take(WhiteDnsParallelTest.MaxSelectedConfigs)
    }

    private fun selectResolverDiscoveryPlan(plans: List<AutoTuneTrialPlan>): AutoTuneTrialPlan {
        val defaultConfigId = WhiteDnsParallelTest.defaultConfigIds.firstOrNull()
        return plans.firstOrNull { plan -> plan.config.id == defaultConfigId }
            ?: plans.firstOrNull { plan -> !plan.config.highUsage }
            ?: plans.first()
    }

    private suspend fun runParallelAutoTuneResolverDiscoveryTrial(
        plan: AutoTuneTrialPlan,
        manager: StormDnsProcessManager,
        serverProfile: StormDnsServerProfile,
    ): AutoTuneResolverDiscoveryResult {
        val fullResolverCount = plan.settings.resolve().resolverEntries.size
        val port = withContext(Dispatchers.IO) {
            allocateRandomLocalPorts(count = 1).first()
        }
        val discoveryPlan = plan.withTrialPort(port)
        val resolverCollector = AutoTuneResolverCollector()
        withContext(Dispatchers.Main.immediate) {
            updateAutoTuneTrialResult(discoveryPlan.result.copy(message = "Finding resolvers"))
            uiState = uiState.copy(
                connectionVerification = ConnectionVerificationState(
                    status = ConnectionVerificationStatus.Checking,
                    message = "Parallel Test: finding reusable resolvers with ${discoveryPlan.config.label}",
                ),
            )
        }
        appendLog(
            "Parallel Test: finding reusable resolvers with ${discoveryPlan.config.label} " +
                "from $fullResolverCount selected resolvers",
        )

        return try {
            val startup = withContext(Dispatchers.IO) {
                startParallelAutoTuneTrial(
                    plan = discoveryPlan,
                    manager = manager,
                    serverProfile = serverProfile,
                    resolverCollector = resolverCollector,
                )
            }
            val result = if (startup.ready) {
                delay(AutoTuneMeasurementSettleMillis)
                measureParallelAutoTuneTrial(startup)
            } else {
                startup.result
            }
            val resolverEntries = if (startup.ready) {
                minimumParallelResolverEntries(resolverCollector.preferredResolvers(AutoTuneResolverSubsetMinCount))
            } else {
                emptyList()
            }
            if (resolverEntries.isNotEmpty()) {
                appendLog(
                    "Parallel Test: ${discoveryPlan.config.label} found ${resolverEntries.size} " +
                        "reusable resolvers for config testing",
                )
            }
            AutoTuneResolverDiscoveryResult(
                result = result,
                resolverEntries = resolverEntries,
            )
        } finally {
            withContext(Dispatchers.IO) {
                manager.stop()
            }
            val openPorts = waitForLocalPortsClosed(listOf(port))
            if (openPorts.isNotEmpty()) {
                appendLog("Parallel Test: ports still closing: ${openPorts.joinToString()}")
            }
        }
    }

    private suspend fun runParallelAutoTunePlanGroup(
        plans: List<AutoTuneTrialPlan>,
        trialManagers: Map<String, StormDnsProcessManager>,
        serverProfile: StormDnsServerProfile,
        groupLabel: String,
    ): List<AutoTuneResult> = coroutineScope {
        val batches = plans.chunked(AutoTuneMaxConcurrentTrials)
        val usedTrialPorts = mutableSetOf<Int>()
        val results = mutableListOf<AutoTuneResult>()
        batches.forEachIndexed { batchIndex, batch ->
            val batchNumber = batchIndex + 1
            val batchPorts = withContext(Dispatchers.IO) {
                allocateRandomLocalPorts(
                    count = batch.size,
                    additionalBlockedPorts = usedTrialPorts,
                )
            }
            usedTrialPorts += batchPorts
            val batchWithPorts = batch.mapIndexed { index, plan ->
                plan.withTrialPort(batchPorts[index])
            }
            val batchManagers = batchWithPorts.map { plan -> trialManagers.getValue(plan.config.id) }
            withContext(Dispatchers.Main.immediate) {
                batchWithPorts.forEach { plan ->
                    updateAutoTuneTrialResult(plan.result)
                }
                uiState = uiState.copy(
                    connectionVerification = ConnectionVerificationState(
                        status = ConnectionVerificationStatus.Checking,
                        message = "Parallel Test: testing $groupLabel batch $batchNumber/${batches.size}",
                    ),
                )
            }
            appendLog(
                "Parallel Test: testing $groupLabel batch $batchNumber/${batches.size} " +
                    "(${batch.size} profiles)",
            )

            try {
                val startups = batchWithPorts.map { plan ->
                    async(Dispatchers.IO) {
                        startParallelAutoTuneTrial(
                            plan = plan,
                            manager = trialManagers.getValue(plan.config.id),
                            serverProfile = serverProfile,
                        )
                    }
                }.awaitAll()

                val readyStartups = startups.filter { it.ready }
                results += startups.filterNot { it.ready }.map { it.result }
                if (readyStartups.isNotEmpty()) {
                    withContext(Dispatchers.Main.immediate) {
                        uiState = uiState.copy(
                            connectionVerification = ConnectionVerificationState(
                                status = ConnectionVerificationStatus.Checking,
                                message = "Parallel Test: measuring $groupLabel batch $batchNumber/${batches.size}",
                            ),
                        )
                    }
                    delay(AutoTuneMeasurementSettleMillis)
                    results += readyStartups.map { startup ->
                        async(Dispatchers.IO) {
                            measureParallelAutoTuneTrial(startup)
                        }
                    }.awaitAll()
                }
            } finally {
                withContext(Dispatchers.IO) {
                    batchManagers.forEach { manager ->
                        runCatching {
                            manager.stop()
                        }
                    }
                }
                val openPorts = waitForLocalPortsClosed(batchWithPorts.map { it.result.listenPort })
                if (openPorts.isNotEmpty()) {
                    appendLog("Parallel Test: ports still closing: ${openPorts.joinToString()}")
                }
            }
        }
        results
    }

    private fun AutoTuneTrialPlan.withTrialPort(port: Int): AutoTuneTrialPlan {
        return copy(
            settings = settings
                .copy(listenPort = port.toString()),
            result = result.copy(listenPort = port),
        )
    }

    private fun AutoTuneTrialPlan.withResolverEntries(resolverEntries: List<String>): AutoTuneTrialPlan {
        return copy(
            settings = settings.copy(
                selectedResolverProfileId = "",
                resolverText = resolverEntries.joinToString(separator = "\n"),
            ),
        )
    }

    private suspend fun startParallelAutoTuneTrial(
        plan: AutoTuneTrialPlan,
        manager: StormDnsProcessManager,
        serverProfile: StormDnsServerProfile,
        resolverCollector: AutoTuneResolverCollector? = null,
    ): AutoTuneTrialStartup {
        val startupFailure = AtomicReference<String?>(null)
        return try {
            withContext(Dispatchers.Main.immediate) {
                updateAutoTuneTrialResult(plan.result.copy(status = "starting", message = "Starting SOCKS proxy"))
            }
            manager.start(serverProfile, plan.settings) { line ->
                resolverCollector?.observe(line)
                detectStormDnsStartupFailure(line)?.let { failure ->
                    startupFailure.compareAndSet(null, failure)
                }
            }
            val ready = waitForAutoTuneTrialReady(
                manager = manager,
                listenPort = plan.result.listenPort,
                startupFailure = startupFailure,
            )
            if (!ready) {
                val failureMessage = startupFailure.get() ?: "SOCKS proxy did not become ready"
                val failedResult = plan.result.copy(status = "failed", message = failureMessage)
                withContext(Dispatchers.Main.immediate) {
                    updateAutoTuneTrialResult(failedResult)
                    updateAutoTuneProgress()
                }
                return AutoTuneTrialStartup(
                    plan = plan,
                    manager = manager,
                    ready = false,
                    result = autoTuneResultForPlan(plan, ready = false),
                )
            }

            withContext(Dispatchers.Main.immediate) {
                updateAutoTuneTrialResult(plan.result.copy(status = "listening", message = "SOCKS proxy ready"))
            }
            AutoTuneTrialStartup(
                plan = plan,
                manager = manager,
                ready = true,
                result = autoTuneResultForPlan(plan, ready = true),
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            val message = error.message ?: error::class.java.simpleName
            withContext(Dispatchers.Main.immediate) {
                updateAutoTuneTrialResult(plan.result.copy(status = "failed", message = message))
                updateAutoTuneProgress()
            }
            AutoTuneTrialStartup(
                plan = plan,
                manager = manager,
                ready = false,
                result = autoTuneResultForPlan(plan, ready = false),
            )
        }
    }

    private suspend fun measureParallelAutoTuneTrial(startup: AutoTuneTrialStartup): AutoTuneResult {
        val plan = startup.plan
        val resolvedSettings = plan.settings.resolve()
        val probeResults = mutableListOf<Long>()
        val pingResults = mutableListOf<Long>()
        repeat(AutoTuneMeasurementProbeCount) { probeIndex ->
            withContext(Dispatchers.Main.immediate) {
                updateAutoTuneTrialResult(
                    plan.result.copy(
                        status = "measuring",
                        message = "Measuring ${probeIndex + 1}/$AutoTuneMeasurementProbeCount",
                    ),
                )
            }
            val probeResult = WhiteDnsTrafficWarmup.measureDownloadThroughput(resolvedSettings)
            if (probeResult != null && probeResult.bytesPerSecond > 0L) {
                probeResults += probeResult.bytesPerSecond
                pingResults += probeResult.latencyMillis
            }
            if (probeIndex < AutoTuneMeasurementProbeCount - 1) {
                delay(AutoTuneMeasurementProbeDelayMillis)
            }
        }
        val score = medianLong(probeResults) ?: 0L
        val pingMillis = medianLong(pingResults)
        val completedResult = plan.result.copy(
            status = "ready",
            speedBytesPerSecond = score,
            pingMillis = pingMillis,
            message = if (score > 0L) {
                "Measured ${probeResults.size}/$AutoTuneMeasurementProbeCount"
            } else {
                "No speed result"
            },
        )
        withContext(Dispatchers.Main.immediate) {
            updateAutoTuneTrialResult(completedResult)
            updateAutoTuneProgress()
        }
        appendLog(
            "Parallel Test: ${plan.config.label} ${plan.result.listenIp}:${plan.result.listenPort} " +
                "median ${formatTrafficSpeed(score)}, ping ${formatAutoTuneLatency(pingMillis)} " +
                "(${probeResults.size}/$AutoTuneMeasurementProbeCount probes)",
        )
        return AutoTuneResult(
            config = plan.config,
            listenIp = plan.result.listenIp,
            listenPort = plan.result.listenPort,
            scoreBytesPerSecond = score,
            pingMillis = pingMillis,
            ready = true,
        )
    }

    private fun selectParallelAutoTuneResult(
        trialResults: List<AutoTuneResult>,
        previousSelectedConfigId: String?,
    ): AutoTuneResult? {
        val readyResults = trialResults.filter { it.ready }
        val positiveResults = readyResults.filter { it.scoreBytesPerSecond > 0L }
        if (positiveResults.isEmpty()) {
            return readyResults.bestAutoTuneResult()
        }

        val fastestResult = positiveResults.bestAutoTuneResult() ?: return null
        val bestConservativeResult = positiveResults
            .filterNot { it.config.highUsage }
            .bestAutoTuneResult()
        val bestHighUsageResult = positiveResults
            .filter { it.config.highUsage }
            .bestAutoTuneResult()
        var selectedResult = if (
            bestConservativeResult != null &&
            bestHighUsageResult != null &&
            bestHighUsageResult.config.id == fastestResult.config.id &&
            !isAtLeastPercentBetter(
                candidate = bestHighUsageResult.scoreBytesPerSecond,
                baseline = bestConservativeResult.scoreBytesPerSecond,
                percent = AutoTuneSelectionHysteresisPercent,
            )
        ) {
            appendLog(
                "Parallel Test: high-usage winner was under " +
                    "$AutoTuneSelectionHysteresisPercent% faster; preferring conservative " +
                    "${bestConservativeResult.config.label}",
            )
            bestConservativeResult
        } else {
            fastestResult
        }

        val previousResult = previousSelectedConfigId?.let { previousId ->
            positiveResults.firstOrNull { it.config.id == previousId }
        }
        if (
            previousResult != null &&
            previousResult.config.id != selectedResult.config.id &&
            !isAtLeastPercentBetter(
                candidate = selectedResult.scoreBytesPerSecond,
                baseline = previousResult.scoreBytesPerSecond,
                percent = AutoTuneSelectionHysteresisPercent,
            )
        ) {
            appendLog(
                "Parallel Test: kept previous winner ${previousResult.config.label}; " +
                    "new result was under $AutoTuneSelectionHysteresisPercent% better",
            )
            selectedResult = previousResult
        }

        return selectedResult
    }

    private fun List<AutoTuneResult>.bestAutoTuneResult(): AutoTuneResult? {
        return sortedWith(
            compareByDescending<AutoTuneResult> { it.scoreBytesPerSecond }
                .thenBy { it.pingMillis ?: Long.MAX_VALUE }
                .thenBy { it.config.highUsage }
                .thenBy { it.config.label },
        ).firstOrNull()
    }

    private fun isAtLeastPercentBetter(
        candidate: Long,
        baseline: Long,
        percent: Int,
    ): Boolean {
        if (baseline <= 0L) {
            return candidate > 0L
        }
        return candidate * 100L >= baseline * (100L + percent)
    }

    private fun medianLong(values: List<Long>): Long? {
        if (values.isEmpty()) {
            return null
        }
        val sortedValues = values.sorted()
        val middleIndex = sortedValues.size / 2
        return if (sortedValues.size % 2 == 1) {
            sortedValues[middleIndex]
        } else {
            (sortedValues[middleIndex - 1] + sortedValues[middleIndex]) / 2L
        }
    }

    private fun WhiteDnsSettings.isHighUsageParallelConfig(): Boolean {
        val uploadDuplicationCount = uploadDuplication.toIntOrNull() ?: 0
        val downloadDuplicationCount = downloadDuplication.toIntOrNull() ?: 0
        return uploadDuplicationCount >= HighUsageUploadDuplicationThreshold ||
            downloadDuplicationCount >= HighUsageDownloadDuplicationThreshold
    }

    private fun minimumParallelResolverEntries(rawResolvers: List<String>): List<String> {
        return validateResolverText(rawResolvers.joinToString(separator = "\n"))
            .normalizedResolvers
            .take(AutoTuneResolverSubsetMaxCount)
    }

    private suspend fun waitForAutoTuneTrialReady(
        manager: StormDnsProcessManager,
        listenPort: Int,
        startupFailure: AtomicReference<String?>,
    ): Boolean {
        val deadline = System.currentTimeMillis() + AutoTuneReadyTimeoutMillis
        while (System.currentTimeMillis() < deadline) {
            if (startupFailure.get() != null) {
                return false
            }
            if (!manager.isRunning()) {
                return false
            }
            if (canConnectToLocalPort(listenPort)) {
                return true
            }
            delay(AutoTuneSignalPollMillis)
        }
        return false
    }

    private fun updateAutoTuneTrialResult(result: AutoTuneTrialResult) {
        val currentResults = uiState.autoTuneTrialResults
        val resultIndex = currentResults.indexOfFirst { it.configId == result.configId }
        val nextResults = if (resultIndex >= 0) {
            currentResults.toMutableList().also { results ->
                results[resultIndex] = result
            }
        } else {
            currentResults + result
        }
        uiState = uiState.copy(autoTuneTrialResults = nextResults)
    }

    private fun updateAutoTuneProgress() {
        val results = uiState.autoTuneTrialResults
        val completed = results.count { it.status == "ready" || it.status == "failed" }
        val total = results.size.coerceAtLeast(1)
        uiState = uiState.copy(
            connectionProgress = ConnectionProgressState(
                phase = "autotune",
                percent = ((completed * 100) / total).coerceIn(5, 99),
                completed = completed,
                total = results.size,
            ),
            connectionVerification = ConnectionVerificationState(
                status = ConnectionVerificationStatus.Checking,
                message = "Parallel Test: measured $completed/${results.size} SOCKS configurations",
            ),
        )
    }

    private fun autoTuneResultForPlan(
        plan: AutoTuneTrialPlan,
        ready: Boolean,
    ): AutoTuneResult {
        return AutoTuneResult(
            config = plan.config,
            listenIp = plan.result.listenIp,
            listenPort = plan.result.listenPort,
            scoreBytesPerSecond = 0L,
            pingMillis = null,
            ready = ready,
        )
    }

    private suspend fun waitForLocalPortsClosed(ports: List<Int>): List<Int> = withContext(Dispatchers.IO) {
        val trackedPorts = ports.filter { it in 1..65535 }.distinct()
        if (trackedPorts.isEmpty()) {
            return@withContext emptyList()
        }
        val deadlineMillis = System.currentTimeMillis() + AutoTunePortReleaseTimeoutMillis
        while (System.currentTimeMillis() < deadlineMillis) {
            if (trackedPorts.none { port -> canConnectToLocalPort(port) }) {
                return@withContext emptyList()
            }
            delay(AutoTunePortReleasePollMillis)
        }
        trackedPorts.filter { port -> canConnectToLocalPort(port) }
    }

    private fun allocateRandomLocalPorts(
        count: Int,
        additionalBlockedPorts: Set<Int> = emptySet(),
    ): List<Int> {
        val ports = linkedSetOf<Int>()
        val blockedPorts = setOf(
            WhiteDnsRuntimeProxy.ListenPortInt,
            WhiteDnsRuntimeProxy.HttpProxyPortInt,
            WhiteDnsRuntimeProxy.LocalDnsPortInt,
        ) + additionalBlockedPorts
        while (ports.size < count) {
            val port = ServerSocket(0).use { socket ->
                socket.localPort
            }
            if (port !in blockedPorts) {
                ports += port
            }
        }
        return ports.toList()
    }

    private fun setAutoTuneTrialManagers(managers: List<StormDnsProcessManager>) {
        synchronized(autoTuneTrialManagersLock) {
            autoTuneTrialManagers = managers
        }
    }

    private fun stopAutoTuneTrialManagers() {
        val managers = synchronized(autoTuneTrialManagersLock) {
            autoTuneTrialManagers.also {
                autoTuneTrialManagers = emptyList()
            }
        }
        managers.forEach { manager ->
            runCatching {
                manager.stop()
            }
        }
    }

    private fun detectStormDnsStartupFailure(line: String): String? {
        val normalized = line.lowercase()
        return when {
            "no valid connections found after mtu testing" in normalized ||
                "mtu tests failed: no valid connections" in normalized ||
                "no valid connections after mtu testing" in normalized ->
                "No DNS resolver passed MTU testing"
            else -> null
        }
    }

    private fun formatAutoTuneLatency(pingMillis: Long?): String {
        return pingMillis?.let { "${it}ms" } ?: "n/a"
    }

    fun beginServerTest(serverProfileId: String? = null) {
        if (
            uiState.connectionStatus != ConnectionStatus.CONNECTED ||
            uiState.serverTestState.isRunning
        ) {
            return
        }

        serverTestJob?.cancel()
        val baseSettings = uiState.settings.syncSelectedConnectionProfileFields()
        val serverProfiles = buildServerTestProfiles(baseSettings, serverProfileId)
        val resolverEntries = connectedServerTestResolvers(baseSettings)

        if (serverProfiles.isEmpty()) {
            val message = if (serverProfileId == null) {
                "Server Test: no saved server profiles are configured"
            } else {
                "Server Test: selected server profile is not configured"
            }
            appendLog(message)
            uiState = uiState.copy(serverTestState = ServerTestState(message = message))
            return
        }
        if (resolverEntries.isEmpty()) {
            val message = "Server Test: no connected resolvers are available"
            appendLog(message)
            uiState = uiState.copy(serverTestState = ServerTestState(message = message))
            return
        }

        serverTestJob = viewModelScope.launch {
            val runtimeBaseSettings = baseSettings.runtimeConnectionSettings()
            val ports = withContext(Dispatchers.IO) {
                allocateRandomLocalPorts(serverProfiles.size)
            }
            val resolverText = resolverEntries.joinToString(separator = "\n")
            val plans = serverProfiles.mapIndexed { index, serverProfile ->
                val listenPort = ports[index]
                ServerTestPlan(
                    serverProfile = serverProfile,
                    settings = runtimeBaseSettings.copy(
                        selectedResolverProfileId = "",
                        resolverText = resolverText,
                        connectionMode = WhiteDnsRuntimeStateStore.ModeProxy,
                        listenIp = WhiteDnsRuntimeProxy.ListenIp,
                        listenPort = listenPort.toString(),
                        httpProxyEnabled = false,
                        httpProxyPort = WhiteDnsRuntimeProxy.HttpProxyPort,
                        socks5Authentication = false,
                        socksUsername = "",
                        socksPassword = "",
                        localDnsEnabled = false,
                        localDnsPort = WhiteDnsRuntimeProxy.LocalDnsPort,
                        startupMode = "resolvers",
                        trafficWarmupEnabled = false,
                        autoTuneEnabled = false,
                    ),
                    result = ServerTestResult(
                        serverId = serverProfile.id,
                        label = serverProfile.label,
                        domain = serverProfile.domain,
                        status = ServerTestStatus.Pending,
                    ),
                )
            }
            val managers = plans.associate { plan ->
                plan.serverProfile.id to StormDnsProcessManager(appContext)
            }
            setServerTestManagers(managers.values.toList())
            val startedMessage = "Server Test: testing ${plans.size} servers with ${resolverEntries.size} connected resolvers"
            appendLog(startedMessage)
            uiState = uiState.copy(
                serverTestState = ServerTestState(
                    isRunning = true,
                    startedAtMillis = System.currentTimeMillis(),
                    message = startedMessage,
                    results = plans.map { it.result },
                ),
            )

            var failureMessage: String? = null
            try {
                val startups = plans.map { plan ->
                    async(Dispatchers.IO) {
                        startServerTestPlan(
                            plan = plan,
                            manager = managers.getValue(plan.serverProfile.id),
                        )
                    }
                }.awaitAll()
                val readyStartups = startups.filter { it.ready }
                if (readyStartups.isNotEmpty()) {
                    delay(AutoTuneMeasurementSettleMillis)
                }
                readyStartups.map { startup ->
                    async(Dispatchers.IO) {
                        measureServerTestPlan(startup)
                    }
                }.awaitAll()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                failureMessage = error.message ?: error::class.java.simpleName
                appendLog("Server Test failed: $failureMessage")
            } finally {
                withContext(NonCancellable) {
                    withContext(Dispatchers.IO) {
                        stopServerTestManagers()
                    }
                    if (
                        uiState.serverTestState.isRunning &&
                        uiState.connectionStatus == ConnectionStatus.CONNECTED
                    ) {
                        val results = uiState.serverTestState.results
                        uiState = uiState.copy(
                            serverTestState = uiState.serverTestState.copy(
                                isRunning = false,
                                completedAtMillis = System.currentTimeMillis(),
                                message = failureMessage ?: serverTestSummaryMessage(results),
                            ),
                        )
                    }
                }
            }
        }
    }

    private suspend fun startServerTestPlan(
        plan: ServerTestPlan,
        manager: StormDnsProcessManager,
    ): ServerTestStartup {
        val startupFailure = AtomicReference<String?>(null)
        return try {
            withContext(Dispatchers.Main.immediate) {
                updateServerTestResultOnMain(
                    plan.result.copy(
                        status = ServerTestStatus.Starting,
                        message = "Starting",
                    ),
                )
            }
            manager.start(plan.serverProfile, plan.settings) { line ->
                detectStormDnsStartupFailure(line)?.let { failure ->
                    startupFailure.compareAndSet(null, failure)
                }
            }
            val ready = waitForAutoTuneTrialReady(
                manager = manager,
                listenPort = plan.settings.resolve().listenPort,
                startupFailure = startupFailure,
            )
            if (!ready) {
                val failedResult = plan.result.copy(
                    status = ServerTestStatus.Failed,
                    message = startupFailure.get() ?: "Server did not become ready",
                )
                withContext(Dispatchers.Main.immediate) {
                    updateServerTestResultOnMain(failedResult)
                }
                return ServerTestStartup(
                    plan = plan,
                    manager = manager,
                    ready = false,
                )
            }

            withContext(Dispatchers.Main.immediate) {
                updateServerTestResultOnMain(
                    plan.result.copy(
                        status = ServerTestStatus.Measuring,
                        message = "Measuring speed",
                    ),
                )
            }
            ServerTestStartup(
                plan = plan,
                manager = manager,
                ready = true,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            val failedResult = plan.result.copy(
                status = ServerTestStatus.Failed,
                message = error.message ?: error::class.java.simpleName,
            )
            withContext(Dispatchers.Main.immediate) {
                updateServerTestResultOnMain(failedResult)
            }
            ServerTestStartup(
                plan = plan,
                manager = manager,
                ready = false,
            )
        }
    }

    private suspend fun measureServerTestPlan(startup: ServerTestStartup): ServerTestResult {
        val plan = startup.plan
        withContext(Dispatchers.Main.immediate) {
            updateServerTestResultOnMain(
                plan.result.copy(
                    status = ServerTestStatus.Measuring,
                    message = "Measuring speed",
                ),
            )
        }
        val probeResult = WhiteDnsTrafficWarmup.measureDownloadThroughput(plan.settings.resolve())
        val speed = probeResult?.bytesPerSecond ?: 0L
        val pingMillis = probeResult?.latencyMillis
        val result = plan.result.copy(
            status = ServerTestStatus.Ready,
            speedBytesPerSecond = speed,
            pingMillis = pingMillis,
            message = if (speed > 0L) "Measured" else "No speed result",
        )
        withContext(Dispatchers.Main.immediate) {
            updateServerTestResultOnMain(result)
        }
        appendLog(
            "Server Test: ${plan.serverProfile.label} ${formatTrafficSpeed(speed)}, " +
                "ping ${formatAutoTuneLatency(pingMillis)}",
        )
        return result
    }

    private fun updateServerTestResultOnMain(result: ServerTestResult) {
        val state = uiState.serverTestState
        val resultIndex = state.results.indexOfFirst { it.serverId == result.serverId }
        val nextResults = if (resultIndex >= 0) {
            state.results.toMutableList().also { results ->
                results[resultIndex] = result
            }
        } else {
            state.results + result
        }
        uiState = uiState.copy(
            serverTestState = state.copy(
                results = nextResults,
                message = serverTestProgressMessage(nextResults),
            ),
        )
    }

    private fun serverTestProgressMessage(results: List<ServerTestResult>): String {
        val completed = results.count { it.status == ServerTestStatus.Ready || it.status == ServerTestStatus.Failed }
        return "Server Test: measured $completed/${results.size} servers"
    }

    private fun serverTestSummaryMessage(results: List<ServerTestResult>): String {
        val ready = results.count { it.status == ServerTestStatus.Ready }
        val failed = results.count { it.status == ServerTestStatus.Failed }
        return "Server Test complete: $ready ready, $failed failed"
    }

    private fun connectedServerTestResolvers(settings: WhiteDnsSettings): List<String> {
        val runtimeResolvers = (
            uiState.resolverRuntimeState.activeResolvers +
                uiState.resolverRuntimeState.standbyResolvers +
                uiState.resolverRuntimeState.validResolvers
            )
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()
        return runtimeResolvers.ifEmpty {
            settings.runtimeConnectionSettings().resolve().resolverEntries
        }
    }

    private fun buildServerTestProfiles(
        settings: WhiteDnsSettings,
        serverProfileId: String?,
    ): List<StormDnsServerProfile> {
        val profiles = settings.normalizedConnectionProfiles()
            .let { connectionProfiles ->
                if (serverProfileId == null) {
                    connectionProfiles
                } else {
                    connectionProfiles.filter { it.id == serverProfileId }
                }
            }
            .mapNotNull(::serverProfileFromConnectionProfile)
        return if (serverProfileId == null) {
            profiles.distinctBy { profile ->
                "${profile.domain}\u0000${profile.encryptionKey}\u0000${profile.encryptionMethod}"
            }
        } else {
            profiles
        }
    }

    private fun setServerTestManagers(managers: List<StormDnsProcessManager>) {
        synchronized(serverTestManagersLock) {
            serverTestManagers = managers
        }
    }

    private fun stopServerTestManagers() {
        val managers = synchronized(serverTestManagersLock) {
            serverTestManagers.also {
                serverTestManagers = emptyList()
            }
        }
        managers.forEach { manager ->
            runCatching {
                manager.stop()
            }
        }
    }

    fun disconnect() {
        connectJob?.cancel()
        statsJob?.cancel()
        runtimeRefreshJob?.cancel()
        verificationJob?.cancel()
        serverTestJob?.cancel()
        viewModelScope.launch(Dispatchers.IO) {
            stopAutoTuneTrialManagers()
            stopServerTestManagers()
            stopAllRuntimeServices()
            if (uiState.settings.resolve().connectionMode == "vpn") {
                delay(VpnStopBeforeStormDnsStopDelayMillis)
            }
        }
        activeProxyListenPort = WhiteDnsRuntimeProxy.ListenPortInt
        activeVpnTrafficInterfaceName = null
        activeRuntimeSessionId = ""
        resetTrafficAccounting()
        resetSocksStreamTracker()
        resetRuntimeUiThrottles()
        appendLog("Disconnected")
        uiState = uiState.copy(
            connectionStatus = ConnectionStatus.DISCONNECTED,
            connectionStats = ConnectionStats(),
            resolverRuntimeState = ResolverRuntimeState(),
            connectionProgress = ConnectionProgressState(),
            connectionVerification = ConnectionVerificationState(),
            autoTuneTrialResults = emptyList(),
            serverTestState = ServerTestState(),
            activeConnectionProfileId = null,
        )
    }

    fun updateScanWorkerCount(rawValue: String) {
        val filtered = rawValue.filter(Char::isDigit).take(MaxScanWorkerDigits)
        val workerCount = filtered.toIntOrNull()
        if (workerCount != null && workerCount > 0) {
            scanSettingsStore.saveWorkerCount(workerCount)
        }
        uiState = uiState.copy(scanWorkerCount = filtered)
    }

    fun updateScanConnectionProfile(profileId: String) {
        if (uiState.scanState.isRunning) {
            return
        }
        val scanConnectionProfileId = resolveScanConnectionProfileId(
            settings = uiState.settings,
            requestedProfileId = profileId,
        )
        scanSettingsStore.saveConnectionProfileId(scanConnectionProfileId)
        uiState = uiState.copy(scanConnectionProfileId = scanConnectionProfileId)
    }

    fun beginScanFromFile(uri: Uri) {
        beginScanFromSource(sourceName = "Resolver file") { sessionId ->
            importScanResolverFile(uri, sessionId)
        }
    }

    fun beginScanFromDefaultResolvers() {
        beginScanFromSource(sourceName = "Default resolver list") { sessionId ->
            importDefaultScanResolverFile(sessionId)
        }
    }

    private fun beginScanFromSource(
        sourceName: String,
        importSource: (String) -> ImportedScanResolverFile,
    ) {
        if (uiState.scanState.isRunning) {
            return
        }
        scanLaunchJob?.cancel()
        scanLaunchJob = viewModelScope.launch {
            val sessionId = UUID.randomUUID().toString()
            try {
                val baseSettings = uiState.settings.syncSelectedConnectionProfileFields()
                val scanConnectionProfileId = resolveScanConnectionProfileId(
                    settings = baseSettings,
                    requestedProfileId = uiState.scanConnectionProfileId,
                )
                if (scanConnectionProfileId != uiState.scanConnectionProfileId) {
                    scanSettingsStore.saveConnectionProfileId(scanConnectionProfileId)
                    uiState = uiState.copy(scanConnectionProfileId = scanConnectionProfileId)
                }
                val workerCount = uiState.scanWorkerCount.toIntOrNull()
                    ?.coerceAtLeast(1)
                    ?: WhiteDnsScanDefaults.DefaultWorkerCount
                val importingState = WhiteDnsScanState(
                    sessionId = sessionId,
                    status = WhiteDnsScanStatus.Starting,
                    sourceName = sourceName,
                    workerCount = workerCount,
                    updatedAtMillis = System.currentTimeMillis(),
                    message = "Importing $sourceName",
                )
                withContext(Dispatchers.IO) {
                    WhiteDnsScanStateStore.write(appContext, importingState)
                }
                uiState = uiState.copy(scanState = importingState)

                val imported = withContext(Dispatchers.IO) {
                    importSource(sessionId)
                }

                withContext(Dispatchers.IO) {
                    WhiteDnsScanRequestStore.save(
                        context = appContext,
                        request = WhiteDnsScanLaunchRequest(
                            id = sessionId,
                            sourceName = imported.sourceName,
                            resolverFilePath = imported.file.absolutePath,
                            workerCount = workerCount.coerceAtLeast(1),
                            initialValidResolvers = emptyList(),
                            initialCompletedResolvers = 0,
                            totalResolvers = imported.pendingResolverCount,
                        ),
                    )
                }

                val readyStatus = if (imported.pendingResolverCount > 0) {
                    WhiteDnsScanStatus.Ready
                } else {
                    WhiteDnsScanStatus.Completed
                }
                val readyState = WhiteDnsScanState(
                    sessionId = sessionId,
                    status = readyStatus,
                    sourceName = imported.sourceName,
                    totalResolvers = imported.displayResolverCount,
                    completedResolvers = if (imported.pendingResolverCount > 0) 0 else imported.displayResolverCount,
                    validResolvers = if (imported.pendingResolverCount > 0) 0 else imported.alreadyValidResolverCount,
                    rejectedResolvers = 0,
                    workerCount = workerCount.coerceAtMost(imported.pendingResolverCount.coerceAtLeast(1)),
                    updatedAtMillis = System.currentTimeMillis(),
                    message = buildPreparedScanMessage(imported),
                    validResolverEntries = emptyList(),
                    rejectedResolverEntries = emptyList(),
                )
                withContext(Dispatchers.IO) {
                    WhiteDnsScanStateStore.write(appContext, readyState)
                }
                uiState = uiState.copy(scanState = readyState)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                setScanFailure(sessionId, "Resolver import failed: ${error.message ?: error::class.java.simpleName}")
            }
        }
    }

    fun startPreparedScan() {
        if (uiState.scanState.isRunning) {
            return
        }
        scanLaunchJob?.cancel()
        scanLaunchJob = viewModelScope.launch {
            val previousState = uiState.scanState
            val sessionId = previousState.sessionId
            if (
                sessionId.isBlank() ||
                previousState.status != WhiteDnsScanStatus.Ready ||
                previousState.completedResolvers >= previousState.totalResolvers
            ) {
                return@launch
            }
            val scanRequest = withContext(Dispatchers.IO) {
                WhiteDnsScanRequestStore.load(appContext, sessionId)
            } ?: run {
                setScanFailure(sessionId, "Scan start failed: resolver file is missing")
                return@launch
            }
            val baseSettings = uiState.settings.syncSelectedConnectionProfileFields()
            val scanConnectionProfileId = resolveScanConnectionProfileId(
                settings = baseSettings,
                requestedProfileId = uiState.scanConnectionProfileId,
            )
            if (scanConnectionProfileId != uiState.scanConnectionProfileId) {
                scanSettingsStore.saveConnectionProfileId(scanConnectionProfileId)
                uiState = uiState.copy(scanConnectionProfileId = scanConnectionProfileId)
            }
            val settings = baseSettings.copy(
                selectedConnectionProfileId = scanConnectionProfileId,
            ).syncSelectedConnectionProfileFields()
            val serverProfile = selectServerProfile(settings)
            if (serverProfile == null) {
                val profileName = settings.selectedConnectionProfile().name.ifBlank { "selected scan profile" }
                setScanFailure(sessionId, "$profileName requires a StormDNS domain and encryption key")
                return@launch
            }
            val workerCount = uiState.scanWorkerCount.toIntOrNull()
                ?.coerceAtLeast(1)
                ?: scanRequest.workerCount.coerceAtLeast(1)
            val resolverFileExists = withContext(Dispatchers.IO) {
                File(scanRequest.resolverFilePath).isFile
            }
            if (!resolverFileExists) {
                setScanFailure(sessionId, "Scan start failed: resolver file is missing")
                return@launch
            }
            val pendingResolverCount = previousState.totalResolvers
                .takeIf { it > 0 }
                ?: scanRequest.totalResolvers
            if (pendingResolverCount == 0) {
                val completedState = previousState.copy(
                    status = WhiteDnsScanStatus.Completed,
                    updatedAtMillis = System.currentTimeMillis(),
                    message = "No new resolvers to scan; Scanner result is up to date",
                )
                WhiteDnsScanStateStore.write(appContext, completedState)
                uiState = uiState.copy(scanState = completedState)
                return@launch
            }
            val nowMillis = System.currentTimeMillis()
            val startingState = previousState.copy(
                status = WhiteDnsScanStatus.Starting,
                workerCount = workerCount.coerceAtMost(pendingResolverCount.coerceAtLeast(1)),
                startedAtMillis = nowMillis,
                updatedAtMillis = nowMillis,
                message = "Starting scan",
            )
            withContext(Dispatchers.IO) {
                RuntimeLaunchRequestStore.save(
                    context = appContext,
                    requestId = sessionId,
                    serverProfile = serverProfile,
                    settings = settings,
                )
                WhiteDnsScanRequestStore.save(
                    context = appContext,
                    request = scanRequest.copy(
                        workerCount = workerCount,
                        initialValidResolvers = previousState.validResolverEntries,
                        initialRejectedResolvers = previousState.rejectedResolverEntries,
                        initialCompletedResolvers = previousState.completedResolvers,
                        totalResolvers = previousState.totalResolvers,
                    ),
                )
            }
            WhiteDnsScanStateStore.write(appContext, startingState)
            uiState = uiState.copy(scanState = startingState)

            withContext(Dispatchers.IO) {
                WhiteDnsScanService.startPrepared(appContext, sessionId)
            }
        }
    }

    fun stopScan() {
        scanLaunchJob?.cancel()
        if (uiState.scanState.isRunning) {
            val stoppedState = uiState.scanState.copy(
                status = WhiteDnsScanStatus.Stopped,
                updatedAtMillis = System.currentTimeMillis(),
                message = "Scan stopped",
            )
            WhiteDnsScanStateStore.write(appContext, stoppedState)
            uiState = uiState.copy(scanState = stoppedState)
        }
        viewModelScope.launch(Dispatchers.IO) {
            WhiteDnsScanService.stop(appContext)
        }
    }

    fun refreshScanState() {
        scanStateRefreshJob?.cancel()
        val currentSettings = uiState.settings
        scanStateRefreshJob = viewModelScope.launch {
            val refreshResult = withContext(Dispatchers.IO) {
                val persistedState = WhiteDnsScanStateStore.read(appContext)
                val scanState = persistedState.recoverIfStale(
                    nowMillis = System.currentTimeMillis(),
                    staleAfterMillis = StaleScanStateTimeoutMillis,
                )
                if (scanState != persistedState) {
                    WhiteDnsScanStateStore.write(appContext, scanState)
                }
                ScanStateRefreshResult(
                    scanState = scanState,
                    updatedSettings = syncScannerResultProfile(currentSettings, scanState),
                )
            }
            uiState = uiState.copy(
                settings = refreshResult.updatedSettings ?: uiState.settings,
                scanState = refreshResult.scanState,
            )
        }
    }

    fun resumeScan() {
        if (uiState.scanState.isRunning) {
            return
        }
        scanLaunchJob?.cancel()
        scanLaunchJob = viewModelScope.launch {
            val previousState = uiState.scanState
            val previousSessionId = previousState.sessionId
            if (previousSessionId.isBlank()) {
                return@launch
            }
            val runtimeRequest = withContext(Dispatchers.IO) {
                RuntimeLaunchRequestStore.load(appContext, previousSessionId)
            } ?: run {
                setScanFailure(previousSessionId, "Scan resume failed: launch settings are missing")
                return@launch
            }
            val scanRequest = withContext(Dispatchers.IO) {
                WhiteDnsScanRequestStore.load(appContext, previousSessionId)
            } ?: run {
                setScanFailure(previousSessionId, "Scan resume failed: resolver file is missing")
                return@launch
            }
            val validEntries = WhiteDnsScannerResultStore.normalizeResolverEntries(previousState.validResolverEntries)
            val rejectedEntries = WhiteDnsScannerResultStore.normalizeResolverEntries(previousState.rejectedResolverEntries)
                .filterNot(validEntries::contains)
            val processed = (validEntries + rejectedEntries).toSet()
            val sessionId = UUID.randomUUID().toString()
            val resolverFile = File(
                File(File(appContext.noBackupFilesDir, "stormdns/scan"), sessionId).apply { mkdirs() },
                "resume.resolvers",
            )
            val remainingResolverCount = withContext(Dispatchers.IO) {
                runCatching {
                    val excludedResolvers = processed + WhiteDnsScannerResultStore.readValidResolverSet(appContext)
                    File(scanRequest.resolverFilePath).bufferedReader(Charsets.UTF_8).useLines { lines ->
                        WhiteDnsScannerResultStore.writePendingScanResolverFile(
                            lines = lines,
                            outputFile = resolverFile,
                            excludedResolvers = excludedResolvers,
                        )
                    }.pendingResolverCount
                }
            }.getOrElse { error ->
                setScanFailure(previousSessionId, "Scan resume failed: ${error.message ?: error::class.java.simpleName}")
                return@launch
            }
            if (remainingResolverCount == 0) {
                val updatedState = previousState.copy(
                    updatedAtMillis = System.currentTimeMillis(),
                    message = "No remaining resolvers to resume",
                )
                WhiteDnsScanStateStore.write(appContext, updatedState)
                uiState = uiState.copy(scanState = updatedState)
                return@launch
            }

            val workerCount = uiState.scanWorkerCount.toIntOrNull()
                ?.coerceAtLeast(1)
                ?: scanRequest.workerCount.coerceAtLeast(1)
            val startingState = WhiteDnsScanState(
                sessionId = sessionId,
                status = WhiteDnsScanStatus.Starting,
                sourceName = previousState.sourceName.ifBlank { scanRequest.sourceName },
                totalResolvers = remainingResolverCount + processed.size,
                completedResolvers = processed.size,
                validResolvers = validEntries.size,
                rejectedResolvers = rejectedEntries.size,
                workerCount = workerCount.coerceAtMost(remainingResolverCount.coerceAtLeast(1)),
                startedAtMillis = System.currentTimeMillis(),
                updatedAtMillis = System.currentTimeMillis(),
                message = "Resuming scan",
                validResolverEntries = validEntries,
                rejectedResolverEntries = rejectedEntries,
            )
            WhiteDnsScanStateStore.write(appContext, startingState)
            uiState = uiState.copy(scanState = startingState)

            withContext(Dispatchers.IO) {
                WhiteDnsScanService.start(
                    context = appContext,
                    sessionId = sessionId,
                    serverProfile = runtimeRequest.serverProfile,
                    settings = runtimeRequest.settings,
                    sourceName = startingState.sourceName,
                    resolverFile = resolverFile,
                    workerCount = workerCount,
                    initialValidResolvers = validEntries,
                    initialRejectedResolvers = rejectedEntries,
                )
            }
        }
    }

    private fun startStatsMonitor() {
        statsJob?.cancel()
        statsJob = viewModelScope.launch {
            while (isActive && uiState.connectionStatus == ConnectionStatus.CONNECTED) {
                delay(1_000)
                val listenPort = activeProxyListenPort
                val stats = withContext(Dispatchers.IO) {
                    buildConnectionStats(listenPort = listenPort)
                }
                uiState = uiState.copy(
                    connectionStats = stats,
                )
            }
        }
    }

    override fun onCleared() {
        connectJob?.cancel()
        statsJob?.cancel()
        runtimeRefreshJob?.cancel()
        verificationJob?.cancel()
        serverTestJob?.cancel()
        scanLaunchJob?.cancel()
        scanStateRefreshJob?.cancel()
        stopAutoTuneTrialManagers()
        viewModelScope.launch(Dispatchers.IO) {
            stopServerTestManagers()
        }
        WhiteDnsProxyEvents.removeListener(proxyEventListener)
        WhiteDnsVpnEvents.removeListener(vpnEventListener)
        unregisterRuntimeBroadcastReceivers()
        super.onCleared()
    }

    private fun registerRuntimeBroadcastReceivers() {
        ContextCompat.registerReceiver(
            appContext,
            proxyBroadcastReceiver,
            IntentFilter(WhiteDnsProxyService.BroadcastAction),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        ContextCompat.registerReceiver(
            appContext,
            vpnBroadcastReceiver,
            IntentFilter(WhiteDnsVpnService.BroadcastAction),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        ContextCompat.registerReceiver(
            appContext,
            scanBroadcastReceiver,
            IntentFilter(WhiteDnsScanService.BroadcastAction),
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
        if (isStaleRuntimeEvent(sessionId)) {
            return
        }
        val trafficStats = parseStormDnsTrafficStatsLine(message)
        val progressState = parseStormDnsConnectionProgressLine(message)
        val resolverState = parseStormDnsResolverStateLine(message)
        if (trafficStats != null) {
            stormDnsTrafficAccounting.record(trafficStats)
        }
        trackSocksStreamLogLine(message)
        val isTelemetry = trafficStats != null ||
            progressState != null ||
            resolverState != null ||
            message.contains("WD_PROGRESS") ||
            message.contains("WD_RESOLVERS")
        if (progressState == null && resolverState == null && isTelemetry) {
            return
        }
        viewModelScope.launch(Dispatchers.Main.immediate) {
            progressState?.let(::updateConnectionProgressOnMain)
            resolverState?.let(::updateResolverStateOnMain)
            if (!isTelemetry) {
                appendLogOnMain(message)
            }
        }
    }

    private fun handleRuntimeReady(sessionId: String, message: String, expectedConnectionMode: String) {
        if (isStaleRuntimeEvent(sessionId)) {
            return
        }
        viewModelScope.launch(Dispatchers.Main.immediate) {
            if (uiState.connectionStatus == ConnectionStatus.DISCONNECTED) {
                val activeRuntimeState = withContext(Dispatchers.IO) {
                    findActiveRuntimeState()?.takeIf { it.mode == expectedConnectionMode }
                }
                if (activeRuntimeState != null) {
                    restoreRuntimeConnection(activeRuntimeState)
                }
                return@launch
            }
            if (uiState.connectionStatus != ConnectionStatus.CONNECTING) {
                return@launch
            }
            if (uiState.settings.resolve().connectionMode != expectedConnectionMode) {
                return@launch
            }
            val readySettings = settingsForRuntimeReady(
                settings = uiState.settings,
                expectedConnectionMode = expectedConnectionMode,
                message = message,
            )
            appendLogOnMain(message)
            uiState = uiState.copy(
                settings = readySettings,
                connectionStatus = ConnectionStatus.CONNECTED,
                connectionStats = ConnectionStats(),
                connectionProgress = ConnectionProgressState(phase = "connected", percent = 100),
                networkIpAddress = findDeviceNetworkIpAddress(),
            )
            reportSuccessfulRegistryResolvers(uiState.resolverRuntimeState)
            trafficBaseline = currentTrafficSnapshot()
            lastTrafficSnapshot = trafficBaseline
            startStatsMonitor()
            startConnectionVerification(expectedConnectionMode)
        }
    }

    private fun handleProxyFailure(sessionId: String, message: String) {
        if (isStaleRuntimeEvent(sessionId)) {
            return
        }
        viewModelScope.launch(Dispatchers.Main.immediate) {
            if (!shouldHandleRuntimeEvent(WhiteDnsRuntimeStateStore.ModeProxy)) {
                return@launch
            }
            appendLogOnMain(message)
            connectJob?.cancel()
            statsJob?.cancel()
            verificationJob?.cancel()
            serverTestJob?.cancel()
            withContext(Dispatchers.IO) {
                stopServerTestManagers()
                stopAllRuntimeServices()
            }
            activeProxyListenPort = WhiteDnsRuntimeProxy.ListenPortInt
            activeVpnTrafficInterfaceName = null
            activeRuntimeSessionId = ""
            resetTrafficAccounting()
            resetSocksStreamTracker()
            resetRuntimeUiThrottles()
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

    private fun handleVpnFailure(sessionId: String, message: String) {
        if (isStaleRuntimeEvent(sessionId)) {
            return
        }
        viewModelScope.launch(Dispatchers.Main.immediate) {
            if (!shouldHandleRuntimeEvent(WhiteDnsRuntimeStateStore.ModeVpn)) {
                return@launch
            }
            appendLogOnMain(message)
            connectJob?.cancel()
            statsJob?.cancel()
            verificationJob?.cancel()
            serverTestJob?.cancel()
            withContext(Dispatchers.IO) {
                stopServerTestManagers()
                stopAllRuntimeServices()
            }
            activeProxyListenPort = WhiteDnsRuntimeProxy.ListenPortInt
            activeVpnTrafficInterfaceName = null
            activeRuntimeSessionId = ""
            resetTrafficAccounting()
            resetSocksStreamTracker()
            resetRuntimeUiThrottles()
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

    private fun shouldHandleRuntimeEvent(expectedConnectionMode: String): Boolean {
        return uiState.connectionStatus != ConnectionStatus.DISCONNECTED &&
            uiState.settings.resolve().connectionMode == expectedConnectionMode
    }

    private fun isStaleRuntimeEvent(sessionId: String): Boolean {
        return activeRuntimeSessionId.isNotBlank() && sessionId != activeRuntimeSessionId
    }

    private fun findActiveRuntimeState(): WhiteDnsRuntimeState? {
        return WhiteDnsRuntimeStateStore.readAll(appContext)
            .asSequence()
            .filter { state ->
                state.status == WhiteDnsRuntimeStateStore.StatusReady ||
                    state.status == WhiteDnsRuntimeStateStore.StatusStarting
            }
            .sortedByDescending { it.updatedAtMillis }
            .firstOrNull(::isRuntimeStateHealthy)
    }

    private fun isRuntimeStateHealthy(state: WhiteDnsRuntimeState): Boolean {
        return when (state.mode) {
            WhiteDnsRuntimeStateStore.ModeProxy -> state.listenPort > 0 && canConnectToLocalPort(state.listenPort)
            WhiteDnsRuntimeStateStore.ModeVpn -> {
                val vpnInterfaceExists = findVpnTrafficInterfaceName() != null
                if (isAmneziaRuntimeState(state)) {
                    vpnInterfaceExists
                } else {
                    state.listenPort > 0 && vpnInterfaceExists && canConnectToLocalPort(state.listenPort)
                }
            }
            else -> false
        }
    }

    private fun isAmneziaRuntimeState(state: WhiteDnsRuntimeState): Boolean {
        return state.mode == WhiteDnsRuntimeStateStore.ModeVpn &&
            state.message.contains("AmneziaWG", ignoreCase = true)
    }

    private fun isSameConnectedRuntime(state: WhiteDnsRuntimeState): Boolean {
        val activeProfileId = state.connectionProfileId.takeIf(String::isNotBlank)
        return uiState.connectionStatus == ConnectionStatus.CONNECTED &&
            (state.sessionId.isBlank() || activeRuntimeSessionId == state.sessionId) &&
            uiState.settings.resolve().connectionMode == state.mode &&
            (activeProfileId == null || uiState.activeConnectionProfileId == activeProfileId)
    }

    private fun restoreRuntimeConnection(state: WhiteDnsRuntimeState) {
        val profileId = state.connectionProfileId.takeIf(String::isNotBlank)
        activeRuntimeSessionId = state.sessionId
        val restoredSettings = uiState.settings
            .copy(
                selectedConnectionProfileId = profileId ?: uiState.settings.selectedConnectionProfileId,
                connectionMode = state.mode,
                transportMode = when {
                    state.mode == WhiteDnsRuntimeStateStore.ModeVpn && !isAmneziaRuntimeState(state) ->
                        WhiteDnsOptions.TransportDns
                    else -> uiState.settings.transportMode
                },
            )
            .syncSelectedConnectionProfileFields()
        activeProxyListenPort = state.listenPort.takeIf { it > 0 }
            ?: restoredSettings.runtimeConnectionSettings().resolve().listenPort
        activeVpnTrafficInterfaceName = null
        resetTrafficAccounting()
        resetSocksStreamTracker()
        resetRuntimeUiThrottles()
        val modeLabel = if (state.mode == WhiteDnsRuntimeStateStore.ModeVpn) {
            "VPN"
        } else {
            "proxy"
        }
        uiState = uiState.copy(
            settings = restoredSettings,
            connectionStatus = ConnectionStatus.CONNECTED,
            connectionStats = ConnectionStats(),
            resolverRuntimeState = ResolverRuntimeState(),
            connectionProgress = ConnectionProgressState(phase = "connected", percent = 100),
            connectionVerification = ConnectionVerificationState(),
            networkIpAddress = findDeviceNetworkIpAddress(),
            activeConnectionProfileId = restoredSettings.selectedConnectionProfile().id,
            connectionLogs = prependConnectionLog("Restored active $modeLabel connection"),
        )
        trafficBaseline = currentTrafficSnapshot()
        lastTrafficSnapshot = trafficBaseline
        startStatsMonitor()
        startConnectionVerification(state.mode)
    }

    private fun markRuntimeDisconnected(message: String) {
        connectJob?.cancel()
        statsJob?.cancel()
        verificationJob?.cancel()
        serverTestJob?.cancel()
        stopServerTestManagers()
        activeProxyListenPort = WhiteDnsRuntimeProxy.ListenPortInt
        activeVpnTrafficInterfaceName = null
        activeRuntimeSessionId = ""
        resetTrafficAccounting()
        resetSocksStreamTracker()
        resetRuntimeUiThrottles()
        uiState = uiState.copy(
            connectionStatus = ConnectionStatus.DISCONNECTED,
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
        val cleanMessage = message
            .replace(Regex("\\u001B\\[[;\\d]*m"), "")
            .trim()
        if (cleanMessage.isEmpty()) {
            return uiState.connectionLogs
        }
        return (listOf(cleanMessage) + uiState.connectionLogs).take(MaxConnectionLogs)
    }

    private fun settingsForRuntimeReady(
        settings: WhiteDnsSettings,
        expectedConnectionMode: String,
        message: String,
    ): WhiteDnsSettings {
        if (expectedConnectionMode != WhiteDnsRuntimeStateStore.ModeVpn) {
            return settings
        }
        if (message.contains("AmneziaWG", ignoreCase = true)) {
            return settings
        }
        return settings.copy(transportMode = WhiteDnsOptions.TransportDns)
            .syncSelectedConnectionProfileFields()
    }

    private fun shouldReconfigureActiveVpn(
        previousSettings: WhiteDnsSettings,
        nextSettings: WhiteDnsSettings,
    ): Boolean {
        if (uiState.connectionStatus != ConnectionStatus.CONNECTED) {
            return false
        }
        if (previousSettings.resolve().connectionMode != "vpn" || nextSettings.resolve().connectionMode != "vpn") {
            return false
        }
        return previousSettings.splitTunnelMode != nextSettings.splitTunnelMode ||
            previousSettings.splitTunnelPackages != nextSettings.splitTunnelPackages
    }

    private fun reconfigureActiveVpnSplitTunnel(settings: WhiteDnsSettings) {
        viewModelScope.launch(Dispatchers.IO) {
            val resolvedSettings = settings.runtimeConnectionSettings().resolve()
            if (resolvedSettings.connectionMode != "vpn") {
                return@launch
            }
            runCatching {
                WhiteDnsVpnService.start(
                    context = getApplication<Application>().applicationContext,
                    sessionId = activeRuntimeSessionId,
                    serverProfile = activeServerProfile,
                    settings = settings.runtimeConnectionSettings(),
                )
            }.onSuccess {
                appendLog("Updated VPN split tunnel apps")
            }.onFailure { error ->
                handleVpnFailure(
                    activeRuntimeSessionId,
                    "Failed to update split tunnel: ${error.message ?: error::class.java.simpleName}",
                )
            }
        }
    }

    private fun stopAllRuntimeServices() {
        WhiteDnsVpnService.stop(appContext)
        WhiteDnsProxyService.stop(appContext)
    }

    private fun setScanFailure(sessionId: String, message: String) {
        val failedState = WhiteDnsScanState(
            sessionId = sessionId,
            status = WhiteDnsScanStatus.Failed,
            updatedAtMillis = System.currentTimeMillis(),
            message = message,
        )
        WhiteDnsScanStateStore.write(appContext, failedState)
        uiState = uiState.copy(scanState = failedState)
    }

    private fun importScanResolverFile(uri: Uri, sessionId: String): ImportedScanResolverFile {
        val scanDir = File(File(appContext.noBackupFilesDir, "stormdns/scan"), sessionId).apply {
            mkdirs()
        }
        val resolverFile = File(scanDir, "input.resolvers")
        val summary = appContext.contentResolver.openInputStream(uri)
            ?.bufferedReader(Charsets.UTF_8)
            ?.useLines { lines ->
                WhiteDnsScannerResultStore.writePendingScanResolverFile(
                    lines = lines,
                    outputFile = resolverFile,
                )
            }
            ?: throw IllegalArgumentException("Unable to open resolver file")
        if (summary.totalResolverCount == 0) {
            throw IllegalArgumentException("No valid resolver entries found in file")
        }
        return ImportedScanResolverFile(
            file = resolverFile,
            sourceName = displayNameForUri(uri),
            pendingResolverCount = summary.pendingResolverCount,
            totalResolverCount = summary.totalResolverCount,
            alreadyValidResolverCount = summary.alreadyValidResolverCount,
            invalidEntryCount = summary.invalidEntryCount,
        )
    }

    private fun importDefaultScanResolverFile(sessionId: String): ImportedScanResolverFile {
        val scanDir = File(File(appContext.noBackupFilesDir, "stormdns/scan"), sessionId).apply {
            mkdirs()
        }
        val resolverFile = File(scanDir, "default.resolvers")
        val summary = appContext.assets.open(DefaultScanResolverAssetName)
            .bufferedReader(Charsets.UTF_8)
            .useLines { lines ->
                WhiteDnsScannerResultStore.writePendingScanResolverFile(
                    lines = lines,
                    outputFile = resolverFile,
                )
            }
        if (summary.totalResolverCount == 0) {
            throw IllegalArgumentException("No valid resolver entries found in default list")
        }
        return ImportedScanResolverFile(
            file = resolverFile,
            sourceName = "Default resolver list",
            pendingResolverCount = summary.pendingResolverCount,
            totalResolverCount = summary.totalResolverCount,
            alreadyValidResolverCount = summary.alreadyValidResolverCount,
            invalidEntryCount = summary.invalidEntryCount,
        )
    }

    private fun loadEmbeddedResolverText(): String {
        val resolverText = appContext.assets.open(DefaultScanResolverAssetName)
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
        val normalizedResolvers = validateResolverText(resolverText).normalizedResolvers
        if (normalizedResolvers.isEmpty()) {
            throw IllegalArgumentException("Embedded resolver list is empty")
        }
        return normalizedResolvers.joinToString(separator = "\n")
    }

    private suspend fun discoverDnsResolvers(
        discoveryNetworks: List<DnsDiscoveryNetwork>,
        onLog: (String) -> Unit,
    ): List<String> = coroutineScope {
        val localDnsResolvers = discoveryNetworks
            .flatMap { it.dnsResolvers }
            .distinct()
        if (localDnsResolvers.isEmpty()) {
            onLog("Локальный DNS не найден в cellular LinkProperties")
        } else {
            onLog("Локальный DNS: ${localDnsResolvers.joinToString()}")
        }

        val foundResolvers = linkedSetOf<String>()
        discoveryNetworks.forEach { discoveryNetwork ->
            discoveryNetwork.dnsResolvers.forEach { resolver ->
                if (foundResolvers.size < TargetResolverCount && probeDnsResolver(resolver, discoveryNetwork.network)) {
                    foundResolvers += resolver
                }
            }
        }
        if (foundResolvers.size >= TargetResolverCount) {
            return@coroutineScope foundResolvers.take(TargetResolverCount)
        }

        val seedTargets = discoveryNetworks
            .flatMap { discoveryNetwork ->
                discoveryNetwork.seedIps.map { seedIp -> seedIp to discoveryNetwork.network }
            }
            .distinctBy { it.first }
            .ifEmpty {
                listOfNotNull(findDeviceNetworkIpAddress().takeIf(::isUsableIpv4Address)?.let { it to null })
            }
        if (seedTargets.isEmpty()) {
            return@coroutineScope foundResolvers.toList()
        }

        val scannedPrefixes = linkedSetOf<String>()
        seedTargets.forEach { (seedIp, network) ->
            val subnetPrefixes = neighboringSubnetPrefixes(seedIp)
            subnetPrefixes.forEachIndexed { index, prefix ->
                if (foundResolvers.size >= TargetResolverCount || !scannedPrefixes.add(prefix)) {
                    return@forEachIndexed
                }
                onLog(
                    if (index == 0) {
                        "Сканирую подсеть $prefix.0/24 от $seedIp"
                    } else {
                        "Сканирую соседнюю подсеть $prefix.0/24 от $seedIp"
                    },
                )
                scanDnsSubnet(prefix, network, foundResolvers)
            }
        }
        foundResolvers.take(TargetResolverCount)
    }

    private suspend fun discoverRegistryResolvers(
        operatorCode: String,
        discoveryNetworks: List<DnsDiscoveryNetwork>,
        onLog: (String) -> Unit,
    ): List<String> {
        val candidates = fetchRegistryResolverCandidates(operatorCode, discoveryNetworks, onLog)
        if (candidates.isEmpty()) {
            onLog("База не вернула кандидатов для оператора")
            return emptyList()
        }
        onLog("Проверяю resolver'ы из базы: ${candidates.take(12).joinToString()}")
        val responsiveResolvers = probeResolverCandidates(
            candidates = candidates,
            discoveryNetworks = discoveryNetworks,
        )
        if (responsiveResolvers.isEmpty()) {
            onLog("Resolver'ы из базы не ответили в этой сети")
        } else {
            onLog("База дала рабочие resolver'ы: ${responsiveResolvers.joinToString()}")
        }
        return responsiveResolvers
    }

    private suspend fun probeResolverCandidates(
        candidates: List<String>,
        discoveryNetworks: List<DnsDiscoveryNetwork>,
    ): List<String> = coroutineScope {
        val normalizedCandidates = WhiteDnsScannerResultStore.normalizeResolverEntries(candidates)
            .distinct()
            .take(RegistryResolverProbeLimit)
        val networks = discoveryNetworks.ifEmpty {
            listOf(DnsDiscoveryNetwork(network = null, dnsResolvers = emptyList(), seedIps = emptyList()))
        }
        val foundResolvers = linkedSetOf<String>()
        normalizedCandidates
            .filter(::isUsableIpv4Address)
            .chunked(DnsScanBatchSize)
            .forEach { batch ->
                if (foundResolvers.size >= TargetResolverCount) {
                    return@forEach
                }
                val responsiveResolvers = batch
                    .map { candidate ->
                        async(Dispatchers.IO) {
                            candidate.takeIf {
                                networks.any { discoveryNetwork ->
                                    probeDnsResolver(it, discoveryNetwork.network)
                                }
                            }
                        }
                    }
                    .awaitAll()
                    .filterNotNull()
                responsiveResolvers.forEach { resolver ->
                    if (foundResolvers.size < TargetResolverCount) {
                        foundResolvers += resolver
                    }
                }
            }
        foundResolvers.toList()
    }

    private fun fetchRegistryResolverCandidates(
        operatorCode: String,
        discoveryNetworks: List<DnsDiscoveryNetwork>,
        onLog: (String) -> Unit,
    ): List<String> {
        return runCatching {
            val registryUrl = Uri.parse(ResolverRegistryUrl)
                .buildUpon()
                .appendQueryParameter("operator", normalizeOperatorCode(operatorCode))
                .appendQueryParameter("local_dns", discoveryNetworks.flatMap { it.dnsResolvers }.joinToString(","))
                .build()
                .toString()
            val connection = openRegistryConnection(registryUrl, discoveryNetworks).apply {
                requestMethod = "GET"
                connectTimeout = RegistryConnectTimeoutMillis
                readTimeout = RegistryReadTimeoutMillis
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", RegistryUserAgent)
            }
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                parseRegistryResolvers(reader.readText(), operatorCode)
            }
        }.getOrElse { error ->
            onLog("База resolver'ов недоступна: ${error.readableNetworkMessage()}")
            emptyList()
        }
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
                postRegistryResolvers(
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

    private fun openRegistryConnection(
        rawUrl: String,
        discoveryNetworks: List<DnsDiscoveryNetwork>,
    ): HttpURLConnection {
        val url = URL(rawUrl)
        val network = discoveryNetworks.firstOrNull { it.network != null }?.network
        return ((network?.openConnection(url) ?: url.openConnection()) as HttpURLConnection)
    }

    private fun parseRegistryResolvers(rawJson: String, operatorCode: String): List<String> {
        val root = JSONObject(rawJson)
        val normalizedOperatorCode = normalizeOperatorCode(operatorCode)
        val resolvers = mutableListOf<String>()

        fun addArray(array: JSONArray?) {
            if (array == null) {
                return
            }
            repeat(array.length()) { index ->
                array.optString(index)
                    .takeIf(String::isNotBlank)
                    ?.let(resolvers::add)
            }
        }

        addArray(root.optJSONArray("resolvers"))
        val operators = root.optJSONObject("operators")
        val operatorValue = operators?.opt(normalizedOperatorCode)
        when (operatorValue) {
            is JSONArray -> addArray(operatorValue)
            is JSONObject -> {
                addArray(operatorValue.optJSONArray("resolvers"))
                addArray(operatorValue.optJSONArray("default"))
            }
        }
        addArray(root.optJSONArray("default"))

        return WhiteDnsScannerResultStore.normalizeResolverEntries(resolvers)
            .distinct()
            .take(RegistryResolverProbeLimit)
    }

    private fun dnsDiscoveryNetworks(connectivityManager: ConnectivityManager): List<DnsDiscoveryNetwork> {
        val activeNetwork = connectivityManager.activeNetwork
        val networks = (listOfNotNull(activeNetwork) + connectivityManager.allNetworks.toList())
            .distinct()
            .sortedWith(
                compareByDescending<android.net.Network> { it == activeNetwork }
                    .thenByDescending { network ->
                        connectivityManager.getNetworkCapabilities(network)
                            ?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) == true
                    },
            )
        return networks
            .mapNotNull { network ->
                val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return@mapNotNull null
                val isCellular = capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR)
                val hasInternet = capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
                if (!isCellular && network != activeNetwork) {
                    return@mapNotNull null
                }
                if (!hasInternet && network != activeNetwork) {
                    return@mapNotNull null
                }
                val linkProperties = connectivityManager.getLinkProperties(network) ?: return@mapNotNull null
                val dnsResolvers = linkProperties.dnsServers
                    .filterIsInstance<Inet4Address>()
                    .mapNotNull { it.hostAddress }
                    .filter(::isUsableIpv4Address)
                    .distinct()
                val linkAddresses = linkProperties.linkAddresses
                    .mapNotNull { it.address as? Inet4Address }
                    .mapNotNull { it.hostAddress }
                    .filter(::isUsableIpv4Address)
                    .distinct()
                DnsDiscoveryNetwork(
                    network = network,
                    dnsResolvers = dnsResolvers,
                    seedIps = (dnsResolvers + linkAddresses).distinct(),
                )
            }
            .ifEmpty {
                listOf(
                    DnsDiscoveryNetwork(
                        network = activeNetwork,
                        dnsResolvers = emptyList(),
                        seedIps = listOfNotNull(findDeviceNetworkIpAddress().takeIf(::isUsableIpv4Address)),
                    ),
                )
            }
    }

    private suspend fun scanDnsSubnet(
        subnetPrefix: String,
        activeNetwork: android.net.Network?,
        foundResolvers: LinkedHashSet<String>,
    ) = coroutineScope {
        val candidates = (1..254)
            .map { host -> "$subnetPrefix.$host" }
            .filterNot { it in foundResolvers }
        candidates
            .chunked(DnsScanBatchSize)
            .forEach { batch ->
                if (foundResolvers.size >= TargetResolverCount) {
                    return@forEach
                }
                val responsiveResolvers = batch
                    .map { candidate ->
                        async(Dispatchers.IO) {
                            candidate.takeIf { probeDnsResolver(it, activeNetwork) }
                        }
                    }
                    .awaitAll()
                    .filterNotNull()
                responsiveResolvers.forEach { resolver ->
                    if (foundResolvers.size < TargetResolverCount) {
                        foundResolvers += resolver
                    }
                }
            }
    }

    private fun probeDnsResolver(
        resolverIp: String,
        activeNetwork: android.net.Network?,
    ): Boolean {
        return runCatching {
            DatagramSocket().use { socket ->
                activeNetwork?.bindSocket(socket)
                socket.soTimeout = DnsProbeTimeoutMillis
                val query = buildDnsProbeQuery()
                val address = InetAddress.getByName(resolverIp)
                socket.send(DatagramPacket(query, query.size, address, 53))
                val buffer = ByteArray(512)
                val response = DatagramPacket(buffer, buffer.size)
                socket.receive(response)
                response.length >= 12 &&
                    buffer[0] == query[0] &&
                    buffer[1] == query[1] &&
                    (buffer[3].toInt() and 0x0F) == 0
            }
        }.getOrDefault(false)
    }

    private fun measureResolverSetProbe(
        resolvers: List<String>,
        onLog: (String) -> Unit,
        logPrefix: String,
    ): ResolverProbeSummary {
        val normalizedResolvers = resolvers
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()
        if (normalizedResolvers.isEmpty()) {
            onLog("$logPrefix DNS probe: resolver list empty")
            return ResolverProbeSummary(successes = 0, attempts = 0, averageLatencyMillis = 0L)
        }
        val activeNetwork = appContext
            .getSystemService(ConnectivityManager::class.java)
            ?.activeNetwork
        var successes = 0
        var attempts = 0
        var latencySum = 0L
        normalizedResolvers.forEach { resolver ->
            var resolverSuccesses = 0
            var resolverLatencySum = 0L
            repeat(ResolverBenchmarkDnsProbeAttempts) {
                attempts += 1
                val latency = probeDnsResolverLatencyMillis(resolver, activeNetwork)
                if (latency != null) {
                    successes += 1
                    resolverSuccesses += 1
                    latencySum += latency
                    resolverLatencySum += latency
                }
            }
            val avg = if (resolverSuccesses > 0) resolverLatencySum / resolverSuccesses else 0L
            onLog("$logPrefix DNS $resolver: $resolverSuccesses/$ResolverBenchmarkDnsProbeAttempts, ${avg}ms")
        }
        return ResolverProbeSummary(
            successes = successes,
            attempts = attempts,
            averageLatencyMillis = if (successes > 0) latencySum / successes else 0L,
        )
    }

    private fun probeDnsResolverLatencyMillis(
        resolverIp: String,
        activeNetwork: android.net.Network?,
    ): Long? {
        return runCatching {
            DatagramSocket().use { socket ->
                activeNetwork?.bindSocket(socket)
                socket.soTimeout = ResolverBenchmarkDnsProbeTimeoutMillis
                val query = buildDnsProbeQuery()
                val address = InetAddress.getByName(resolverIp)
                val startedAt = System.currentTimeMillis()
                socket.send(DatagramPacket(query, query.size, address, 53))
                val buffer = ByteArray(512)
                val response = DatagramPacket(buffer, buffer.size)
                socket.receive(response)
                val latencyMillis = (System.currentTimeMillis() - startedAt).coerceAtLeast(1L)
                val ok = response.length >= 12 &&
                    buffer[0] == query[0] &&
                    buffer[1] == query[1] &&
                    (buffer[3].toInt() and 0x0F) == 0
                latencyMillis.takeIf { ok }
            }
        }.getOrNull()
    }

    private fun buildDnsProbeQuery(): ByteArray {
        val query = mutableListOf<Byte>()
        query += 0x42.toByte()
        query += 0x24.toByte()
        query += 0x01.toByte()
        query += 0x00.toByte()
        query += 0x00.toByte()
        query += 0x01.toByte()
        query += 0x00.toByte()
        query += 0x00.toByte()
        query += 0x00.toByte()
        query += 0x00.toByte()
        query += 0x00.toByte()
        query += 0x00.toByte()
        "cloudflare.com".split('.').forEach { label ->
            query += label.length.toByte()
            label.encodeToByteArray().forEach(query::add)
        }
        query += 0x00.toByte()
        query += 0x00.toByte()
        query += 0x01.toByte()
        query += 0x00.toByte()
        query += 0x01.toByte()
        return query.toByteArray()
    }

    private fun neighboringSubnetPrefixes(seedIp: String): List<String> {
        val octets = seedIp.split('.').mapNotNull(String::toIntOrNull)
        if (octets.size != 4) {
            return emptyList()
        }
        val first = octets[0]
        val second = octets[1]
        val third = octets[2]
        return buildList {
            add(third)
            for (distance in 1..NeighborSubnetDistance) {
                add(third - distance)
                add(third + distance)
            }
        }
            .filter { it in 0..255 }
            .distinct()
            .map { "$first.$second.$it" }
    }

    private fun isUsableIpv4Address(ip: String): Boolean {
        val octets = ip.split('.').mapNotNull(String::toIntOrNull)
        if (octets.size != 4) {
            return false
        }
        return octets.all { it in 0..255 } &&
            octets[0] !in setOf(0, 127, 169, 224, 255) &&
            !(octets[0] == 169 && octets[1] == 254)
    }

    private fun normalizeOperatorCode(operatorCode: String): String {
        return when (operatorCode) {
            WhiteDnsOptions.OperatorMegafonYota,
            WhiteDnsOptions.OperatorMts,
            WhiteDnsOptions.OperatorBeeline,
            WhiteDnsOptions.OperatorTele2,
            -> operatorCode
            else -> WhiteDnsOptions.OperatorMegafonYota
        }
    }

    private fun WhiteDnsSettings.applyOperatorRuntimeSettings(operatorCode: String): WhiteDnsSettings {
        return when (normalizeOperatorCode(operatorCode)) {
            WhiteDnsOptions.OperatorMts -> copy(
                balancingStrategy = 3,
                uploadDuplication = "2",
                downloadDuplication = "4",
                uploadCompression = 2,
                downloadCompression = 2,
                baseEncodeData = false,
                minUploadMtu = "40",
                minDownloadMtu = "100",
                maxUploadMtu = "140",
                maxDownloadMtu = "700",
                mtuTestRetriesResolvers = "3",
                mtuTestTimeoutResolvers = "2.0",
                mtuTestParallelismResolvers = "6",
                mtuTestRetriesLogs = "5",
                mtuTestTimeoutLogs = "2.0",
                mtuTestParallelismLogs = "32",
                rxTxWorkers = "4",
                tunnelProcessWorkers = "4",
                tunnelPacketTimeoutSeconds = "10.0",
                dispatcherIdlePollIntervalSeconds = "0.02",
                txChannelSize = "2048",
                rxChannelSize = "2048",
                resolverUdpConnectionPoolSize = "64",
                streamQueueInitialCapacity = "128",
                orphanQueueInitialCapacity = "32",
                dnsResponseFragmentStoreCapacity = "256",
                maxActiveStreams = "2048",
                localHandshakeTimeoutSeconds = "5.0",
                socksUdpAssociateReadTimeoutSeconds = "30.0",
                clientTerminalStreamRetentionSeconds = "45.0",
                clientCancelledSetupRetentionSeconds = "120.0",
                sessionInitRetryBaseSeconds = "1.0",
                sessionInitRetryStepSeconds = "1.0",
                sessionInitRetryLinearAfter = "5",
                sessionInitRetryMaxSeconds = "60.0",
                sessionInitBusyRetryIntervalSeconds = "60.0",
                autoTuneEnabled = false,
                trafficWarmupEnabled = false,
                trafficKeepaliveIntervalSeconds = "5",
                logLevel = "WARN",
            )
            else -> this
        }
    }

    private fun WhiteDnsSettings.copyStormRuntimeSettingsFrom(source: WhiteDnsSettings): WhiteDnsSettings {
        return copy(
            listenIp = source.listenIp,
            listenPort = source.listenPort,
            httpProxyEnabled = source.httpProxyEnabled,
            httpProxyPort = source.httpProxyPort,
            socks5Authentication = source.socks5Authentication,
            socksUsername = source.socksUsername,
            socksPassword = source.socksPassword,
            localDnsEnabled = source.localDnsEnabled,
            localDnsPort = source.localDnsPort,
            balancingStrategy = source.balancingStrategy,
            uploadDuplication = source.uploadDuplication,
            downloadDuplication = source.downloadDuplication,
            uploadCompression = source.uploadCompression,
            downloadCompression = source.downloadCompression,
            baseEncodeData = source.baseEncodeData,
            minUploadMtu = source.minUploadMtu,
            minDownloadMtu = source.minDownloadMtu,
            maxUploadMtu = source.maxUploadMtu,
            maxDownloadMtu = source.maxDownloadMtu,
            mtuTestRetriesResolvers = source.mtuTestRetriesResolvers,
            mtuTestTimeoutResolvers = source.mtuTestTimeoutResolvers,
            mtuTestParallelismResolvers = source.mtuTestParallelismResolvers,
            mtuTestRetriesLogs = source.mtuTestRetriesLogs,
            mtuTestTimeoutLogs = source.mtuTestTimeoutLogs,
            mtuTestParallelismLogs = source.mtuTestParallelismLogs,
            rxTxWorkers = source.rxTxWorkers,
            tunnelProcessWorkers = source.tunnelProcessWorkers,
            tunnelPacketTimeoutSeconds = source.tunnelPacketTimeoutSeconds,
            dispatcherIdlePollIntervalSeconds = source.dispatcherIdlePollIntervalSeconds,
            txChannelSize = source.txChannelSize,
            rxChannelSize = source.rxChannelSize,
            resolverUdpConnectionPoolSize = source.resolverUdpConnectionPoolSize,
            streamQueueInitialCapacity = source.streamQueueInitialCapacity,
            orphanQueueInitialCapacity = source.orphanQueueInitialCapacity,
            dnsResponseFragmentStoreCapacity = source.dnsResponseFragmentStoreCapacity,
            maxActiveStreams = source.maxActiveStreams,
            localHandshakeTimeoutSeconds = source.localHandshakeTimeoutSeconds,
            socksUdpAssociateReadTimeoutSeconds = source.socksUdpAssociateReadTimeoutSeconds,
            clientTerminalStreamRetentionSeconds = source.clientTerminalStreamRetentionSeconds,
            clientCancelledSetupRetentionSeconds = source.clientCancelledSetupRetentionSeconds,
            sessionInitRetryBaseSeconds = source.sessionInitRetryBaseSeconds,
            sessionInitRetryStepSeconds = source.sessionInitRetryStepSeconds,
            sessionInitRetryLinearAfter = source.sessionInitRetryLinearAfter,
            sessionInitRetryMaxSeconds = source.sessionInitRetryMaxSeconds,
            sessionInitBusyRetryIntervalSeconds = source.sessionInitBusyRetryIntervalSeconds,
            startupMode = source.startupMode,
            pingWatchdogSeconds = source.pingWatchdogSeconds,
            trafficWarmupEnabled = source.trafficWarmupEnabled,
            trafficWarmupProbeCount = source.trafficWarmupProbeCount,
            trafficKeepaliveIntervalSeconds = source.trafficKeepaliveIntervalSeconds,
            autoTuneEnabled = source.autoTuneEnabled,
            parallelTestSelectedConfigIds = source.parallelTestSelectedConfigIds,
            parallelTestAggressivePresetsEnabled = source.parallelTestAggressivePresetsEnabled,
            logLevel = source.logLevel,
        )
    }

    private fun WhiteDnsSettings.applyCachedAutoTuneWinnerRuntimeSettings(operatorCode: String): WhiteDnsSettings {
        val normalizedOperatorCode = normalizeOperatorCode(operatorCode)
        val cachedWinnerConfigId = readCachedAutoTuneWinnerConfigId(normalizedOperatorCode)
            ?.takeIf(String::isNotBlank)
            ?: return this
        val winnerSettings = applyAutoTuneConfig(cachedWinnerConfigId) ?: return this
        lastAutoTuneWinnerConfigId = cachedWinnerConfigId
        return winnerSettings.copy(
            autoTuneEnabled = false,
            parallelTestSelectedConfigIds = listOf(cachedWinnerConfigId),
            parallelTestAggressivePresetsEnabled = cachedWinnerConfigId in WhiteDnsParallelTest.aggressiveConfigIds,
        )
    }

    private fun WhiteDnsSettings.applyAutoTuneConfig(configId: String): WhiteDnsSettings? {
        WhiteDnsParallelTest.presetIdFromConfigId(configId)?.let { presetId ->
            val preset = WhiteDnsAutoTunePresets.all.firstOrNull { it.id == presetId } ?: return null
            return applyAutoTunePreset(preset)
                .copy(autoTuneEnabled = false)
                .syncSelectedConnectionProfileFields()
        }

        WhiteDnsParallelTest.settingProfileIdFromConfigId(configId)?.let { profileId ->
            val profile = normalizedAdvancedProfiles().firstOrNull { it.id == profileId } ?: return null
            return applyAdvancedProfile(profile)
                .copy(autoTuneEnabled = false)
                .syncSelectedConnectionProfileFields()
        }

        return null
    }

    private fun WhiteDnsSettings.applyParallelResolverRuntimeSettings(): WhiteDnsSettings {
        val resolverCount = resolve().resolverEntries.size.coerceIn(1, 30)
        fun atLeastResolverCount(rawValue: String, defaultValue: Int, maxValue: Int): String {
            val value = rawValue.trim().toIntOrNull() ?: defaultValue
            return value.coerceAtLeast(resolverCount).coerceAtMost(maxValue).toString()
        }
        return copy(
            uploadDuplication = atLeastResolverCount(uploadDuplication, resolverCount, 30),
            downloadDuplication = atLeastResolverCount(downloadDuplication, resolverCount, 30),
            mtuTestParallelismResolvers = atLeastResolverCount(mtuTestParallelismResolvers, resolverCount, 1024),
        )
    }

    private fun WhiteDnsSettings.applyFastStartupRuntimeSettings(useCachedResolvers: Boolean): WhiteDnsSettings {
        if (!useCachedResolvers) {
            return this
        }
        return copy(
            mtuTestRetriesResolvers = "1",
            mtuTestTimeoutResolvers = "1.0",
            localHandshakeTimeoutSeconds = "3.0",
            sessionInitRetryBaseSeconds = "0.5",
            sessionInitRetryStepSeconds = "0.5",
        )
    }

    private fun measureCloudflareSpeedBytesPerSecond(
        onLog: (String) -> Unit,
        socksProxyPort: Int? = null,
    ): Long {
        var bestSpeed = 0L
        repeat(CloudflareSpeedAttempts) { attemptIndex ->
            CloudflareSpeedDownloadBytes.forEachIndexed { sizeIndex, bytes ->
                val url = "https://speed.cloudflare.com/__down?bytes=$bytes&cacheBust=${UUID.randomUUID()}"
                val result = measureCloudflareDownloadSpeed(
                    downloadUrl = url,
                    maxBytes = bytes,
                    socksProxyPort = socksProxyPort,
                )
                if (result.bytesPerSecond > 0L) {
                    bestSpeed = maxOf(bestSpeed, result.bytesPerSecond)
                    val suffix = if (result.message == "ok") "" else " (${result.message})"
                    onLog(
                        "Cloudflare попытка ${attemptIndex + 1}.${sizeIndex + 1}: " +
                            "${"%.2f".format(Locale.US, result.bytesPerSecond * 8.0 / 1_000_000.0)} Мбит/с$suffix",
                    )
                    return bestSpeed
                }
                onLog("Cloudflare попытка ${attemptIndex + 1}.${sizeIndex + 1}: ${result.message}")
            }
        }
        return bestSpeed
    }

    private fun measureCloudflarePostConnectBytesPerSecond(
        onLog: (String) -> Unit,
        socksProxyPort: Int? = null,
        downloadBytes: List<Long> = CloudflarePostConnectDownloadBytes,
        attempts: Int = CloudflarePostConnectAttempts,
        connectTimeoutMillis: Int = CloudflarePostConnectConnectTimeoutMillis,
        readTimeoutMillis: Int = CloudflarePostConnectReadTimeoutMillis,
        logPrefix: String = "Cloudflare check",
    ): Long {
        var bestSpeed = 0L
        repeat(attempts) { attemptIndex ->
            downloadBytes.forEachIndexed { sizeIndex, bytes ->
                val url = "https://speed.cloudflare.com/__down?bytes=$bytes&cacheBust=${UUID.randomUUID()}"
                val result = measureCloudflareDownloadSpeed(
                    downloadUrl = url,
                    maxBytes = bytes,
                    socksProxyPort = socksProxyPort,
                    connectTimeoutMillis = connectTimeoutMillis,
                    readTimeoutMillis = readTimeoutMillis,
                )
                if (result.bytesPerSecond > 0L) {
                    bestSpeed = maxOf(bestSpeed, result.bytesPerSecond)
                    val suffix = if (result.message == "ok") "" else " (${result.message})"
                    onLog(
                        "$logPrefix ${attemptIndex + 1}.${sizeIndex + 1}: " +
                            "${formatTrafficSpeed(result.bytesPerSecond)}$suffix",
                    )
                    return bestSpeed
                }
                onLog("$logPrefix ${attemptIndex + 1}.${sizeIndex + 1}: ${result.message}")
            }
        }
        return bestSpeed
    }

    private fun measureCloudflareBenchmarkSpeedSummary(
        onLog: (String) -> Unit,
        logPrefix: String,
        socksProxyPort: Int? = null,
    ): SpeedBenchmarkSummary {
        var bestSpeed = 0L
        var successfulSamples = 0
        CloudflareBenchmarkDownloadBytes.forEachIndexed { sampleIndex, bytes ->
            val url = "https://speed.cloudflare.com/__down?bytes=$bytes&cacheBust=${UUID.randomUUID()}"
            val result = measureCloudflareDownloadSpeed(
                downloadUrl = url,
                maxBytes = bytes,
                socksProxyPort = socksProxyPort,
                connectTimeoutMillis = CloudflareBenchmarkConnectTimeoutMillis,
                readTimeoutMillis = CloudflareBenchmarkReadTimeoutMillis,
            )
            if (result.bytesPerSecond > 0L) {
                successfulSamples += 1
                bestSpeed = maxOf(bestSpeed, result.bytesPerSecond)
                val suffix = if (result.message == "ok") "" else " (${result.message})"
                onLog(
                    "$logPrefix Cloudflare sample ${sampleIndex + 1}/${CloudflareBenchmarkDownloadBytes.size}: " +
                        "${formatTrafficSpeed(result.bytesPerSecond)}$suffix",
                )
            } else {
                onLog(
                    "$logPrefix Cloudflare sample ${sampleIndex + 1}/${CloudflareBenchmarkDownloadBytes.size}: " +
                        result.message,
                )
            }
        }
        return SpeedBenchmarkSummary(
            bestBytesPerSecond = bestSpeed,
            successfulSamples = successfulSamples,
        )
    }

    private suspend fun runPostConnectHttpHealthCheck(onLog: (String) -> Unit): Boolean {
        return measurePostConnectHttpHealthScore(
            onLog = onLog,
            logPrefix = "HTTP",
        ) >= PostConnectHealthSuccessThreshold
    }

    private suspend fun measurePostConnectHttpHealthScore(
        onLog: (String) -> Unit,
        logPrefix: String,
        connectTimeoutMillis: Int = PostConnectHealthConnectTimeoutMillis,
        readTimeoutMillis: Int = PostConnectHealthReadTimeoutMillis,
    ): Int = coroutineScope {
        val results = PostConnectHealthUrls
            .map { endpoint ->
                async(Dispatchers.IO) {
                    endpoint to checkHttpHealthEndpoint(endpoint, connectTimeoutMillis, readTimeoutMillis)
                }
            }
            .awaitAll()
        results.forEach { (endpoint, result) ->
            onLog("$logPrefix ${endpoint.label}: ${result.message}")
        }
        results.count { (_, result) -> result.ok }
    }

    private fun checkHttpHealthEndpoint(
        endpoint: PostConnectHealthEndpoint,
        connectTimeoutMillis: Int,
        readTimeoutMillis: Int,
    ): HealthProbeResult {
        val connection = runCatching {
            (URL(endpoint.url).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                useCaches = false
                connectTimeout = connectTimeoutMillis
                readTimeout = readTimeoutMillis
                requestMethod = "GET"
                setRequestProperty("User-Agent", PostConnectHealthUserAgent)
                setRequestProperty("Cache-Control", "no-cache")
            }
        }.getOrElse { error ->
            return HealthProbeResult(ok = false, message = error.readableNetworkMessage())
        }
        return try {
            val responseCode = connection.responseCode
            val ok = responseCode in 200..399
            HealthProbeResult(
                ok = ok,
                message = if (ok) {
                    "HTTP $responseCode"
                } else {
                    "HTTP $responseCode"
                },
            )
        } catch (error: Exception) {
            HealthProbeResult(ok = false, message = error.readableNetworkMessage())
        } finally {
            connection.disconnect()
        }
    }

    private fun measureCloudflareDownloadSpeed(
        downloadUrl: String,
        maxBytes: Long,
        socksProxyPort: Int? = null,
        connectTimeoutMillis: Int = CloudflareSpeedConnectTimeoutMillis,
        readTimeoutMillis: Int = CloudflareSpeedReadTimeoutMillis,
    ): SpeedProbeResult {
        val startedAt = System.currentTimeMillis()
        var bytesRead = 0L
        var readError: Throwable? = null
        val connection = runCatching {
            openCloudflareConnection(
                url = downloadUrl,
                socksProxyPort = socksProxyPort,
                connectTimeoutMillis = connectTimeoutMillis,
                readTimeoutMillis = readTimeoutMillis,
            ).apply {
                useCaches = false
            }
        }.getOrElse { error ->
            return SpeedProbeResult(0L, error.readableNetworkMessage())
        }

        try {
            runCatching {
                val responseCode = connection.responseCode
                if (responseCode !in 200..299) {
                    return SpeedProbeResult(0L, "HTTP $responseCode")
                }
                BufferedInputStream(connection.inputStream).use { input ->
                    val buffer = ByteArray(16 * 1024)
                    while (bytesRead < maxBytes) {
                        val read = input.read(buffer)
                        if (read <= 0) {
                            break
                        }
                        bytesRead += read
                    }
                }
            }.onFailure { error ->
                readError = error
            }
        } finally {
            connection.disconnect()
        }

        if (bytesRead <= 0L) {
            return SpeedProbeResult(0L, readError?.readableNetworkMessage() ?: "скачано 0 байт")
        }

        val elapsedMillis = (System.currentTimeMillis() - startedAt).coerceAtLeast(1L)
        val speedBytesPerSecond = bytesRead * 1000L / elapsedMillis
        val error = readError
        return if (error != null) {
            SpeedProbeResult(speedBytesPerSecond, "partial: ${error.readableNetworkMessage()}")
        } else {
            SpeedProbeResult(speedBytesPerSecond, "ok")
        }
    }

    private fun openCloudflareConnection(
        url: String,
        socksProxyPort: Int? = null,
        connectTimeoutMillis: Int = CloudflareSpeedConnectTimeoutMillis,
        readTimeoutMillis: Int = CloudflareSpeedReadTimeoutMillis,
    ): HttpURLConnection {
        val proxy = socksProxyPort?.let {
            Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", it))
        }
        val connection = if (proxy != null) {
            URL(url).openConnection(proxy)
        } else {
            URL(url).openConnection()
        }
        return (connection as HttpURLConnection).apply {
            connectTimeout = connectTimeoutMillis
            readTimeout = readTimeoutMillis
            setRequestProperty("User-Agent", CloudflareSpeedUserAgent)
            setRequestProperty("Cache-Control", "no-cache")
        }
    }

    private fun measureYandexInternetometerSpeedBytesPerSecond(
        onLog: (String) -> Unit,
        socksProxyPort: Int? = null,
    ): Long {
        val downloadUrls = yandexInternetometerDownloadUrls(onLog, socksProxyPort)
        if (downloadUrls.isEmpty()) {
            onLog("Яндекс Интернетометр: нет доступных файлов для теста")
            return 0L
        }

        var bestSpeed = 0L
        repeat(YandexSpeedAttempts) { attemptIndex ->
            downloadUrls.forEachIndexed { urlIndex, downloadUrl ->
                val result = measureDownloadSpeed(downloadUrl, socksProxyPort)
                if (result.bytesPerSecond > 0L) {
                    bestSpeed = maxOf(bestSpeed, result.bytesPerSecond)
                    val suffix = if (result.message == "ok") "" else " (${result.message})"
                    onLog(
                        "Яндекс попытка ${attemptIndex + 1}.${urlIndex + 1}: " +
                            "${"%.2f".format(Locale.US, result.bytesPerSecond * 8.0 / 1_000_000.0)} Мбит/с$suffix",
                    )
                    return bestSpeed
                }
                onLog("Яндекс попытка ${attemptIndex + 1}.${urlIndex + 1}: ${result.message}")
            }
        }
        return bestSpeed
    }

    private fun measureDownloadSpeed(downloadUrl: String, socksProxyPort: Int? = null): SpeedProbeResult {
        val startedAt = System.currentTimeMillis()
        var bytesRead = 0L
        var readError: Throwable? = null
        val connection = runCatching {
            openYandexConnection(downloadUrl, socksProxyPort).apply {
                useCaches = false
                setRequestProperty("Range", "bytes=0-${YandexSpeedMaxBytes - 1}")
            }
        }.getOrElse { error ->
            return SpeedProbeResult(0L, error.readableNetworkMessage())
        }

        try {
            runCatching {
                val responseCode = connection.responseCode
                if (responseCode !in 200..299 && responseCode != HttpURLConnection.HTTP_PARTIAL) {
                    return SpeedProbeResult(0L, "HTTP $responseCode")
                }
                BufferedInputStream(connection.inputStream).use { input ->
                    val buffer = ByteArray(16 * 1024)
                    while (bytesRead < YandexSpeedMaxBytes) {
                        val read = input.read(buffer)
                        if (read <= 0) {
                            break
                        }
                        bytesRead += read
                    }
                }
            }.onFailure { error ->
                readError = error
            }
        } finally {
            connection.disconnect()
        }

        if (bytesRead <= 0L) {
            return SpeedProbeResult(0L, readError?.readableNetworkMessage() ?: "скачано 0 байт")
        }

        val elapsedMillis = (System.currentTimeMillis() - startedAt).coerceAtLeast(1L)
        val speedBytesPerSecond = bytesRead * 1000L / elapsedMillis
        val error = readError
        return if (error != null) {
            SpeedProbeResult(
                speedBytesPerSecond,
                "partial: ${error.readableNetworkMessage()}",
            )
        } else {
            SpeedProbeResult(speedBytesPerSecond, "ok")
        }
    }

    private fun yandexInternetometerDownloadUrls(
        onLog: (String) -> Unit,
        socksProxyPort: Int? = null,
    ): List<String> {
        return runCatching {
            val configUrl = "https://yandex.ru/internet/api/v0/get-probes?t=${System.currentTimeMillis()}"
            val connection = openYandexConnection(configUrl, socksProxyPort)
            val body = try {
                val responseCode = connection.responseCode
                if (responseCode !in 200..299) {
                    onLog("Яндекс Интернетометр config: HTTP $responseCode")
                    return fallbackYandexInternetometerDownloadUrls(onLog)
                }
                connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            } finally {
                connection.disconnect()
            }
            val probes = JSONObject(body)
                .optJSONObject("download")
                ?.optJSONArray("probes")
                ?: return fallbackYandexInternetometerDownloadUrls(onLog)
            val preferredUrls = mutableListOf<String>()
            val fallbackUrls = mutableListOf<String>()
            for (index in 0 until probes.length()) {
                val probe = probes.optJSONObject(index) ?: continue
                val url = probe.optString("url").takeIf(String::isNotBlank) ?: continue
                if ("50mb" in url.lowercase(Locale.US)) {
                    preferredUrls += url
                } else {
                    fallbackUrls += url
                }
            }
            val apiUrls = (preferredUrls + fallbackUrls)
                .distinct()
                .take(YandexSpeedMaxProbeUrls)
                .map { it.withYandexRid() }
            apiUrls.ifEmpty { fallbackYandexInternetometerDownloadUrls(onLog) }
        }.getOrElse { error ->
            onLog("Яндекс Интернетометр config: ${error.readableNetworkMessage()}")
            fallbackYandexInternetometerDownloadUrls(onLog)
        }
    }

    private fun fallbackYandexInternetometerDownloadUrls(onLog: (String) -> Unit): List<String> {
        onLog("Яндекс Интернетометр: пробую прямые CDN probe")
        val mid = "android${System.currentTimeMillis()}"
        return YandexFallbackDownloadProbes
            .map { probe ->
                "https://${probe.host}/cdnrphoszsa2sp7ilm7a/probes/50mb?lid=${probe.lid}&mid=$mid"
                    .withYandexRid()
            }
            .take(YandexSpeedMaxProbeUrls)
    }

    private fun openYandexConnection(url: String, socksProxyPort: Int? = null): HttpURLConnection {
        val proxy = socksProxyPort?.let {
            Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", it))
        }
        val connection = if (proxy != null) {
            URL(url).openConnection(proxy)
        } else {
            URL(url).openConnection()
        }
        return (connection as HttpURLConnection).apply {
            connectTimeout = YandexSpeedConnectTimeoutMillis
            readTimeout = YandexSpeedReadTimeoutMillis
            setRequestProperty("User-Agent", YandexInternetometerUserAgent)
            setRequestProperty("Referer", "https://yandex.ru/internet/")
            setRequestProperty("Origin", "https://yandex.ru")
            setRequestProperty("Cache-Control", "no-cache")
        }
    }

    private fun Throwable.readableNetworkMessage(): String {
        return message?.takeIf(String::isNotBlank) ?: javaClass.simpleName
    }

    private fun String.withYandexRid(): String {
        val separator = if ("?" in this) "&" else "?"
        return "$this${separator}rid=${UUID.randomUUID().toString().replace("-", "")}"
    }

    private data class SpeedProbeResult(
        val bytesPerSecond: Long,
        val message: String,
    )

    private data class SpeedBenchmarkSummary(
        val bestBytesPerSecond: Long,
        val successfulSamples: Int,
    )

    private data class ResolverProbeSummary(
        val successes: Int,
        val attempts: Int,
        val averageLatencyMillis: Long,
    )

    private data class HealthProbeResult(
        val ok: Boolean,
        val message: String,
    )

    private data class PostConnectHealthEndpoint(
        val label: String,
        val url: String,
    )

    private data class YandexFallbackProbe(
        val host: String,
        val lid: String,
    )

    private data class DnsDiscoveryNetwork(
        val network: android.net.Network?,
        val dnsResolvers: List<String>,
        val seedIps: List<String>,
    )

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
        val cacheableResolvers = resolvers
            .map(String::trim)
            .filter(String::isNotEmpty)
            .filterNot { it in YandexDnsFallbackResolvers }
            .distinct()
        if (cacheableResolvers.isEmpty()) {
            return
        }
        val normalizedOperatorCode = normalizeOperatorCode(operatorCode)
        val currentResolvers = fastResolverStore.getString(KeyCachedResolvers, null)
            ?.let { validateResolverText(it).normalizedResolvers }
            .orEmpty()
        val mergedResolvers = (currentResolvers + cacheableResolvers)
            .filterNot { it in YandexDnsFallbackResolvers }
            .distinct()
            .joinToString(separator = "\n")
        val currentOperatorResolvers = fastResolverStore.getString(cachedResolversKey(normalizedOperatorCode), null)
            ?.let { validateResolverText(it).normalizedResolvers }
            .orEmpty()
        val mergedOperatorResolvers = (currentOperatorResolvers + cacheableResolvers)
            .filterNot { it in YandexDnsFallbackResolvers }
            .distinct()
            .joinToString(separator = "\n")
        fastResolverStore.edit()
            .putString(KeyCachedResolvers, mergedResolvers)
            .putString(cachedResolversKey(normalizedOperatorCode), mergedOperatorResolvers)
            .apply()
    }

    private fun readCachedResolvers(operatorCode: String): List<String> {
        val normalizedOperatorCode = normalizeOperatorCode(operatorCode)
        val operatorResolvers = fastResolverStore.getString(cachedResolversKey(normalizedOperatorCode), null)
            ?.let { validateResolverText(it).normalizedResolvers }
            .orEmpty()
        val globalResolvers = fastResolverStore.getString(KeyCachedResolvers, null)
            ?.let { validateResolverText(it).normalizedResolvers }
            .orEmpty()
        val lastSuccessfulResolver = fastResolverStore.getString(lastSuccessfulResolverKey(normalizedOperatorCode), null)
            ?: fastResolverStore.getString(KeyLastSuccessfulResolver, null)
        return (listOfNotNull(lastSuccessfulResolver) + operatorResolvers + globalResolvers)
            .mapNotNull { validateResolverText(it).normalizedResolvers.firstOrNull() }
            .filterNot { it in YandexDnsFallbackResolvers }
            .distinct()
    }

    private fun cachedResolversKey(operatorCode: String): String {
        return "$KeyCachedResolvers.${normalizeOperatorCode(operatorCode)}"
    }

    private fun lastSuccessfulResolverKey(operatorCode: String): String {
        return "$KeyLastSuccessfulResolver.${normalizeOperatorCode(operatorCode)}"
    }

    private fun readCachedAutoTuneWinnerConfigId(operatorCode: String): String? {
        return fastResolverStore
            .getString(autoTuneWinnerConfigKey(operatorCode), null)
            ?.takeIf(String::isNotBlank)
    }

    private fun cacheAutoTuneWinnerConfigId(operatorCode: String, configId: String) {
        if (configId.isBlank()) {
            return
        }
        fastResolverStore.edit()
            .putString(autoTuneWinnerConfigKey(operatorCode), configId)
            .apply()
    }

    private fun autoTuneWinnerConfigKey(operatorCode: String): String {
        return "$KeyAutoTuneWinnerConfig.${normalizeOperatorCode(operatorCode)}"
    }

    private fun readResolverBenchmarkWinnerId(operatorCode: String, localResolvers: List<String>): String? {
        return fastResolverStore
            .getString(resolverBenchmarkWinnerKey(operatorCode, localResolvers), null)
            ?.takeIf(String::isNotBlank)
    }

    private fun resolverBenchmarkWinnerKey(operatorCode: String, localResolvers: List<String>): String {
        return "$KeyResolverBenchmarkWinner.${normalizeOperatorCode(operatorCode)}.${resolverBenchmarkSignature(localResolvers)}"
    }

    private fun resolverBenchmarkWinnerResolversKey(operatorCode: String, localResolvers: List<String>): String {
        return "$KeyResolverBenchmarkWinnerResolvers.${normalizeOperatorCode(operatorCode)}.${resolverBenchmarkSignature(localResolvers)}"
    }

    private fun resolverBenchmarkSignature(resolvers: List<String>): String {
        return validateResolverText(resolvers.joinToString(separator = "\n"))
            .normalizedResolvers
            .distinct()
            .joinToString(separator = ",")
            .hashCode()
            .toString()
    }

    private fun resolverBenchmarkLabel(winnerId: String): String {
        return when (winnerId) {
            ResolverBenchmarkWinnerYandex -> "Yandex DNS"
            ResolverBenchmarkWinnerLocal -> "local DNS"
            else -> "custom DNS"
        }
    }

    private fun prewarmStormDnsRuntime(settings: WhiteDnsSettings) {
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
        val resolver = (resolverState.activeResolvers + resolverState.validResolvers)
            .firstOrNull()
            ?.let { validateResolverText(it).normalizedResolvers.firstOrNull() }
            ?: return
        if (resolver in YandexDnsFallbackResolvers) {
            return
        }
        if (fastResolverStore.getString(KeyLastSuccessfulResolver, null) == resolver) {
            return
        }
        val operatorCode = normalizeOperatorCode(uiState.settings.operatorCode)
        mergeCachedResolvers(listOf(resolver), operatorCode)
        fastResolverStore.edit()
            .putString(KeyLastSuccessfulResolver, resolver)
            .putString(lastSuccessfulResolverKey(operatorCode), resolver)
            .apply()
        appendLog("Cached fast resolver: $resolver")
    }

    private fun reportSuccessfulRegistryResolvers(resolverState: ResolverRuntimeState) {
        if (uiState.connectionStatus != ConnectionStatus.CONNECTED) {
            return
        }
        val runtimeResolvers = WhiteDnsScannerResultStore.normalizeResolverEntries(
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
        val discoveryNetworks = dnsDiscoveryNetworks(appContext.getSystemService(ConnectivityManager::class.java))
        reportRegistryResolvers(
            operatorCode = operatorCode,
            resolvers = resolvers,
            discoveryNetworks = discoveryNetworks,
        )
        appendLog("Registry report: operator=$operatorCode, resolvers=${resolvers.joinToString()}")
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

    private fun registryReportableResolverOrNull(resolver: String): String? {
        val normalizedResolver = registryResolverHostOrNull(resolver) ?: return null
        if (normalizedResolver in YandexDnsFallbackResolvers || normalizedResolver == "114.114.114.114") {
            return null
        }
        val octets = normalizedResolver.split('.').mapNotNull(String::toIntOrNull)
        if (octets.size != 4) {
            return null
        }
        if (octets[0] == 192 && octets[1] == 168) {
            return null
        }
        if (octets[0] == 172 && octets[1] in 16..31) {
            return null
        }
        return normalizedResolver.takeIf(::isUsableIpv4Address)
    }

    private fun registryResolverHostOrNull(resolver: String): String? {
        val text = resolver.trim()
        val host = when {
            text.startsWith("[") && "]:" in text -> text.substringAfter("[").substringBefore("]")
            text.count { it == ':' } == 1 && "." in text -> text.substringBefore(":")
            else -> text
        }.trim()
        return host.takeIf(::isUsableIpv4Address)
    }

    private fun buildPreparedScanMessage(imported: ImportedScanResolverFile): String {
        val parts = mutableListOf<String>()
        parts += "Imported ${imported.totalResolverCount} resolver${if (imported.totalResolverCount == 1) "" else "s"}"
        if (imported.pendingResolverCount > 0) {
            parts += "Ready to scan ${imported.pendingResolverCount} entr${if (imported.pendingResolverCount == 1) "y" else "ies"}"
        } else {
            parts += "No new resolvers to scan"
        }
        if (imported.alreadyValidResolverCount > 0) {
            parts += "skipped ${imported.alreadyValidResolverCount} already in Scanner result"
        }
        if (imported.invalidEntryCount > 0) {
            parts += "ignored ${imported.invalidEntryCount} invalid entries"
        }
        return parts.joinToString(separator = "\n")
    }

    private fun syncScannerResultProfile(
        currentSettings: WhiteDnsSettings,
        scanState: WhiteDnsScanState,
    ): WhiteDnsSettings? {
        if (scanState.isRunning) {
            return null
        }
        val scannerResultText = WhiteDnsScannerResultStore.readValidResolvers(appContext)
            .joinToString(separator = "\n")
        if (scannerResultText.isBlank() || scannerResultText == lastScannerResultProfileText) {
            return null
        }

        val normalizedSettings = currentSettings.syncSelectedConnectionProfileFields()
        val resolverProfiles = normalizedSettings.normalizedResolverProfiles()
        val existingIndex = resolverProfiles.indexOfFirst { it.name == ScannerResultProfileName }
        if (existingIndex >= 0 && resolverProfiles[existingIndex].resolverText == scannerResultText) {
            lastScannerResultProfileText = scannerResultText
            return null
        }

        val scannerResultProfile = if (existingIndex >= 0) {
            resolverProfiles[existingIndex].copy(resolverText = scannerResultText)
        } else {
            ResolverProfile(
                id = ResolverProfile.newId(),
                name = ScannerResultProfileName,
                resolverText = scannerResultText,
            )
        }
        val updatedProfiles = if (existingIndex >= 0) {
            resolverProfiles.toMutableList().also { profiles ->
                profiles[existingIndex] = scannerResultProfile
            }
        } else {
            resolverProfiles + scannerResultProfile
        }
        val updatedSettings = normalizedSettings.copy(
            resolverProfiles = updatedProfiles,
        ).syncSelectedConnectionProfileFields()
        settingsStore.save(updatedSettings)
        lastScannerResultProfileText = scannerResultText
        return updatedSettings
    }

    private fun displayNameForUri(uri: Uri): String {
        return runCatching {
            appContext.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (index >= 0) {
                            cursor.getString(index)
                        } else {
                            null
                        }
                    } else {
                        null
                    }
                }
        }.getOrNull()
            ?.takeIf(String::isNotBlank)
            ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf(String::isNotBlank)
            ?: "Resolver file"
    }

    private fun resolveScanConnectionProfileId(
        settings: WhiteDnsSettings,
        requestedProfileId: String,
    ): String {
        val normalizedSettings = settings.syncSelectedConnectionProfileFields()
        val profiles = normalizedSettings.normalizedConnectionProfiles()
        return profiles.firstOrNull { it.id == requestedProfileId }?.id
            ?: normalizedSettings.selectedConnectionProfile().id
    }

    private fun selectServerProfile(settings: WhiteDnsSettings): StormDnsServerProfile? {
        return serverProfileFromConnectionProfile(settings.selectedConnectionProfile())
    }

    private fun serverProfileFromConnectionProfile(connectionProfile: ConnectionProfile): StormDnsServerProfile? {
        val domain = connectionProfile.customServerDomain
            .trim()
            .trimEnd('.')
        val encryptionKey = connectionProfile.customServerEncryptionKey.trim()
        if (domain.isBlank() || encryptionKey.isBlank()) {
            return null
        }
        return StormDnsServerProfile(
            id = connectionProfile.id.ifBlank { domain },
            label = connectionProfile.name.ifBlank { domain },
            domain = domain,
            encryptionKey = encryptionKey,
            encryptionMethod = connectionProfile.customServerEncryptionMethod.coerceIn(0, 5),
        )
    }

    private fun startConnectionVerification(expectedConnectionMode: String) {
        verificationJob?.cancel()
        uiState = uiState.copy(
            connectionVerification = ConnectionVerificationState(
                status = ConnectionVerificationStatus.Checking,
                message = "Checking tunnel route",
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
        if (!canConnectToLocalPort(activeProxyListenPort)) {
            return failedVerification("Connection verification failed: local SOCKS listener is not reachable")
        }
        if (expectedConnectionMode == WhiteDnsRuntimeStateStore.ModeVpn && findVpnTrafficInterfaceName() == null) {
            return failedVerification("Connection verification failed: VPN interface is not active")
        }

        val probePassed = repeatBooleanAttempt(VerificationProbeAttempts) {
            WhiteDnsTrafficWarmup.verifySocksRoute(resolvedSettings)
        }
        return ConnectionVerificationState(
            status = ConnectionVerificationStatus.Verified,
            message = if (probePassed) {
                if (expectedConnectionMode == WhiteDnsRuntimeStateStore.ModeVpn) {
                    "Connection verified: VPN tunnel can reach the internet"
                } else {
                    "Connection verified: proxy tunnel can reach the internet"
                }
            } else {
                if (expectedConnectionMode == WhiteDnsRuntimeStateStore.ModeVpn) {
                    "Connection ready: VPN tunnel is active; outbound probe is still warming up"
                } else {
                    "Connection ready: proxy tunnel is active; outbound probe is still warming up"
                }
            },
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

    private fun buildConnectionStats(listenPort: Int): ConnectionStats {
        val connectedApps = maxOf(
            countActiveProxyClients(listenPort),
            countTrackedSocksStreams(),
        )
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
        if (uiState.settings.resolve().connectionMode == "vpn") {
            currentVpnTrafficSnapshot()?.let { snapshot ->
                return snapshot
            }
        }
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

    private fun currentVpnTrafficSnapshot(): TrafficSnapshot? {
        val cachedName = activeVpnTrafficInterfaceName
        if (cachedName != null) {
            val cachedCounters = readNetworkInterfaceCounters(cachedName)
            if (cachedCounters != null) {
                return cachedCounters.toTrafficSnapshot(cachedName)
            }
            activeVpnTrafficInterfaceName = null
        }

        val interfaceName = findVpnTrafficInterfaceName() ?: return null
        val counters = readNetworkInterfaceCounters(interfaceName) ?: return null
        activeVpnTrafficInterfaceName = interfaceName
        return counters.toTrafficSnapshot(interfaceName)
    }

    private fun Pair<Long, Long>.toTrafficSnapshot(interfaceName: String): TrafficSnapshot {
        return TrafficSnapshot(
            rxBytes = first,
            txBytes = second,
            timestampMillis = System.currentTimeMillis(),
            sourceKey = "$VpnTrafficSourcePrefix$interfaceName",
        )
    }

    private fun findVpnTrafficInterfaceName(): String? {
        return runCatching {
            NetworkInterface.getNetworkInterfaces()
                .asSequence()
                .firstOrNull { networkInterface ->
                    networkInterface.isUp &&
                        networkInterface.inetAddresses
                            .asSequence()
                            .any { address ->
                                address.hostAddress?.substringBefore('%') == WhiteDnsVpnService.TunIpv4Address
                            }
                }
                ?.name
        }.getOrNull()
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

    private fun readNetworkInterfaceCounters(interfaceName: String): Pair<Long, Long>? {
        if (!SafeNetworkInterfaceNameRegex.matches(interfaceName)) {
            return null
        }
        val statisticsDir = File(File(File("/sys/class/net"), interfaceName), "statistics")
        val rxBytes = readTrafficCounterFile(File(statisticsDir, "rx_bytes")) ?: return null
        val txBytes = readTrafficCounterFile(File(statisticsDir, "tx_bytes")) ?: return null
        return rxBytes to txBytes
    }

    private fun readTrafficCounterFile(file: File): Long? {
        return runCatching {
            file.readText()
                .trim()
                .toLongOrNull()
                ?.coerceAtLeast(0)
        }.getOrNull()
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

    private fun countActiveProxyClients(listenPort: Int): Int {
        val tcpPaths = listOf(
            "/proc/self/net/tcp",
            "/proc/self/net/tcp6",
            "/proc/net/tcp",
            "/proc/net/tcp6",
        )
        val localMatches = tcpPaths
            .flatMap { path -> activeTcpClientKeys(path, listenPort, matchLocalPort = true) }
            .distinct()
        if (localMatches.isNotEmpty()) {
            return localMatches.size
        }

        return tcpPaths
            .flatMap { path -> activeTcpClientKeys(path, listenPort, matchLocalPort = false) }
            .distinct()
            .size
    }

    private fun activeTcpClientKeys(
        path: String,
        listenPort: Int,
        matchLocalPort: Boolean,
    ): List<String> {
        return runCatching {
            java.io.File(path)
                .readLines()
                .drop(1)
                .mapNotNull { line ->
                    val columns = line.trim().split(Regex("\\s+"))
                    val localAddress = columns.getOrNull(1) ?: return@mapNotNull null
                    val remoteAddress = columns.getOrNull(2) ?: return@mapNotNull null
                    val state = columns.getOrNull(3) ?: return@mapNotNull null
                    val addressToMatch = if (matchLocalPort) localAddress else remoteAddress
                    val portHex = addressToMatch.substringAfterLast(':', missingDelimiterValue = "")
                    val port = portHex.toIntOrNull(radix = 16)
                    if (port == listenPort && state == EstablishedTcpState) {
                        "$localAddress-$remoteAddress-$state"
                    } else {
                        null
                    }
                }
        }.getOrDefault(emptyList())
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

    private data class ImportedScanResolverFile(
        val file: File,
        val sourceName: String,
        val pendingResolverCount: Int,
        val totalResolverCount: Int,
        val alreadyValidResolverCount: Int,
        val invalidEntryCount: Int,
    ) {
        val displayResolverCount: Int
            get() = if (pendingResolverCount > 0) pendingResolverCount else totalResolverCount
    }

    private data class ScanStateRefreshResult(
        val scanState: WhiteDnsScanState,
        val updatedSettings: WhiteDnsSettings?,
    )

    private fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val powerManager = context.getSystemService(PowerManager::class.java) ?: return true
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    private fun areNotificationsEnabled(context: Context): Boolean {
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private fun findDeviceNetworkIpAddress(): String {
        return runCatching {
            NetworkInterface.getNetworkInterfaces()
                .asSequence()
                .filter { it.isUp && !it.isLoopback && !it.isVirtual }
                .flatMap { it.inetAddresses.asSequence() }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { !it.isLoopbackAddress && !it.isLinkLocalAddress }
                ?.hostAddress
        }.getOrNull() ?: "127.0.0.1"
    }

    private fun <T> java.util.Enumeration<T>.asSequence(): Sequence<T> {
        return Collections.list(this).asSequence()
    }

    private fun appendLog(message: String) {
        viewModelScope.launch(Dispatchers.Main.immediate) {
            appendLogOnMain(message)
        }
    }

    private fun appendLogOnMain(message: String) {
        val cleanMessage = message
            .replace(Regex("\\u001B\\[[;\\d]*m"), "")
            .trim()
        if (cleanMessage.isEmpty()) {
            return
        }
        val nextLogs = (listOf(cleanMessage) + uiState.connectionLogs).take(MaxConnectionLogs)
        uiState = uiState.copy(connectionLogs = nextLogs)
    }

    private companion object {
        const val MaxConnectionLogs = 100
        const val RuntimeProgressUiUpdateIntervalMillis = 250L
        const val RuntimeResolverUiUpdateIntervalMillis = 500L
        const val FastResolverPreferencesName = "white_dns_fast_resolver"
        const val KeyLastSuccessfulResolver = "last_successful_resolver"
        const val KeyCachedResolvers = "cached_resolvers"
        const val KeyAutoTuneWinnerConfig = "auto_tune_winner_config"
        const val KeyResolverBenchmarkWinner = "resolver_benchmark_winner"
        const val KeyResolverBenchmarkWinnerResolvers = "resolver_benchmark_winner_resolvers"
        const val ResolverBenchmarkWinnerLocal = "local"
        const val ResolverBenchmarkWinnerYandex = "yandex"
        const val TargetResolverCount = 4
        const val ResolverRegistryUrl = "http://ns.alq.su/stormdns/resolvers"
        const val ResolverRegistryReportUrl = "http://ns.alq.su/stormdns/resolvers/report"
        const val RegistryConnectTimeoutMillis = 3_000
        const val RegistryReadTimeoutMillis = 4_000
        const val RegistryResolverProbeLimit = 64
        const val RegistryUserAgent = "StormDNS-Android/1.0"
        val CloudflareSpeedDownloadBytes = listOf(2_000_000L, 5_000_000L, 10_000_000L)
        const val CloudflareSpeedAttempts = 3
        const val CloudflareSpeedConnectTimeoutMillis = 8_000
        const val CloudflareSpeedReadTimeoutMillis = 30_000
        const val CloudflareSpeedUserAgent = "StormDNS-Android/1.0"
        val CloudflareBenchmarkDownloadBytes = listOf(5_000_000L, 10_000_000L)
        const val CloudflareBenchmarkConnectTimeoutMillis = 10_000
        const val CloudflareBenchmarkReadTimeoutMillis = 45_000
        const val ResolverBenchmarkDnsProbeAttempts = 3
        const val ResolverBenchmarkDnsProbeTimeoutMillis = 650
        const val ResolverBenchmarkYandexSpeedMultiplier = 2L
        const val ResolverBenchmarkYandexLatencyMultiplier = 2L
        val YandexDnsFallbackResolvers = listOf(
            "77.88.8.8",
            "77.88.8.1",
            "77.88.8.2",
            "77.88.8.3",
            "77.88.8.7",
            "77.88.8.88",
        )
        const val DnsScanBatchSize = 32
        const val DnsProbeTimeoutMillis = 450
        const val DnsDiscoveryTimeoutMillis = 40_000L
        const val NeighborSubnetDistance = 2
        val CloudflarePostConnectDownloadBytes = listOf(2_000_000L, 5_000_000L)
        const val CloudflarePostConnectAttempts = 2
        const val CloudflarePostConnectConnectTimeoutMillis = 10_000
        const val CloudflarePostConnectReadTimeoutMillis = 35_000
        const val AmneziaPostConnectStabilizationDelayMillis = 1_500L
        const val AmneziaPostConnectHealthConnectTimeoutMillis = 4_000
        const val AmneziaPostConnectHealthReadTimeoutMillis = 5_000
        val AmneziaQuickDownloadBytes = listOf(256_000L, 512_000L)
        const val AmneziaQuickDownloadAttempts = 1
        const val AmneziaQuickDownloadConnectTimeoutMillis = 5_000
        const val AmneziaQuickDownloadReadTimeoutMillis = 8_000
        const val PostConnectStabilizationDelayMillis = 5_000L
        const val PostConnectHealthAttempts = 4
        const val PostConnectHealthSuccessThreshold = 1
        const val PostConnectHealthRetryDelayMillis = 3_000L
        const val PostConnectHealthConnectTimeoutMillis = 10_000
        const val PostConnectHealthReadTimeoutMillis = 25_000
        const val PostConnectHealthUserAgent =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36"
        val PostConnectHealthUrls = listOf(
            PostConnectHealthEndpoint(
                label = "Android 204",
                url = "https://connectivitycheck.gstatic.com/generate_204",
            ),
            PostConnectHealthEndpoint(
                label = "Google 204",
                url = "https://www.google.com/generate_204",
            ),
            PostConnectHealthEndpoint(
                label = "Cloudflare trace",
                url = "https://www.cloudflare.com/cdn-cgi/trace",
            ),
            PostConnectHealthEndpoint(
                label = "Yandex",
                url = "https://ya.ru/",
            ),
        )
        const val SuccessfulSpeedThresholdMbps = 1.5
        const val YandexSpeedMaxBytes = 2_000_000L
        const val YandexSpeedAttempts = 3
        const val YandexSpeedMaxProbeUrls = 6
        const val YandexSpeedConnectTimeoutMillis = 10_000
        const val YandexSpeedReadTimeoutMillis = 20_000
        const val YandexInternetometerUserAgent =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36"
        val YandexFallbackDownloadProbes = listOf(
            YandexFallbackProbe(host = "cloudcdn-m9-5.cdn.yandex.net", lid = "94"),
            YandexFallbackProbe(host = "cloudcdn-mar-69.cdn.yandex.net", lid = "26"),
            YandexFallbackProbe(host = "cloudcdn-spbmiran-24.cdn.yandex.net", lid = "85"),
        )
        const val EstablishedTcpState = "01"
        const val RuntimeHealthRetryDelayMillis = 1_000L
        const val VpnStopBeforeStormDnsStopDelayMillis = 1_500L
        const val SocksStreamTrackingTtlMillis = 120_000L
        const val EmptyTrafficSource = "none"
        const val BatteryOptimizationRefreshAttempts = 8
        const val BatteryOptimizationRefreshRetryDelayMillis = 500L
        const val VerificationStartDelayMillis = 700L
        const val VerificationProbeAttempts = 2
        const val VerificationProbeRetryDelayMillis = 750L
        const val AutoTuneReadyTimeoutMillis = 90_000L
        const val AutoTuneSignalPollMillis = 100L
        const val AutoTuneMeasurementSettleMillis = 1_500L
        const val AutoTuneMaxConcurrentTrials = 3
        const val AutoTuneMeasurementProbeCount = 3
        const val AutoTuneMeasurementProbeDelayMillis = 500L
        const val AutoTuneSelectionHysteresisPercent = 20
        const val HighUsageUploadDuplicationThreshold = 10
        const val HighUsageDownloadDuplicationThreshold = 20
        const val AutoTuneResolverSubsetMinCount = 8
        const val AutoTuneResolverSubsetMaxCount = 24
        const val AutoTuneUnassignedPort = 0
        const val AutoTunePortReleaseTimeoutMillis = 3_000L
        const val AutoTunePortReleasePollMillis = 100L
        const val MaxScanWorkerDigits = 3
        const val StaleScanStateTimeoutMillis = 15_000L
        const val ScannerResultProfileName = "Scanner result"
        const val DefaultScanResolverAssetName = "default_resolvers.txt"
        const val UidTrafficSourcePrefix = "uid:"
        const val VpnTrafficSourcePrefix = "vpn:"
        val ParallelTestConnectionModes = setOf(
            WhiteDnsRuntimeStateStore.ModeProxy,
            WhiteDnsRuntimeStateStore.ModeVpn,
        )
        val socksStreamOpenedRegex = Regex("""New SOCKS\d TCP CONNECT .*Stream ID:\s*(\d+)""")
        val socksStreamClosedRegex = Regex("""ARQ Stream Closed .*Stream:\s*(\d+)""")
        val SafeNetworkInterfaceNameRegex = Regex("""[A-Za-z0-9_.:-]+""")
    }

    private data class AutoTuneTrialConfig(
        val id: String,
        val label: String,
        val userSettings: WhiteDnsSettings,
        val highUsage: Boolean,
    )

    private data class AutoTuneTrialPlan(
        val config: AutoTuneTrialConfig,
        val settings: WhiteDnsSettings,
        val result: AutoTuneTrialResult,
    )

    private data class AutoTuneTrialStartup(
        val plan: AutoTuneTrialPlan,
        val manager: StormDnsProcessManager,
        val ready: Boolean,
        val result: AutoTuneResult,
    )

    private data class AutoTuneResolverDiscoveryResult(
        val result: AutoTuneResult,
        val resolverEntries: List<String>,
    )

    private data class AutoTuneResult(
        val config: AutoTuneTrialConfig,
        val listenIp: String,
        val listenPort: Int,
        val scoreBytesPerSecond: Long,
        val pingMillis: Long?,
        val ready: Boolean,
    )

    private class AutoTuneResolverCollector {
        private val lock = Any()
        private val activeResolvers = linkedSetOf<String>()
        private val standbyResolvers = linkedSetOf<String>()
        private val validResolvers = linkedSetOf<String>()

        fun observe(line: String) {
            val state = parseStormDnsResolverStateLine(line) ?: return
            synchronized(lock) {
                activeResolvers += state.activeResolvers
                standbyResolvers += state.standbyResolvers
                validResolvers += state.validResolvers
            }
        }

        fun preferredResolvers(minCount: Int): List<String> {
            return synchronized(lock) {
                val activeAndStandby = (activeResolvers + standbyResolvers).distinct()
                val preferred = if (activeAndStandby.size >= minCount) {
                    activeAndStandby
                } else {
                    (activeAndStandby + validResolvers).distinct()
                }
                preferred
            }
        }
    }

    private data class ServerTestPlan(
        val serverProfile: StormDnsServerProfile,
        val settings: WhiteDnsSettings,
        val result: ServerTestResult,
    )

    private data class ServerTestStartup(
        val plan: ServerTestPlan,
        val manager: StormDnsProcessManager,
        val ready: Boolean,
    )

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
