package shop.whitezia.client

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import shop.whitezia.client.fallback.FallbackNetworkState
import shop.whitezia.client.fallback.FallbackTransport
import shop.whitezia.client.fallback.FallbackPlanAction
import shop.whitezia.client.fallback.FallbackPlanner
import shop.whitezia.client.model.ConnectionStatus
import shop.whitezia.client.fallback.HealthCheckFallbackAction
import shop.whitezia.client.fallback.TransportRestartCoordinator
import shop.whitezia.client.fallback.TransportRestartResult
import shop.whitezia.client.model.WhiteZiaOptions
import shop.whitezia.client.model.WhiteZiaSettings
import shop.whitezia.client.model.WhiteZiaThemeMode
import shop.whitezia.client.resolver.ResolverBenchmarkPhase
import shop.whitezia.client.resolver.ResolverBenchmarkScore
import shop.whitezia.client.ui.connect.WhiteZiaConnectScreen
import shop.whitezia.client.ui.WhiteZiaTheme
import shop.whitezia.client.ui.WhiteZiaViewModel
import shop.whitezia.client.ui.settings.WhiteZiaSettingsDialog
import shop.whitezia.client.account.WhiteZiaAccountDialog
import shop.whitezia.client.account.WhiteZiaAccountViewModel
import shop.whitezia.client.update.AppUpdateDialog
import shop.whitezia.client.update.AppUpdateInstaller
import shop.whitezia.client.update.AppUpdateState
import shop.whitezia.client.update.AppUpdateViewModel

class MainActivity : ComponentActivity() {

    private val viewModel by viewModels<WhiteZiaViewModel>()
    private val updateViewModel by viewModels<AppUpdateViewModel>()
    private val accountViewModel by viewModels<WhiteZiaAccountViewModel>()
    private val networkMonitor by lazy { MainNetworkMonitor(this) }
    private var inboundProfileLink by mutableStateOf("")

    override fun onResume() {
        super.onResume()
        viewModel.refreshBatteryOptimizationStatusWithRetry()
        viewModel.refreshNotificationStatus()
        viewModel.refreshRuntimeConnectionStatus()
        accountViewModel.refreshAfterResume()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val initialProfileLink = profileLinkFromIntent(intent) ?: viewModel.uiState.settings.subscriptionLink
        inboundProfileLink = profileLinkFromIntent(intent).orEmpty()

        setContent {
            WhiteZiaTheme(
                themeMode = WhiteZiaThemeMode.System,
                languageCode = viewModel.uiState.settings.languageCode,
            ) {
                val context = LocalContext.current
                val updateState = updateViewModel.state
                val accountState = accountViewModel.state
                LaunchedEffect(Unit) {
                    updateViewModel.checkOnStartup()
                }
                val openUpdateInstaller: (AppUpdateState.ReadyToInstall) -> Unit = { ready ->
                    runCatching {
                        context.startActivity(AppUpdateInstaller.installIntent(context, ready.apk))
                    }.onSuccess {
                        updateViewModel.installerOpened()
                    }.onFailure {
                        updateViewModel.installerError("Не удалось открыть системный установщик")
                    }
                }
                val installPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult(),
                ) {
                    val ready = updateViewModel.state as? AppUpdateState.ReadyToInstall
                    if (ready != null && AppUpdateInstaller.canInstallPackages(context)) {
                        openUpdateInstaller(ready)
                    } else if (ready != null) {
                        updateViewModel.installerError("Разрешение на установку приложений не выдано")
                    }
                }
                var subscriptionLink by rememberSaveable { mutableStateOf(initialProfileLink) }
                LaunchedEffect(accountState.pendingProfileBundle) {
                    accountState.pendingProfileBundle?.takeIf(String::isNotBlank)?.let { bundle ->
                        viewModel.updateSubscriptionLink(bundle)
                            .onSuccess {
                                subscriptionLink = bundle
                                accountViewModel.profileBundleApplied(bundle)
                            }
                            .onFailure { error ->
                                accountViewModel.profileBundleRejected(
                                    error.message?.takeIf(String::isNotBlank)
                                        ?: "Не удалось применить профиль устройства",
                                )
                            }
                    }
                }
                var errorMessage by remember { mutableStateOf<String?>(null) }
                val subscriptionQrScanner = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult(),
                ) { result ->
                    val decoded = result.data?.getStringExtra(QrScannerActivity.EXTRA_QR_VALUE)?.trim().orEmpty()
                    when {
                        result.resultCode == Activity.RESULT_OK &&
                            (decoded.startsWith("stormbundle://") || decoded.startsWith("stormdns://")) -> {
                            viewModel.updateSubscriptionLink(decoded)
                                .onSuccess {
                                    subscriptionLink = decoded
                                    errorMessage = null
                                }
                                .onFailure { error ->
                                    errorMessage = error.message ?: "Не удалось импортировать профиль"
                                }
                        }
                        result.resultCode == Activity.RESULT_OK -> {
                            errorMessage = "QR не содержит ссылку WhiteZia"
                        }
                        else -> {
                            errorMessage = result.data?.getStringExtra(QrScannerActivity.EXTRA_ERROR)
                                ?: "Сканирование QR отменено"
                        }
                    }
                }
                var visibleLog by rememberSaveable { mutableStateOf("Готов к подключению") }
                var fullVisibleLog by rememberSaveable { mutableStateOf("Готов к подключению") }
                var userStatus by remember { mutableStateOf("Готово к подключению") }
                var operatorDisplayLabel by rememberSaveable {
                    mutableStateOf(operatorLabel(viewModel.uiState.settings.operatorCode))
                }
                var wifiEnabled by remember { mutableStateOf(networkMonitor.isWifiNetworkAvailable()) }
                var wifiRadioEnabled by remember { mutableStateOf(networkMonitor.isWifiRadioEnabled()) }
                var activeBaseNetworkTransport by remember { mutableStateOf(networkMonitor.currentBaseNetworkTransport()) }
                var lastNetworkReconnectTransport by remember {
                    mutableStateOf(activeBaseNetworkTransport)
                }
                var showSplitTunnelDialog by rememberSaveable { mutableStateOf(false) }
                var showSettingsDialog by rememberSaveable { mutableStateOf(false) }
                var showAccountDialog by rememberSaveable { mutableStateOf(false) }
                var showLogDialog by rememberSaveable { mutableStateOf(false) }
                var postCheckAttempt by remember { mutableIntStateOf(0) }
                var completedPostCheckAttempt by remember { mutableIntStateOf(0) }
                var connectionLaunchStarted by remember { mutableStateOf(false) }
                var connectionWanted by remember {
                    mutableStateOf(viewModel.uiState.connectionStatus != ConnectionStatus.DISCONNECTED)
                }
                var disconnectingByUser by remember { mutableStateOf(false) }
                var resolverScanOperator by remember { mutableStateOf("") }
                var resolverBenchmarkPhase by remember { mutableStateOf(ResolverBenchmarkPhase.Idle) }
                var resolverBenchmarkLocalText by remember { mutableStateOf("") }
                var resolverBenchmarkLocalSpeed by remember { mutableLongStateOf(0L) }
                var resolverBenchmarkLocalScore by remember { mutableStateOf<ResolverBenchmarkScore?>(null) }
                var resolverBenchmarkReconnectJob by remember { mutableStateOf<Job?>(null) }
                var networkReconnectJob by remember { mutableStateOf<Job?>(null) }
                var profileRefreshJob by remember { mutableStateOf<Job?>(null) }
                var pendingNetworkReconnectTransport by remember { mutableStateOf("") }
                var pendingStormDnsAfterWifiOff by remember { mutableStateOf(false) }
                var pendingStormDnsAfterResolverScan by remember { mutableStateOf(false) }
                var pendingAmneziaFallback by remember { mutableStateOf(false) }
                var pendingXrayFallbackAfterAmnezia by remember { mutableStateOf(false) }
                var pendingXrayAfterWifiOff by remember { mutableStateOf(false) }
                var pendingDnsFallbackAfterAmnezia by remember { mutableStateOf(false) }
                var pendingDnsFallbackAfterXray by remember { mutableStateOf(false) }
                var allowDnsFallbackAfterXray by remember { mutableStateOf(false) }
                var resolverFallbackYandexAllowed by remember { mutableStateOf(false) }
                var resolverSetupFromCache by remember { mutableStateOf(false) }
                var resolverScanKick by remember { mutableIntStateOf(0) }
                var resolverFallbackConnectKick by remember { mutableIntStateOf(0) }
                var pendingActionAfterVpnPermission by remember { mutableStateOf(PermissionActionNone) }
                val transportRestartCoordinator = remember { TransportRestartCoordinator() }

                fun hasPendingAutomaticTransition(): Boolean =
                    pendingStormDnsAfterWifiOff ||
                        pendingStormDnsAfterResolverScan ||
                        pendingAmneziaFallback ||
                        pendingXrayFallbackAfterAmnezia ||
                        pendingXrayAfterWifiOff ||
                        pendingDnsFallbackAfterAmnezia ||
                        pendingDnsFallbackAfterXray ||
                        profileRefreshJob?.isActive == true

                fun clearPendingConnectionFlow() {
                    connectionWanted = false
                    connectionLaunchStarted = false
                    disconnectingByUser = false
                    pendingNetworkReconnectTransport = ""
                    pendingStormDnsAfterWifiOff = false
                    pendingStormDnsAfterResolverScan = false
                    pendingAmneziaFallback = false
                    pendingXrayFallbackAfterAmnezia = false
                    pendingXrayAfterWifiOff = false
                    pendingDnsFallbackAfterAmnezia = false
                    pendingDnsFallbackAfterXray = false
                    allowDnsFallbackAfterXray = false
                    resolverFallbackYandexAllowed = false
                    resolverSetupFromCache = false
                    resolverBenchmarkPhase = ResolverBenchmarkPhase.Idle
                    resolverBenchmarkLocalText = ""
                    resolverBenchmarkLocalSpeed = 0L
                    resolverBenchmarkLocalScore = null
                    pendingActionAfterVpnPermission = PermissionActionNone
                    resolverBenchmarkReconnectJob?.cancel()
                    networkReconnectJob?.cancel()
                    profileRefreshJob?.cancel()
                    profileRefreshJob = null
                }


                val appendFullVisibleLog: (String) -> Unit = { message ->
                    val cleanMessage = message.trim()
                    if (cleanMessage.isNotEmpty()) {
                        fullVisibleLog = (fullVisibleLog.lineSequence().toList() + cleanMessage)
                            .takeLast(WhiteZiaFullVisibleLogLimit)
                            .joinToString(separator = "\n")
                        viewModel.appendConnectionLog(cleanMessage)
                    }
                }
                val setVisibleLog: (String) -> Unit = { message ->
                    visibleLog = message
                    appendFullVisibleLog(message)
                }
                val addVisibleLog: (String) -> Unit = { message ->
                    val cleanMessage = message.trim()
                    if (cleanMessage.isNotEmpty()) {
                        visibleLog = (visibleLog.lineSequence().toList() + cleanMessage)
                            .takeLast(WhiteZiaVisibleLogTailLimit)
                            .joinToString(separator = "\n")
                        appendFullVisibleLog(cleanMessage)
                    }
                }
                fun failRuntimeStopTransition() {
                    clearPendingConnectionFlow()
                    resolverBenchmarkPhase = ResolverBenchmarkPhase.Idle
                    resolverBenchmarkLocalText = ""
                    resolverBenchmarkLocalSpeed = 0L
                    resolverBenchmarkLocalScore = null
                    userStatus = "Не удалось подключиться. Повторите попытку"
                    errorMessage = "VPN туннель не завершил работу"
                    addVisibleLog("Не удалось полностью остановить предыдущий VPN туннель")
                }

                fun isStormDnsBlockedByWifi(): Boolean = networkMonitor.isActiveWifiNetwork()
                fun currentFallbackNetworkState(): FallbackNetworkState = FallbackNetworkState(
                    activeWifi = networkMonitor.isActiveWifiNetwork(),
                    mobileAvailable = networkMonitor.isMobileNetworkAvailable(),
                )
                fun isXrayBlockedByNetwork(): Boolean =
                    FallbackPlanner.planManualXrayOnly(currentFallbackNetworkState()) ==
                        FallbackPlanAction.WaitForMobileForXray
                fun xrayNetworkWaitMessage(): String {
                    val network = currentFallbackNetworkState()
                    return when {
                        network.activeWifi -> "Выключите Wi-Fi"
                        !network.mobileAvailable -> "Включите мобильный интернет"
                        else -> ""
                    }
                }
                fun markXrayNetworkWait(logMessage: String) {
                    val waitMessage = xrayNetworkWaitMessage()
                    pendingXrayAfterWifiOff = true
                    errorMessage = waitMessage
                    userStatus = waitMessage
                    addVisibleLog(logMessage)
                }
                var lastSimDetectionLogKey by remember { mutableStateOf("") }

                fun refreshDetectedOperator(
                    preferNetworkOperator: Boolean,
                    reason: String,
                ): String? {
                    val detection = detectActiveSimOperator(
                        context = context,
                        preferNetworkOperator = preferNetworkOperator,
                    )
                    val detectedOperator = detection.operatorCode
                    val logKey = listOf(
                        reason,
                        detectedOperator.orEmpty(),
                        detection.rawValues.joinToString(separator = "|"),
                        detection.isTMobile.toString(),
                    ).joinToString(separator = ":")
                    if (
                        detectedOperator != null &&
                        detectedOperator != viewModel.uiState.settings.operatorCode
                    ) {
                        resolverScanOperator = ""
                        operatorDisplayLabel = operatorLabel(detectedOperator)
                        addVisibleLog("Оператор SIM определен: ${operatorLabel(detectedOperator)}")
                        viewModel.updateOperatorCode(detectedOperator)
                    } else if (logKey != lastSimDetectionLogKey) {
                        when {
                            detectedOperator != null -> {
                                operatorDisplayLabel = operatorLabel(detectedOperator)
                                addVisibleLog("Оператор SIM: ${operatorLabel(detectedOperator)}")
                            }
                            detection.isTMobile -> {
                                operatorDisplayLabel = "T-Mobile / уточнение по mobile"
                                addVisibleLog("SIM T-Mobile: оператор будет уточнен по мобильной сети")
                            }
                            detection.rawValues.isNotEmpty() -> addVisibleLog("SIM не распознана: ${detection.rawValues.joinToString()}")
                        }
                    }
                    lastSimDetectionLogKey = logKey
                    return detectedOperator
                }

                LaunchedEffect(inboundProfileLink) {
                    if (inboundProfileLink.isNotBlank()) {
                        viewModel.updateSubscriptionLink(inboundProfileLink)
                            .onSuccess {
                                subscriptionLink = inboundProfileLink
                                errorMessage = null
                            }
                            .onFailure { error ->
                                errorMessage = error.message ?: "Не удалось импортировать профиль"
                            }
                    }
                }

                LaunchedEffect(Unit) {
                    refreshDetectedOperator(
                        preferNetworkOperator = activeBaseNetworkTransport == NetworkTransportMobile || !wifiEnabled,
                        reason = "startup",
                    )
                }

                LaunchedEffect(Unit) {
                    networkMonitor.observeWifiState { state ->
                        wifiEnabled = state.networkAvailable
                        wifiRadioEnabled = state.radioEnabled
                        if (state.radioEnabled) {
                            if (pendingStormDnsAfterWifiOff) {
                                userStatus = "Выключите Wi-Fi"
                                errorMessage = "Выключите Wi-Fi"
                            }
                            addVisibleLog("Wi-Fi включен")
                        } else {
                            addVisibleLog("Wi-Fi выключен")
                        }
                    }
                }

                LaunchedEffect(Unit) {
                    networkMonitor.observeBaseNetworkTransport { transport ->
                        activeBaseNetworkTransport = transport
                        addVisibleLog("Активная сеть: ${networkTransportLabel(transport)}")
                    }
                }

                LaunchedEffect(activeBaseNetworkTransport, wifiEnabled) {
                    if (!wifiEnabled || activeBaseNetworkTransport == NetworkTransportMobile) {
                        refreshDetectedOperator(
                            preferNetworkOperator = true,
                            reason = "mobile-network",
                        )
                    }
                }

                LaunchedEffect(
                    viewModel.uiState.settings.operatorCode,
                    viewModel.uiState.settings.forceDnsTunnel,
                    viewModel.uiState.settings.manualMode,
                    viewModel.uiState.settings.transportMode,
                    wifiEnabled,
                    activeBaseNetworkTransport,
                    resolverScanKick,
                ) {
                    val manualXrayOnly = viewModel.uiState.settings.manualMode &&
                        viewModel.uiState.settings.transportMode == WhiteZiaOptions.TransportXray
                    if (manualXrayOnly) {
                        resolverScanOperator = ""
                        return@LaunchedEffect
                    }
                    val detectedOperator = if (!wifiEnabled || activeBaseNetworkTransport == NetworkTransportMobile) {
                        refreshDetectedOperator(
                            preferNetworkOperator = true,
                            reason = "before-resolver-scan",
                        )
                    } else {
                        null
                    }
                    val operatorCode = detectedOperator ?: viewModel.uiState.settings.operatorCode
                    if (isStormDnsBlockedByWifi()) {
                        resolverScanOperator = ""
                        if (pendingStormDnsAfterWifiOff || viewModel.uiState.settings.forceDnsTunnel) {
                            setVisibleLog("Выключите Wi-Fi")
                            userStatus = "Выключите Wi-Fi"
                            errorMessage = "Выключите Wi-Fi"
                        } else {
                            setVisibleLog("Wi-Fi подключен")
                            if (viewModel.uiState.connectionStatus == ConnectionStatus.DISCONNECTED) {
                                userStatus = "Готово к подключению"
                            }
                            errorMessage = null
                        }
                        return@LaunchedEffect
                    }
                    if (!pendingStormDnsAfterResolverScan) {
                        if (viewModel.uiState.connectionStatus == ConnectionStatus.DISCONNECTED) {
                            userStatus = "Готово к подключению"
                            errorMessage = null
                        }
                        return@LaunchedEffect
                    }
                    if (
                        resolverScanOperator == operatorCode &&
                        viewModel.uiState.settings.resolverText.isNotBlank() &&
                        (!viewModel.isYandexResolverSet() || resolverFallbackYandexAllowed)
                    ) {
                        return@LaunchedEffect
                    }
                    resolverScanOperator = operatorCode
                    viewModel.resetConnectionLog("Поиск DNS для выбранного оператора")
                    setVisibleLog("Выбран оператор: ${operatorLabel(operatorCode)}")
                    userStatus = "производится первичная настройка"
                    val hasCachedResolvers = viewModel.applyCachedResolversForOperator(operatorCode, addVisibleLog)
                    addVisibleLog(
                        if (hasCachedResolvers) {
                            "Cache resolver'ов применен"
                        } else {
                            "Ищу DNS resolver'ы до подключения"
                        },
                    )
                    if (hasCachedResolvers) {
                        resolverSetupFromCache = !viewModel.usingCustomResolvers()
                        resolverFallbackYandexAllowed = false
                        errorMessage = null
                        if (pendingStormDnsAfterResolverScan) {
                            userStatus = "Подключение"
                            addVisibleLog("Cache resolver'ов готов, продолжаю DNS fallback")
                            resolverFallbackConnectKick += 1
                        } else {
                            userStatus = "Готово к подключению"
                        }
                        return@LaunchedEffect
                    }
                    val discoveryError = viewModel.discoverAndApplyDnsResolvers(addVisibleLog)
                    if (discoveryError != null) {
                        resolverSetupFromCache = false
                        resolverFallbackYandexAllowed = false
                        if (!hasCachedResolvers) {
                            errorMessage = discoveryError
                            userStatus = "Не удалось выполнить первичную настройку"
                        }
                        addVisibleLog(discoveryError)
                    } else {
                        resolverSetupFromCache = false
                        resolverFallbackYandexAllowed = viewModel.isYandexResolverSet()
                        errorMessage = null
                        if (pendingStormDnsAfterResolverScan) {
                            userStatus = "Подключение"
                            addVisibleLog("Resolver'ы готовы, продолжаю DNS fallback")
                            resolverFallbackConnectKick += 1
                        } else {
                            userStatus = "Готово к подключению"
                            addVisibleLog(
                                if (hasCachedResolvers) {
                                    "Cache resolver'ов обновлен"
                                } else {
                                    "Resolver'ы готовы, можно нажать Connect"
                                },
                            )
                        }
                    }
                }

                LaunchedEffect(
                    viewModel.uiState.settings.manualMode,
                    viewModel.uiState.settings.transportMode,
                    wifiEnabled,
                    activeBaseNetworkTransport,
                    viewModel.uiState.connectionStatus,
                ) {
                    val manualXrayOnly = viewModel.uiState.settings.manualMode &&
                        viewModel.uiState.settings.transportMode == WhiteZiaOptions.TransportXray
                    val networkWaitMessages = setOf("Выключите Wi-Fi", "Включите мобильный интернет")
                    if (
                        !manualXrayOnly ||
                        viewModel.uiState.connectionStatus != ConnectionStatus.DISCONNECTED ||
                        connectionLaunchStarted ||
                        pendingXrayAfterWifiOff ||
                        pendingXrayFallbackAfterAmnezia
                    ) {
                        return@LaunchedEffect
                    }
                    if (isXrayBlockedByNetwork()) {
                        val waitMessage = xrayNetworkWaitMessage()
                        if (errorMessage == null || errorMessage in networkWaitMessages) {
                            if (errorMessage != waitMessage) {
                                addVisibleLog("Ручной режим Xray: $waitMessage")
                            }
                            errorMessage = waitMessage
                            userStatus = waitMessage
                        }
                    } else if (errorMessage in networkWaitMessages) {
                        errorMessage = null
                        userStatus = "Готово к подключению"
                        addVisibleLog("Мобильная сеть готова для Xray")
                    }
                }

                fun runPermissionAction(action: String) {
                    when (action) {
                        PermissionActionConnectNow -> {
                            if (viewModel.beginConnection()) {
                                connectionLaunchStarted = true
                            } else {
                                addVisibleLog("Запуск VPN уже выполняется")
                            }
                        }
                    }
                }

                val vpnPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult(),
                ) { result ->
                    val action = pendingActionAfterVpnPermission
                    pendingActionAfterVpnPermission = PermissionActionNone
                    if (result.resultCode == Activity.RESULT_OK && action != PermissionActionNone) {
                        addVisibleLog("VPN разрешение получено")
                        runPermissionAction(action)
                    } else if (action != PermissionActionNone) {
                        clearPendingConnectionFlow()
                        userStatus = "Ошибка подключения. Повторите попытку"
                        errorMessage = "VPN permission is required"
                        addVisibleLog("Ошибка: VPN разрешение не выдано")
                    }
                }

                val requestVpnPermission: (String) -> Unit = { action ->
                    val permissionIntent = VpnService.prepare(context)
                    if (permissionIntent == null) {
                        addVisibleLog("VPN разрешение уже есть")
                        runPermissionAction(action)
                    } else {
                        addVisibleLog("Запрашиваю VPN разрешение заранее")
                        pendingActionAfterVpnPermission = action
                        vpnPermissionLauncher.launch(permissionIntent)
                    }
                }

                val requestPermissionsThen: (String) -> Unit = { action ->
                    // Notification visibility must never gate the foreground VPN service.
                    requestVpnPermission(action)
                }

                fun restartForResolverBenchmark() {
                    resolverBenchmarkReconnectJob?.cancel()
                    networkReconnectJob?.cancel()
                    resolverBenchmarkReconnectJob = lifecycleScope.launch {
                        viewModel.disconnect()
                        when (
                            transportRestartCoordinator.awaitReady(
                                awaitRuntimeStop = viewModel::awaitRuntimeStopCompletion,
                                settleDelayMillis = ResolverBenchmarkReconnectDelayMillis,
                                shouldContinue = {
                                    connectionWanted &&
                                        viewModel.uiState.connectionStatus == ConnectionStatus.DISCONNECTED
                                },
                            )
                        ) {
                            TransportRestartResult.Ready -> {
                                addVisibleLog("Предыдущий VPN туннель полностью остановлен")
                                postCheckAttempt += 1
                                connectionLaunchStarted = viewModel.beginConnection()
                            }
                            TransportRestartResult.RuntimeStopTimedOut -> {
                                failRuntimeStopTransition()
                                return@launch
                            }
                            TransportRestartResult.Cancelled -> Unit
                        }
                    }
                }

                val beginStormDnsFallbackConnection = {
                    connectionWanted = true
                    disconnectingByUser = false
                    pendingNetworkReconnectTransport = ""
                    val activeOperatorCode = refreshDetectedOperator(
                        preferNetworkOperator = true,
                        reason = "stormdns-fallback",
                    ) ?: viewModel.uiState.settings.operatorCode
                    errorMessage = null
                    resolverBenchmarkReconnectJob?.cancel()
                    networkReconnectJob?.cancel()
                    viewModel.resetConnectionLog("Fallback подключение через DNS канал")
                    postCheckAttempt += 1
                    resolverBenchmarkPhase = ResolverBenchmarkPhase.Idle
                    resolverBenchmarkLocalText = ""
                    resolverBenchmarkLocalSpeed = 0L
                    resolverBenchmarkLocalScore = null
                    connectionLaunchStarted = false
                    pendingStormDnsAfterResolverScan = false
                    setVisibleLog("DNS fallback")
                    userStatus = "Подготовка DNS подключения"
                    val trimmedLink = subscriptionLink.trim()
                    addVisibleLog("Проверяю подписку")
                    if (isStormDnsBlockedByWifi()) {
                        pendingStormDnsAfterWifiOff = true
                        errorMessage = "Выключите Wi-Fi"
                        userStatus = "Выключите Wi-Fi"
                        addVisibleLog(errorMessage.orEmpty())
                    } else {
                        val customResolversEnabled = viewModel.uiState.settings.customResolversEnabled
                        val currentResolversAreYandex = viewModel.isYandexResolverSet()
                        var resolverSetupReady =
                            viewModel.uiState.settings.resolverText.isNotBlank() &&
                                (
                                    customResolversEnabled ||
                                        (
                                            resolverScanOperator == activeOperatorCode &&
                                                (!currentResolversAreYandex || resolverFallbackYandexAllowed)
                                            )
                                    )
                        if (!resolverSetupReady) {
                            val hasCachedResolvers = viewModel.applyCachedResolversForOperator(
                                operatorCode = activeOperatorCode,
                                onLog = addVisibleLog,
                            )
                            if (hasCachedResolvers) {
                                resolverSetupFromCache = !viewModel.usingCustomResolvers()
                                resolverScanOperator = activeOperatorCode
                            }
                            resolverSetupReady =
                                viewModel.uiState.settings.resolverText.isNotBlank() &&
                                    (
                                        customResolversEnabled ||
                                            (
                                                resolverScanOperator == activeOperatorCode &&
                                                    (!viewModel.isYandexResolverSet() || resolverFallbackYandexAllowed)
                                                )
                                        )
                        }
                        if (!resolverSetupReady) {
                            resolverSetupFromCache = false
                            pendingStormDnsAfterResolverScan = true
                            resolverScanOperator = ""
                            resolverScanKick += 1
                            errorMessage = null
                            userStatus = "производится первичная настройка"
                            addVisibleLog("Ищу DNS resolver'ы перед DNS fallback")
                        }
                    }
                    val resolverReadyForFallback =
                        viewModel.uiState.settings.resolverText.isNotBlank() &&
                            (
                                viewModel.uiState.settings.customResolversEnabled ||
                                    (
                                        resolverScanOperator == activeOperatorCode &&
                                            (!viewModel.isYandexResolverSet() || resolverFallbackYandexAllowed)
                                        )
                                )
                    if (
                        !isStormDnsBlockedByWifi() &&
                        !resolverReadyForFallback
                    ) {
                        resolverSetupFromCache = false
                        pendingStormDnsAfterResolverScan = true
                        resolverScanOperator = ""
                        resolverScanKick += 1
                        errorMessage = null
                        userStatus = "производится первичная настройка"
                        addVisibleLog("Ищу DNS resolver'ы перед DNS fallback")
                    } else if (!isStormDnsBlockedByWifi()) {
                        val simCheck = checkSelectedOperatorAgainstActiveSim(
                            context = context,
                            selectedOperatorCode = activeOperatorCode,
                        )
                        if (simCheck.isMismatch) {
                            clearPendingConnectionFlow()
                            errorMessage = simCheck.message
                            userStatus = "Ошибка подключения. Повторите попытку"
                            addVisibleLog(simCheck.message)
                        } else {
                            addVisibleLog(simCheck.message)
                            val preparationError = viewModel.prepareSubscriptionConnection(
                                rawLink = trimmedLink,
                                operatorCode = activeOperatorCode,
                                transportMode = WhiteZiaOptions.TransportDns,
                            )
                            if (preparationError != null) {
                                clearPendingConnectionFlow()
                                errorMessage = preparationError
                                userStatus = "Ошибка подключения. Повторите попытку"
                                addVisibleLog(preparationError)
                            } else {
                                pendingStormDnsAfterWifiOff = false
                                pendingStormDnsAfterResolverScan = false
                                viewModel.applyCachedResolverBenchmarkWinner(addVisibleLog)
                                userStatus = "Подключение"
                                addVisibleLog("Подписка принята, настройки применены")
                                addVisibleLog("Проверяю разрешения перед запуском VPN")
                                requestPermissionsThen(PermissionActionConnectNow)
                            }
                        }
                    }
                }

                val beginXrayFallbackConnection: (Boolean) -> Unit = beginXrayFallbackConnection@{ allowDnsFallback ->
                    val activeOperatorCode = refreshDetectedOperator(
                        preferNetworkOperator = activeBaseNetworkTransport == NetworkTransportMobile || !wifiEnabled,
                        reason = "xray-fallback",
                    ) ?: viewModel.uiState.settings.operatorCode
                    connectionWanted = true
                    disconnectingByUser = false
                    errorMessage = null
                    pendingStormDnsAfterWifiOff = false
                    pendingStormDnsAfterResolverScan = false
                    pendingAmneziaFallback = false
                    pendingXrayFallbackAfterAmnezia = false
                    pendingXrayAfterWifiOff = false
                    pendingDnsFallbackAfterAmnezia = false
                    pendingDnsFallbackAfterXray = false
                    allowDnsFallbackAfterXray = allowDnsFallback
                    resolverFallbackYandexAllowed = false
                    resolverSetupFromCache = false
                    resolverBenchmarkReconnectJob?.cancel()
                    networkReconnectJob?.cancel()
                    pendingNetworkReconnectTransport = ""
                    viewModel.resetConnectionLog("Fallback подключение через Xray")
                    setVisibleLog("Xray fallback")
                    userStatus = "Подключение через Xray"
                    val preparationError = viewModel.prepareSubscriptionConnection(
                        rawLink = subscriptionLink.trim(),
                        operatorCode = activeOperatorCode,
                        transportMode = WhiteZiaOptions.TransportXray,
                    )
                    if (preparationError != null) {
                        if (allowDnsFallback) {
                            allowDnsFallbackAfterXray = false
                            pendingDnsFallbackAfterXray = true
                            errorMessage = null
                            userStatus = "Подготовка DNS подключения"
                            addVisibleLog("Xray не удалось подготовить, переключаюсь на DNS канал")
                        } else {
                            clearPendingConnectionFlow()
                            errorMessage = preparationError
                            userStatus = "Ошибка подключения. Повторите попытку"
                        }
                        addVisibleLog(preparationError)
                    } else {
                        when (
                            FallbackPlanner.planXrayFallback(
                                hasXray = viewModel.uiState.settings.xrayUri.isNotBlank(),
                                network = currentFallbackNetworkState(),
                                allowDnsFallback = allowDnsFallback,
                            )
                        ) {
                            FallbackPlanAction.StartXray -> {
                                postCheckAttempt += 1
                                addVisibleLog("Проверяю разрешения перед запуском Xray")
                                requestPermissionsThen(PermissionActionConnectNow)
                            }
                            FallbackPlanAction.WaitForMobileForXray -> {
                                markXrayNetworkWait("Для Xray fallback нужна мобильная сеть без Wi-Fi")
                            }
                            FallbackPlanAction.StartDns -> {
                                allowDnsFallbackAfterXray = false
                                addVisibleLog("В подписке нет Xray")
                                pendingDnsFallbackAfterXray = true
                                userStatus = "Подготовка DNS подключения"
                            }
                            FallbackPlanAction.WaitForWifiOffForDns -> {
                                allowDnsFallbackAfterXray = false
                                addVisibleLog("В подписке нет Xray, DNS ждет отключения Wi-Fi")
                                pendingStormDnsAfterWifiOff = true
                                errorMessage = "Выключите Wi-Fi"
                                userStatus = "Выключите Wi-Fi"
                            }
                            FallbackPlanAction.FailNoXray -> {
                                clearPendingConnectionFlow()
                                addVisibleLog("В подписке нет Xray")
                                errorMessage = "В подписке нет Xray"
                                userStatus = "Ошибка подключения. Повторите попытку"
                            }
                        }
                    }
                }

                val beginPreparedConnection = beginPreparedConnection@{
                    val activeOperatorCode = refreshDetectedOperator(
                        preferNetworkOperator = activeBaseNetworkTransport == NetworkTransportMobile || !wifiEnabled,
                        reason = "connect",
                    ) ?: viewModel.uiState.settings.operatorCode
                    val xrayOnlyMode = viewModel.uiState.settings.manualMode &&
                        viewModel.uiState.settings.transportMode == WhiteZiaOptions.TransportXray
                    connectionWanted = true
                    disconnectingByUser = false
                    errorMessage = null
                    pendingStormDnsAfterWifiOff = false
                    pendingStormDnsAfterResolverScan = false
                    pendingAmneziaFallback = false
                    pendingXrayFallbackAfterAmnezia = false
                    pendingXrayAfterWifiOff = false
                    pendingDnsFallbackAfterAmnezia = false
                    pendingDnsFallbackAfterXray = false
                    allowDnsFallbackAfterXray = false
                    resolverFallbackYandexAllowed = false
                    resolverSetupFromCache = false
                    resolverBenchmarkReconnectJob?.cancel()
                    networkReconnectJob?.cancel()
                    pendingNetworkReconnectTransport = ""
                    viewModel.resetConnectionLog("Новая попытка подключения")
                    setVisibleLog("Connect нажата")
                    val trimmedLink = subscriptionLink.trim()
                    if (xrayOnlyMode) {
                        if (isXrayBlockedByNetwork()) {
                            markXrayNetworkWait("Ручной режим Xray: нужна мобильная сеть без Wi-Fi")
                            return@beginPreparedConnection
                        }
                        userStatus = "Подключение через Xray"
                        addVisibleLog("Ручной режим Xray: запускаю только Xray")
                        allowDnsFallbackAfterXray = false
                        val preparationError = viewModel.prepareSubscriptionConnection(
                            rawLink = trimmedLink,
                            operatorCode = activeOperatorCode,
                            transportMode = WhiteZiaOptions.TransportXray,
                        )
                        if (preparationError == null && viewModel.uiState.settings.xrayUri.isBlank()) {
                            clearPendingConnectionFlow()
                            errorMessage = "В подписке нет Xray"
                            userStatus = "Ошибка подключения. Повторите попытку"
                            addVisibleLog("Ручной режим Xray: Xray ссылка не импортирована")
                            return@beginPreparedConnection
                        }
                        if (preparationError != null) {
                            clearPendingConnectionFlow()
                            errorMessage = preparationError
                            userStatus = "Ошибка подключения. Повторите попытку"
                            addVisibleLog(preparationError)
                        } else {
                            postCheckAttempt += 1
                            userStatus = "Подключение через Xray"
                            requestPermissionsThen(PermissionActionConnectNow)
                        }
                        return@beginPreparedConnection
                    }
                    if (viewModel.uiState.settings.manualMode && viewModel.uiState.settings.forceDnsTunnel) {
                        addVisibleLog("Включен принудительный DNS канал")
                        userStatus = "Подготовка DNS подключения"
                        beginStormDnsFallbackConnection()
                        return@beginPreparedConnection
                    }
                    userStatus = "Подключение через AmneziaWG"
                    addVisibleLog("Пробую основной канал AmneziaWG")
                    val preparationError = viewModel.prepareSubscriptionConnection(
                        rawLink = trimmedLink,
                        operatorCode = activeOperatorCode,
                        transportMode = WhiteZiaOptions.TransportAuto,
                    )
                    if (preparationError != null) {
                        clearPendingConnectionFlow()
                        errorMessage = preparationError
                        userStatus = "Ошибка подключения. Повторите попытку"
                        addVisibleLog(preparationError)
                    } else if (viewModel.uiState.settings.amneziaWgConfig.isBlank()) {
                        val fallbackAction = FallbackPlanner.planAfterAmneziaUnavailable(
                            hasXray = viewModel.uiState.settings.xrayUri.isNotBlank(),
                            network = currentFallbackNetworkState(),
                        )
                        when (fallbackAction) {
                            FallbackPlanAction.StartXray -> {
                                addVisibleLog("В подписке нет AmneziaWG, запускаю Xray")
                                beginXrayFallbackConnection(true)
                            }
                            FallbackPlanAction.WaitForMobileForXray -> {
                                addVisibleLog("В подписке нет AmneziaWG, Xray ждет мобильную сеть")
                                allowDnsFallbackAfterXray = true
                                markXrayNetworkWait("Для Xray нужна мобильная сеть без Wi-Fi")
                            }
                            FallbackPlanAction.StartDns -> {
                                addVisibleLog("В подписке нет AmneziaWG и Xray, запускаю DNS канал")
                                beginStormDnsFallbackConnection()
                            }
                            FallbackPlanAction.WaitForWifiOffForDns -> {
                                addVisibleLog("В подписке нет AmneziaWG и Xray, DNS ждет отключения Wi-Fi")
                                pendingStormDnsAfterWifiOff = true
                                errorMessage = "Выключите Wi-Fi"
                                userStatus = "Выключите Wi-Fi"
                            }
                            FallbackPlanAction.FailNoXray -> Unit
                        }
                    } else {
                        pendingAmneziaFallback = true
                        postCheckAttempt += 1
                        userStatus = "Подключение через AmneziaWG"
                        requestPermissionsThen(PermissionActionConnectNow)
                    }
                }

                val beginConnectionWithProfileRefresh = {
                    if (profileRefreshJob?.isActive == true) {
                        addVisibleLog("Проверка конфигурации уже выполняется")
                    } else {
                        connectionWanted = true
                        disconnectingByUser = false
                        errorMessage = null
                        userStatus = "Проверяю конфигурацию"
                        profileRefreshJob = lifecycleScope.launch {
                            var latestBundle: String? = null
                            try {
                                latestBundle = accountViewModel.refreshManagedProfileBeforeConnection()
                            } catch (_: CancellationException) {
                                return@launch
                            } catch (_: Exception) {
                                // Cached settings remain a valid fallback when both Core routes fail.
                            } finally {
                                profileRefreshJob = null
                            }
                            if (!connectionWanted) return@launch
                            if (!latestBundle.isNullOrBlank()) {
                                viewModel.updateSubscriptionLink(latestBundle)
                                    .onSuccess {
                                        subscriptionLink = latestBundle
                                        accountViewModel.profileBundleApplied(latestBundle)
                                    }
                                    .onFailure {
                                        addVisibleLog("Получена некорректная конфигурация, использую сохраненную")
                                    }
                            }
                            if (connectionWanted) {
                                beginPreparedConnection()
                            }
                        }
                    }
                }

                fun restartForNetworkSwitch(nextTransport: String) {
                    if (networkReconnectJob?.isActive == true) {
                        return
                    }
                    connectionWanted = true
                    disconnectingByUser = false
                    resolverBenchmarkReconnectJob?.cancel()
                    networkReconnectJob?.cancel()
                    networkReconnectJob = lifecycleScope.launch {
                        pendingStormDnsAfterWifiOff = false
                        pendingStormDnsAfterResolverScan = false
                        pendingAmneziaFallback = false
                        pendingXrayFallbackAfterAmnezia = false
                        pendingXrayAfterWifiOff = false
                        pendingDnsFallbackAfterAmnezia = false
                        pendingDnsFallbackAfterXray = false
                        allowDnsFallbackAfterXray = false
                        resolverFallbackYandexAllowed = false
                        resolverSetupFromCache = false
                        connectionLaunchStarted = false
                        resolverBenchmarkPhase = ResolverBenchmarkPhase.Idle
                        resolverBenchmarkLocalText = ""
                        resolverBenchmarkLocalSpeed = 0L
                        resolverBenchmarkLocalScore = null
                        errorMessage = null
                        userStatus = "Сеть изменилась, переподключаюсь"
                        addVisibleLog("Смена сети: ${networkTransportLabel(nextTransport)}, перезапуск VPN")
                        viewModel.disconnect()
                        when (
                            transportRestartCoordinator.awaitReady(
                                awaitRuntimeStop = viewModel::awaitRuntimeStopCompletion,
                                settleDelayMillis = NetworkSwitchReconnectDelayMillis,
                                shouldContinue = {
                                    connectionWanted &&
                                        networkMonitor.currentBaseNetworkTransport() != NetworkTransportNone &&
                                        viewModel.uiState.connectionStatus == ConnectionStatus.DISCONNECTED
                                },
                            )
                        ) {
                            TransportRestartResult.Ready -> {
                                networkReconnectJob = null
                                beginPreparedConnection()
                            }
                            TransportRestartResult.RuntimeStopTimedOut -> {
                                networkReconnectJob = null
                                failRuntimeStopTransition()
                                return@launch
                            }
                            TransportRestartResult.Cancelled -> {
                                networkReconnectJob = null
                            }
                        }
                    }
                }

                LaunchedEffect(activeBaseNetworkTransport, viewModel.uiState.connectionStatus) {
                    val currentTransport = activeBaseNetworkTransport
                    val previousTransport = lastNetworkReconnectTransport
                    val transportChanged = currentTransport != previousTransport
                    val meaningfulSwitch =
                        currentTransport != NetworkTransportNone &&
                            previousTransport != NetworkTransportNone &&
                            (
                                currentTransport == NetworkTransportWifi ||
                                    currentTransport == NetworkTransportMobile ||
                                    previousTransport == NetworkTransportWifi ||
                                    previousTransport == NetworkTransportMobile
                                )
                    if (transportChanged && currentTransport != NetworkTransportNone) {
                        lastNetworkReconnectTransport = currentTransport
                    }
                    if (
                        transportChanged &&
                        meaningfulSwitch &&
                        connectionWanted &&
                        !hasPendingAutomaticTransition()
                    ) {
                        pendingNetworkReconnectTransport = currentTransport
                    } else if (
                        !connectionWanted ||
                        previousTransport == NetworkTransportNone ||
                        hasPendingAutomaticTransition()
                    ) {
                        pendingNetworkReconnectTransport = ""
                        lastNetworkReconnectTransport = currentTransport
                    }
                }

                LaunchedEffect(
                    pendingNetworkReconnectTransport,
                    viewModel.uiState.connectionStatus,
                    connectionWanted,
                ) {
                    val targetTransport = pendingNetworkReconnectTransport
                    if (
                        targetTransport.isBlank() ||
                        !connectionWanted ||
                        viewModel.uiState.connectionStatus == ConnectionStatus.CONNECTING ||
                        networkMonitor.currentBaseNetworkTransport() == NetworkTransportNone ||
                        networkReconnectJob?.isActive == true
                    ) {
                        return@LaunchedEffect
                    }
                    pendingNetworkReconnectTransport = ""
                    restartForNetworkSwitch(targetTransport)
                }

                LaunchedEffect(
                    pendingStormDnsAfterWifiOff,
                    wifiEnabled,
                    viewModel.uiState.settings.resolverText,
                    subscriptionLink,
                    connectionWanted,
                ) {
                    if (
                        !connectionWanted ||
                        !pendingStormDnsAfterWifiOff ||
                        isStormDnsBlockedByWifi()
                    ) {
                        return@LaunchedEffect
                    }
                    if (
                        viewModel.uiState.settings.resolverText.isBlank() ||
                        (viewModel.isYandexResolverSet() && !resolverFallbackYandexAllowed)
                    ) {
                        pendingStormDnsAfterResolverScan = true
                        userStatus = "производится первичная настройка"
                        errorMessage = null
                        resolverScanOperator = ""
                        resolverScanKick += 1
                        return@LaunchedEffect
                    }
                    pendingStormDnsAfterWifiOff = false
                    beginStormDnsFallbackConnection()
                }

                LaunchedEffect(
                    resolverFallbackConnectKick,
                    activeBaseNetworkTransport,
                    connectionWanted,
                ) {
                    if (
                        !connectionWanted ||
                        resolverFallbackConnectKick == 0 ||
                        !pendingStormDnsAfterResolverScan ||
                        isStormDnsBlockedByWifi()
                    ) {
                        return@LaunchedEffect
                    }
                    pendingStormDnsAfterResolverScan = false
                    beginStormDnsFallbackConnection()
                }

                LaunchedEffect(
                    pendingXrayAfterWifiOff,
                    wifiEnabled,
                    activeBaseNetworkTransport,
                    connectionWanted,
                ) {
                    if (!connectionWanted || !pendingXrayAfterWifiOff || isXrayBlockedByNetwork()) {
                        return@LaunchedEffect
                    }
                    pendingXrayAfterWifiOff = false
                    beginXrayFallbackConnection(allowDnsFallbackAfterXray)
                }

                LaunchedEffect(
                    pendingXrayFallbackAfterAmnezia,
                    viewModel.uiState.connectionStatus,
                ) {
                    if (
                        !pendingXrayFallbackAfterAmnezia ||
                        viewModel.uiState.connectionStatus != ConnectionStatus.DISCONNECTED
                    ) {
                        return@LaunchedEffect
                    }
                    userStatus = "Подключение через Xray"
                    errorMessage = null
                    when (
                        transportRestartCoordinator.awaitReady(
                            awaitRuntimeStop = viewModel::awaitRuntimeStopCompletion,
                            settleDelayMillis = FallbackTransportRestartDelayMillis,
                            shouldContinue = {
                                pendingXrayFallbackAfterAmnezia &&
                                    viewModel.uiState.connectionStatus == ConnectionStatus.DISCONNECTED &&
                                    connectionWanted
                            },
                        )
                    ) {
                        TransportRestartResult.Ready -> {
                            pendingXrayFallbackAfterAmnezia = false
                            addVisibleLog("AWG туннель закрыт, запускаю Xray")
                            beginXrayFallbackConnection(true)
                        }
                        TransportRestartResult.RuntimeStopTimedOut -> failRuntimeStopTransition()
                        TransportRestartResult.Cancelled -> Unit
                    }
                }

                LaunchedEffect(
                    pendingDnsFallbackAfterAmnezia,
                    pendingDnsFallbackAfterXray,
                    viewModel.uiState.connectionStatus,
                    wifiEnabled,
                    connectionWanted,
                ) {
                    if (
                        !connectionWanted ||
                        (!pendingDnsFallbackAfterAmnezia && !pendingDnsFallbackAfterXray) ||
                        viewModel.uiState.connectionStatus != ConnectionStatus.DISCONNECTED
                    ) {
                        return@LaunchedEffect
                    }
                    userStatus = "Подготовка DNS подключения"
                    errorMessage = null
                    when (
                        transportRestartCoordinator.awaitReady(
                            awaitRuntimeStop = viewModel::awaitRuntimeStopCompletion,
                            settleDelayMillis = FallbackTransportRestartDelayMillis,
                            shouldContinue = {
                                connectionWanted &&
                                    (pendingDnsFallbackAfterAmnezia || pendingDnsFallbackAfterXray) &&
                                    viewModel.uiState.connectionStatus == ConnectionStatus.DISCONNECTED
                            },
                        )
                    ) {
                        TransportRestartResult.Ready -> {
                            if (isStormDnsBlockedByWifi()) {
                                pendingDnsFallbackAfterAmnezia = false
                                pendingDnsFallbackAfterXray = false
                                allowDnsFallbackAfterXray = false
                                pendingStormDnsAfterWifiOff = true
                                userStatus = "Выключите Wi-Fi"
                                errorMessage = "Выключите Wi-Fi"
                                addVisibleLog("Для StormDNS отключите Wi-Fi")
                            } else {
                                pendingDnsFallbackAfterAmnezia = false
                                pendingDnsFallbackAfterXray = false
                                allowDnsFallbackAfterXray = false
                                beginStormDnsFallbackConnection()
                            }
                        }
                        TransportRestartResult.RuntimeStopTimedOut -> failRuntimeStopTransition()
                        TransportRestartResult.Cancelled -> Unit
                    }
                }

                LaunchedEffect(
                    viewModel.uiState.connectionStatus,
                    disconnectingByUser,
                ) {
                    if (
                        disconnectingByUser &&
                        viewModel.uiState.connectionStatus == ConnectionStatus.DISCONNECTED
                    ) {
                        disconnectingByUser = false
                    }
                }

                LaunchedEffect(viewModel.uiState.connectionStatus, postCheckAttempt) {
                    if (viewModel.uiState.connectionStatus == ConnectionStatus.CONNECTED) {
                        connectionWanted = true
                        disconnectingByUser = false
                        if (postCheckAttempt == 0) {
                            connectionLaunchStarted = false
                            errorMessage = null
                            userStatus = "Подключение успешно"
                        }
                    }
                    if (
                        viewModel.uiState.connectionStatus == ConnectionStatus.DISCONNECTED &&
                        disconnectingByUser
                    ) {
                        disconnectingByUser = false
                    }
                    if (
                        viewModel.uiState.connectionStatus == ConnectionStatus.DISCONNECTED &&
                        connectionLaunchStarted &&
                        completedPostCheckAttempt != postCheckAttempt
                    ) {
                        connectionLaunchStarted = false
                        if (pendingAmneziaFallback) {
                            pendingAmneziaFallback = false
                            when (
                                FallbackPlanner.planAfterAmneziaUnavailable(
                                    hasXray = viewModel.uiState.settings.xrayUri.isNotBlank(),
                                    network = currentFallbackNetworkState(),
                                )
                            ) {
                                FallbackPlanAction.StartXray -> {
                                    addVisibleLog("AmneziaWG недоступен, переключаюсь на Xray")
                                    pendingXrayFallbackAfterAmnezia = true
                                    userStatus = "Подключение через Xray"
                                    errorMessage = null
                                }
                                FallbackPlanAction.WaitForMobileForXray -> {
                                    addVisibleLog("AmneziaWG недоступен, Xray ждет мобильную сеть")
                                    allowDnsFallbackAfterXray = true
                                    markXrayNetworkWait("Для Xray нужна мобильная сеть без Wi-Fi")
                                }
                                FallbackPlanAction.StartDns -> {
                                    addVisibleLog("AmneziaWG недоступен, переключаюсь на DNS канал")
                                    beginStormDnsFallbackConnection()
                                }
                                FallbackPlanAction.WaitForWifiOffForDns -> {
                                    addVisibleLog("AmneziaWG недоступен, DNS ждет отключения Wi-Fi")
                                    pendingStormDnsAfterWifiOff = true
                                    userStatus = "Выключите Wi-Fi"
                                    errorMessage = "Выключите Wi-Fi"
                                }
                                FallbackPlanAction.FailNoXray -> Unit
                            }
                        } else if (
                            viewModel.uiState.settings.transportMode == WhiteZiaOptions.TransportXray &&
                            allowDnsFallbackAfterXray
                        ) {
                            allowDnsFallbackAfterXray = false
                            pendingDnsFallbackAfterXray = true
                            userStatus = "Подготовка DNS подключения"
                            errorMessage = null
                            addVisibleLog("Xray не запустился, переключаюсь на DNS канал")
                        } else {
                            connectionWanted = false
                            userStatus = "Не удалось подключиться. Повторите попытку"
                            errorMessage = "Повторите попытку"
                        }
                    }
                    if (
                        viewModel.uiState.connectionStatus == ConnectionStatus.CONNECTED &&
                        postCheckAttempt > 0 &&
                        completedPostCheckAttempt != postCheckAttempt
                    ) {
                        connectionLaunchStarted = false
                        completedPostCheckAttempt = postCheckAttempt
                        val usingXrayTransport =
                            viewModel.uiState.activeTransportMode == WhiteZiaOptions.TransportXray
                        val usingStormDnsTransport =
                            viewModel.uiState.activeTransportMode == WhiteZiaOptions.TransportDns ||
                                (!usingXrayTransport && viewModel.uiState.settings.amneziaWgConfig.isBlank())
                        userStatus = if (usingStormDnsTransport) {
                            "Проверка подключения"
                        } else if (usingXrayTransport) {
                            "Проверка Xray"
                        } else {
                            "Проверка AmneziaWG"
                        }
                        val ok = when {
                            usingStormDnsTransport -> viewModel.runDnsPostConnectionCheck(addVisibleLog)
                            usingXrayTransport -> viewModel.runXrayPostConnectionCheck(addVisibleLog)
                            else -> viewModel.runAmneziaPostConnectionCheck(addVisibleLog)
                        }
                        if (!ok && !usingStormDnsTransport && !usingXrayTransport) {
                            pendingAmneziaFallback = false
                            when (
                                FallbackPlanner.planAfterHealthCheckFailure(
                                    failedTransport = FallbackTransport.AmneziaWg,
                                    hasXray = viewModel.uiState.settings.xrayUri.isNotBlank(),
                                    allowDnsFallback = true,
                                )
                            ) {
                                HealthCheckFallbackAction.StartXray -> {
                                    pendingXrayFallbackAfterAmnezia = true
                                    addVisibleLog("AmneziaWG поднялся, но интернет не проходит. Переключаюсь на Xray")
                                    userStatus = "Подключение через Xray"
                                }
                                HealthCheckFallbackAction.StartDns -> {
                                    pendingDnsFallbackAfterAmnezia = true
                                    addVisibleLog("AmneziaWG поднялся, но интернет не проходит. Переключаюсь на DNS канал")
                                    userStatus = "Подготовка DNS подключения"
                                }
                                HealthCheckFallbackAction.Stop -> Unit
                            }
                            errorMessage = null
                            viewModel.disconnect()
                            return@LaunchedEffect
                        }
                        if (!ok && usingXrayTransport) {
                            val canFallbackToDns = allowDnsFallbackAfterXray || pendingAmneziaFallback
                            pendingAmneziaFallback = false
                            allowDnsFallbackAfterXray = false
                            when (
                                FallbackPlanner.planAfterHealthCheckFailure(
                                    failedTransport = FallbackTransport.Xray,
                                    hasXray = true,
                                    allowDnsFallback = canFallbackToDns,
                                )
                            ) {
                                HealthCheckFallbackAction.StartDns -> {
                                    pendingDnsFallbackAfterXray = true
                                    addVisibleLog("Xray поднялся, но проверка не прошла. Переключаюсь на DNS канал")
                                    userStatus = "Подготовка DNS подключения"
                                    errorMessage = null
                                    viewModel.disconnect()
                                    return@LaunchedEffect
                                }
                                HealthCheckFallbackAction.StartXray,
                                HealthCheckFallbackAction.Stop -> Unit
                            }
                        }
                        if (!ok && usingStormDnsTransport && resolverBenchmarkPhase != ResolverBenchmarkPhase.PostCheckYandex) {
                            val currentResolvers = viewModel.currentResolverEntries()
                            if (viewModel.usingCustomResolvers()) {
                                addVisibleLog("Кастомные resolver'ы не прошли Cloudflare-check; Yandex fallback отключен")
                            } else if (currentResolvers != viewModel.yandexResolverEntries()) {
                                val removedFromCache = resolverSetupFromCache &&
                                    viewModel.discardCurrentCachedResolversForOperator(
                                        operatorCode = viewModel.uiState.settings.operatorCode,
                                        onLog = addVisibleLog,
                                    )
                                if (removedFromCache) {
                                    resolverBenchmarkPhase = ResolverBenchmarkPhase.Idle
                                    resolverBenchmarkLocalText = ""
                                    resolverBenchmarkLocalSpeed = 0L
                                    resolverBenchmarkLocalScore = null
                                    resolverFallbackYandexAllowed = false
                                    resolverSetupFromCache = false
                                    pendingStormDnsAfterResolverScan = true
                                    resolverScanOperator = ""
                                    userStatus = "производится первичная настройка"
                                    errorMessage = null
                                    addVisibleLog("Cached resolver'ы недоступны, пересканирую local DNS")
                                    viewModel.disconnect()
                                    resolverScanKick += 1
                                    return@LaunchedEffect
                                }
                                resolverBenchmarkPhase = ResolverBenchmarkPhase.PostCheckYandex
                                resolverBenchmarkLocalText = currentResolvers.joinToString(separator = "\n")
                                viewModel.applyResolverEntriesForReconnect(viewModel.yandexResolverEntries())
                                userStatus = "Оптимизация подключения"
                                errorMessage = null
                                addVisibleLog("Local resolver'ы не прошли Cloudflare-check, пробую Yandex")
                                restartForResolverBenchmark()
                                return@LaunchedEffect
                            }
                        }
                        pendingAmneziaFallback = false
                        pendingXrayFallbackAfterAmnezia = false
                        pendingDnsFallbackAfterXray = false
                        allowDnsFallbackAfterXray = false
                        addVisibleLog(
                            if (ok) {
                                "Успешное подключение"
                            } else {
                                "Проверка подключения не прошла. Попробуйте снова"
                            },
                        )
                        if (ok) {
                            userStatus = "Подключение успешно"
                            errorMessage = null
                        } else {
                            userStatus = "Не удалось подключиться. Повторите попытку"
                            errorMessage = "Повторите попытку"
                            connectionWanted = false
                            pendingStormDnsAfterWifiOff = false
                            pendingStormDnsAfterResolverScan = false
                            pendingAmneziaFallback = false
                            pendingXrayFallbackAfterAmnezia = false
                            pendingXrayAfterWifiOff = false
                            pendingDnsFallbackAfterAmnezia = false
                            pendingDnsFallbackAfterXray = false
                            allowDnsFallbackAfterXray = false
                            resolverFallbackYandexAllowed = false
                            resolverSetupFromCache = false
                            resolverBenchmarkPhase = ResolverBenchmarkPhase.Idle
                            resolverBenchmarkLocalScore = null
                            resolverBenchmarkReconnectJob?.cancel()
                            networkReconnectJob?.cancel()
                            viewModel.disconnect()
                        }
                        if (ok && usingStormDnsTransport) {
                            when (resolverBenchmarkPhase) {
                                ResolverBenchmarkPhase.PostCheckYandex -> {
                                    resolverBenchmarkPhase = ResolverBenchmarkPhase.Done
                                    userStatus = "Подключение успешно"
                                    addVisibleLog("Yandex resolver set прошел Cloudflare-check без закрепления в cache")
                                }
                                ResolverBenchmarkPhase.Idle -> {
                                    if (AutoResolverBenchmarkAfterConnect && viewModel.shouldRunResolverBenchmark()) {
                                        val localResolvers = viewModel.currentResolverBenchmarkLocalResolvers()
                                        viewModel.markResolverBenchmarkAttempted(localResolvers)
                                        resolverBenchmarkLocalText = localResolvers.joinToString(separator = "\n")
                                        userStatus = "Оптимизация подключения"
                                        addVisibleLog("Сравнение resolver'ов: тест local")
                                        val localScore = viewModel.measureResolverBenchmarkScore(
                                            label = "local",
                                            onLog = addVisibleLog,
                                        )
                                        resolverBenchmarkLocalScore = localScore
                                        resolverBenchmarkLocalSpeed = localScore.speedBytesPerSecond
                                        addVisibleLog(
                                            "local: ${formatMbps(resolverBenchmarkLocalSpeed)}",
                                        )
                                        resolverBenchmarkPhase = ResolverBenchmarkPhase.TestingYandex
                                        userStatus = "Оптимизация подключения"
                                        addVisibleLog("Жду 3 секунды перед переключением на Yandex resolver'ы")
                                        delay(ResolverBenchmarkSwitchSettleDelayMillis)
                                        viewModel.applyResolverEntriesForReconnect(viewModel.yandexResolverEntries())
                                        addVisibleLog("Переключаюсь на Yandex resolver'ы")
                                        restartForResolverBenchmark()
                                        return@LaunchedEffect
                                    }
                                }
                                ResolverBenchmarkPhase.TestingYandex -> {
                                    userStatus = "Оптимизация подключения"
                                    addVisibleLog("Сравнение resolver'ов: тест Yandex")
                                    val yandexScore = viewModel.measureResolverBenchmarkScore(
                                        label = "Yandex",
                                        onLog = addVisibleLog,
                                    )
                                    val yandexSpeed = yandexScore.speedBytesPerSecond
                                    addVisibleLog("Yandex: ${formatMbps(yandexSpeed)}")
                                    val localResolvers = resolverBenchmarkLocalText
                                        .lineSequence()
                                        .map(String::trim)
                                        .filter(String::isNotEmpty)
                                        .toList()
                                    val localScore = resolverBenchmarkLocalScore ?: ResolverBenchmarkScore(
                                        label = "local",
                                        speedBytesPerSecond = resolverBenchmarkLocalSpeed,
                                        speedSuccessfulSamples = if (resolverBenchmarkLocalSpeed > 0L) 1 else 0,
                                        healthSuccesses = if (resolverBenchmarkLocalSpeed > 0L) 1 else 0,
                                        resolverSuccesses = if (resolverBenchmarkLocalSpeed > 0L) 1 else 0,
                                        resolverAttempts = 1,
                                        averageResolverLatencyMillis = 0L,
                                    )
                                    val yandexWins = viewModel.shouldPreferYandexResolverScore(
                                        local = localScore,
                                        yandex = yandexScore,
                                        onLog = addVisibleLog,
                                    )
                                    if (yandexWins) {
                                        resolverBenchmarkPhase = ResolverBenchmarkPhase.ApplyingYandexWinner
                                        resolverBenchmarkLocalScore = null
                                        val yandexResolvers = viewModel.yandexResolverEntries()
                                        viewModel.cacheResolverBenchmarkWinner(
                                            localResolvers = localResolvers,
                                            winnerId = "yandex",
                                            winnerResolvers = yandexResolvers,
                                            onLog = addVisibleLog,
                                        )
                                        viewModel.applyResolverEntriesForReconnect(yandexResolvers)
                                        userStatus = "Оптимизация подключения"
                                        addVisibleLog("Выбран Yandex resolver set, переподключаюсь через 3 секунды")
                                        restartForResolverBenchmark()
                                        return@LaunchedEffect
                                    } else {
                                        resolverBenchmarkPhase = ResolverBenchmarkPhase.ApplyingLocalWinner
                                        resolverBenchmarkLocalScore = null
                                        if (
                                            viewModel.shouldCacheLocalResolverScore(
                                                local = localScore,
                                                yandex = yandexScore,
                                                onLog = addVisibleLog,
                                            )
                                        ) {
                                            viewModel.cacheResolverBenchmarkWinner(
                                                localResolvers = localResolvers,
                                                winnerId = "local",
                                                winnerResolvers = localResolvers,
                                                onLog = addVisibleLog,
                                            )
                                        }
                                        viewModel.applyResolverEntriesForReconnect(localResolvers)
                                        userStatus = "Оптимизация подключения"
                                        addVisibleLog("Выбран local resolver set, переподключаюсь через 3 секунды")
                                        restartForResolverBenchmark()
                                        return@LaunchedEffect
                                    }
                                }
                                ResolverBenchmarkPhase.ApplyingYandexWinner -> {
                                    resolverBenchmarkPhase = ResolverBenchmarkPhase.Done
                                    userStatus = "Подключение успешно"
                                    addVisibleLog("Yandex resolver set применен")
                                }
                                ResolverBenchmarkPhase.ApplyingLocalWinner -> {
                                    resolverBenchmarkPhase = ResolverBenchmarkPhase.Done
                                    userStatus = "Подключение успешно"
                                    addVisibleLog("Local resolver set применен")
                                }
                                ResolverBenchmarkPhase.Done -> Unit
                            }
                            viewModel.reportCurrentResolversToRegistry(addVisibleLog)
                        }
                    }
                }

                WhiteZiaConnectScreen(
                    subscriptionLink = subscriptionLink,
                    settings = viewModel.uiState.settings,
                    operatorDisplayLabel = operatorDisplayLabel,
                    connectionStatus = viewModel.uiState.connectionStatus,
                    wifiEnabled = wifiEnabled,
                    errorMessage = errorMessage,
                    userStatus = userStatus,
                    isDisconnecting = disconnectingByUser,
                    forceDnsTunnel = viewModel.uiState.settings.forceDnsTunnel,
                    xrayPreflightBlocked = viewModel.uiState.settings.manualMode &&
                        viewModel.uiState.settings.transportMode == WhiteZiaOptions.TransportXray &&
                        isXrayBlockedByNetwork(),
                    onConnectClick = {
                        val networkWaitMessages = setOf("Выключите Wi-Fi", "Включите мобильный интернет")
                        val retryAfterError =
                            viewModel.uiState.connectionStatus == ConnectionStatus.DISCONNECTED &&
                                errorMessage != null &&
                                errorMessage !in networkWaitMessages
                        if (retryAfterError) {
                            disconnectingByUser = false
                            connectionWanted = false
                            pendingNetworkReconnectTransport = ""
                            beginConnectionWithProfileRefresh()
                            return@WhiteZiaConnectScreen
                        }

                        val hasPendingConnectionFlow =
                            pendingStormDnsAfterWifiOff ||
                                pendingStormDnsAfterResolverScan ||
                                pendingAmneziaFallback ||
                                pendingXrayFallbackAfterAmnezia ||
                                pendingXrayAfterWifiOff ||
                                pendingDnsFallbackAfterAmnezia ||
                                pendingDnsFallbackAfterXray ||
                                profileRefreshJob?.isActive == true ||
                                resolverBenchmarkReconnectJob?.isActive == true ||
                                networkReconnectJob?.isActive == true
                        val stopRequested =
                            viewModel.uiState.connectionStatus != ConnectionStatus.DISCONNECTED ||
                                (connectionWanted && hasPendingConnectionFlow)
                        if (stopRequested && userStatus != "производится первичная настройка") {
                            connectionWanted = false
                            disconnectingByUser = true
                            pendingNetworkReconnectTransport = ""
                            resolverBenchmarkReconnectJob?.cancel()
                            networkReconnectJob?.cancel()
                            profileRefreshJob?.cancel()
                            profileRefreshJob = null
                            pendingStormDnsAfterWifiOff = false
                            pendingStormDnsAfterResolverScan = false
                            pendingAmneziaFallback = false
                            pendingXrayFallbackAfterAmnezia = false
                            pendingXrayAfterWifiOff = false
                            pendingDnsFallbackAfterAmnezia = false
                            pendingDnsFallbackAfterXray = false
                            allowDnsFallbackAfterXray = false
                            resolverFallbackYandexAllowed = false
                            resolverBenchmarkPhase = ResolverBenchmarkPhase.Idle
                            resolverBenchmarkLocalScore = null
                            errorMessage = null
                            connectionLaunchStarted = false
                            userStatus = "отключено"
                            addVisibleLog("Отключение")
                            viewModel.disconnect()
                            return@WhiteZiaConnectScreen
                        }

                        val clickLockedByAutomaticFlow = disconnectingByUser ||
                            userStatus == "производится первичная настройка"
                        if (clickLockedByAutomaticFlow) {
                            addVisibleLog("Автоматическое подключение еще выполняется")
                            return@WhiteZiaConnectScreen
                        }

                        when (viewModel.uiState.connectionStatus) {
                            ConnectionStatus.DISCONNECTED -> beginConnectionWithProfileRefresh()
                            ConnectionStatus.CONNECTING,
                            ConnectionStatus.CONNECTED -> {
                                connectionWanted = false
                                disconnectingByUser = true
                                errorMessage = null
                                connectionLaunchStarted = false
                                userStatus = "отключено"
                                addVisibleLog("Отключение")
                                viewModel.disconnect()
                            }
                        }
                    },
                    onXrayOnlyModeChange = { enabled ->
                        val updatedSettings = viewModel.uiState.settings.copy(
                            forceDnsTunnel = false,
                            transportMode = if (enabled) {
                                WhiteZiaOptions.TransportXray
                            } else {
                                WhiteZiaOptions.TransportAuto
                            },
                        )
                        viewModel.updateSettings(updatedSettings)
                        val networkWaitMessage = if (enabled && isXrayBlockedByNetwork()) {
                            xrayNetworkWaitMessage()
                        } else {
                            null
                        }
                        errorMessage = networkWaitMessage
                        userStatus = networkWaitMessage ?: "Готово к подключению"
                        addVisibleLog(
                            if (enabled) {
                                networkWaitMessage?.let { "Ручной режим Xray включен: $it" }
                                    ?: "Ручной режим Xray включен"
                            } else {
                                "Ручной режим Xray выключен"
                            },
                        )
                    },
                    onForceDnsTunnelChange = { enabled ->
                        if (enabled && viewModel.uiState.settings.transportMode == WhiteZiaOptions.TransportXray) {
                            viewModel.updateSettings(
                                viewModel.uiState.settings.copy(
                                    forceDnsTunnel = true,
                                    transportMode = WhiteZiaOptions.TransportAuto,
                                ),
                            )
                        } else {
                            viewModel.setForceDnsTunnel(enabled)
                        }
                        if (enabled) {
                            addVisibleLog("Принудительный DNS канал включен")
                            if (isStormDnsBlockedByWifi()) {
                                userStatus = "Выключите Wi-Fi"
                                errorMessage = "Выключите Wi-Fi"
                                addVisibleLog("Для DNS канала выключите Wi-Fi")
                            } else if (viewModel.uiState.connectionStatus == ConnectionStatus.DISCONNECTED) {
                                userStatus = "Готово к подключению"
                                errorMessage = null
                            }
                        } else {
                            addVisibleLog("Автоматический выбор канала включен")
                            if (errorMessage == "Выключите Wi-Fi" && !pendingStormDnsAfterWifiOff) {
                                errorMessage = null
                                if (viewModel.uiState.connectionStatus == ConnectionStatus.DISCONNECTED) {
                                    userStatus = "Готово к подключению"
                                }
                            }
                        }
                    },
                    onAccountClick = {
                        accountViewModel.retrySessionRestore()
                        showAccountDialog = true
                    },
                    onSettingsClick = { showSettingsDialog = true },
                    onLogClick = { showLogDialog = true },
                )

                if (showLogDialog) {
                    WhiteZiaLogDialog(
                        logText = buildVisibleLog(
                            localLog = fullVisibleLog,
                            runtimeLogs = viewModel.uiState.connectionLogs,
                        ),
                        onDismiss = { showLogDialog = false },
                    )
                }

                if (showAccountDialog) {
                    WhiteZiaAccountDialog(
                        state = accountState,
                        onDismiss = { showAccountDialog = false },
                        onShowSignIn = accountViewModel::showSignIn,
                        onShowRegister = accountViewModel::showRegister,
                        onShowRecovery = accountViewModel::showRecovery,
                        onLogin = accountViewModel::login,
                        onRegister = accountViewModel::register,
                        onVerifyEmail = accountViewModel::verifyEmail,
                        onResendVerification = accountViewModel::resendVerification,
                        onRequestPasswordReset = accountViewModel::requestPasswordReset,
                        onResetPassword = accountViewModel::resetPassword,
                        onRefresh = accountViewModel::refreshDashboard,
                        onStartPayment = accountViewModel::startPayment,
                        onPaymentOpened = accountViewModel::paymentOpened,
                        onAttachCurrentDevice = accountViewModel::attachCurrentDevice,
                        onDisableDevice = accountViewModel::disableDevice,
                        onLogout = {
                            if (accountState.managedProfileInstalled) {
                                clearPendingConnectionFlow()
                                errorMessage = null
                                userStatus = "отключено"
                                subscriptionLink = ""
                                viewModel.clearSubscriptionProfile()
                                viewModel.disconnect()
                                addVisibleLog("Профиль личного кабинета удалён")
                            }
                            accountViewModel.logout()
                        },
                    )
                }


                if (showSettingsDialog) {
                    WhiteZiaSettingsDialog(
                        settings = viewModel.uiState.settings,
                        subscriptionLink = subscriptionLink,
                        accountManaged = accountState.managedProfileInstalled,
                        onDismiss = { showSettingsDialog = false },
                        onOpenSplitTunnelApps = { updatedSettings, updatedSubscriptionLink ->
                            subscriptionLink = updatedSubscriptionLink
                            viewModel.updateSettings(
                                updatedSettings.copy(subscriptionLink = updatedSubscriptionLink),
                            )
                            showSettingsDialog = false
                            showSplitTunnelDialog = true
                        },
                        onScanSubscription = {
                            subscriptionQrScanner.launch(Intent(context, QrScannerActivity::class.java))
                        },
                        isCheckingForUpdates = updateState is AppUpdateState.Checking,
                        onCheckForUpdates = { updateViewModel.checkForUpdate() },
                        onSave = { updatedSettings, updatedSubscriptionLink ->
                            subscriptionLink = updatedSubscriptionLink
                            viewModel.updateSettings(
                                updatedSettings.copy(subscriptionLink = updatedSubscriptionLink),
                            )
                            showSettingsDialog = false
                        },
                    )
                }


                if (showSplitTunnelDialog) {
                    SplitTunnelDialog(
                        settings = viewModel.uiState.settings,
                        onDismiss = { showSplitTunnelDialog = false },
                        onSettingsChange = {
                            viewModel.updateSettings(it)
                            showSplitTunnelDialog = false
                        },
                    )
                }

                AppUpdateDialog(
                    state = updateState,
                    onDownload = updateViewModel::download,
                    onCancelDownload = updateViewModel::cancelDownload,
                    onInstall = { ready ->
                        if (AppUpdateInstaller.canInstallPackages(context)) {
                            openUpdateInstaller(ready)
                        } else {
                            installPermissionLauncher.launch(
                                AppUpdateInstaller.permissionIntent(context),
                            )
                        }
                    },
                    onRetry = updateViewModel::retry,
                    onDismiss = updateViewModel::dismiss,
                )

            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        profileLinkFromIntent(intent)?.let { inboundProfileLink = it }
    }

    override fun onDestroy() {
        networkMonitor.close()
        super.onDestroy()
    }

    private fun profileLinkFromIntent(intent: Intent?): String? {
        val scheme = intent?.data?.scheme
        if (
            intent?.action != Intent.ACTION_VIEW ||
            (scheme != StormDnsScheme && scheme != StormBundleScheme)
        ) {
            return null
        }
        return intent.dataString?.takeIf(String::isNotBlank)
    }

    private companion object {
        const val StormDnsScheme = "stormdns"
        const val StormBundleScheme = "stormbundle"
    }
}

private const val PermissionActionNone = ""
private const val PermissionActionConnectNow = "connect_now"
private const val NetworkSwitchReconnectDelayMillis = 1_000L
private const val FallbackTransportRestartDelayMillis = 3_000L
private const val ResolverBenchmarkReconnectDelayMillis = 3_000L
private const val ResolverBenchmarkSwitchSettleDelayMillis = 3_000L
private const val AutoResolverBenchmarkAfterConnect = true
