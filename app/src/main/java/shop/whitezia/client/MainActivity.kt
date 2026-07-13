package shop.whitezia.client

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Article
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import shop.whitezia.client.fallback.FallbackNetworkState
import shop.whitezia.client.fallback.FallbackPlanAction
import shop.whitezia.client.fallback.FallbackPlanner
import shop.whitezia.client.model.ConnectionStatus
import shop.whitezia.client.model.ResolverProfile
import shop.whitezia.client.model.ResolverRuntimeState
import shop.whitezia.client.model.WhiteZiaOptions
import shop.whitezia.client.model.WhiteZiaSettings
import shop.whitezia.client.model.WhiteZiaThemeMode
import shop.whitezia.client.model.syncSelectedConnectionProfileFields
import shop.whitezia.client.model.validateResolverText
import shop.whitezia.client.ui.ResolverBenchmarkScore
import shop.whitezia.client.ui.WhiteZiaTheme
import shop.whitezia.client.ui.WhiteZiaViewModel
import shop.whitezia.client.update.AppUpdateDialog
import shop.whitezia.client.update.AppUpdateInstaller
import shop.whitezia.client.update.AppUpdateState
import shop.whitezia.client.update.AppUpdateViewModel

class MainActivity : ComponentActivity() {

    private val viewModel by viewModels<WhiteZiaViewModel>()
    private val updateViewModel by viewModels<AppUpdateViewModel>()
    private var wifiStateCallback: ConnectivityManager.NetworkCallback? = null
    private var wifiStateReceiver: BroadcastReceiver? = null
    private var baseNetworkTransportCallback: ConnectivityManager.NetworkCallback? = null
    private var inboundProfileLink by mutableStateOf("")

    override fun onResume() {
        super.onResume()
        viewModel.refreshBatteryOptimizationStatusWithRetry()
        viewModel.refreshNotificationStatus()
        viewModel.refreshRuntimeConnectionStatus()
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
                var errorMessage by remember { mutableStateOf<String?>(null) }
                val subscriptionQrScanner = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult(),
                ) { result ->
                    val decoded = result.data?.getStringExtra(QrScannerActivity.EXTRA_QR_VALUE)?.trim().orEmpty()
                    when {
                        result.resultCode == Activity.RESULT_OK &&
                            (decoded.startsWith("stormbundle://") || decoded.startsWith("stormdns://")) -> {
                            subscriptionLink = decoded
                            viewModel.updateSubscriptionLink(decoded)
                            errorMessage = null
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
                var wifiEnabled by remember { mutableStateOf(isWifiNetworkAvailable()) }
                var wifiRadioEnabled by remember { mutableStateOf(isWifiRadioEnabled()) }
                var activeBaseNetworkTransport by remember { mutableStateOf(currentBaseNetworkTransport()) }
                var lastNetworkReconnectTransport by remember {
                    mutableStateOf(activeBaseNetworkTransport)
                }
                var showSplitTunnelDialog by rememberSaveable { mutableStateOf(false) }
                var showSubscriptionDialog by rememberSaveable { mutableStateOf(false) }
                var showSettingsDialog by rememberSaveable { mutableStateOf(false) }
                var showLogDialog by rememberSaveable { mutableStateOf(false) }
                var postCheckAttempt by remember { mutableStateOf(0) }
                var completedPostCheckAttempt by remember { mutableStateOf(0) }
                var connectionLaunchStarted by remember { mutableStateOf(false) }
                var connectionWanted by remember {
                    mutableStateOf(viewModel.uiState.connectionStatus != ConnectionStatus.DISCONNECTED)
                }
                var disconnectingByUser by remember { mutableStateOf(false) }
                var resolverScanOperator by remember { mutableStateOf("") }
                var resolverBenchmarkPhase by remember { mutableStateOf("") }
                var resolverBenchmarkLocalText by remember { mutableStateOf("") }
                var resolverBenchmarkLocalSpeed by remember { mutableStateOf(0L) }
                var resolverBenchmarkLocalScore by remember { mutableStateOf<ResolverBenchmarkScore?>(null) }
                var resolverBenchmarkReconnectJob by remember { mutableStateOf<Job?>(null) }
                var networkReconnectJob by remember { mutableStateOf<Job?>(null) }
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
                var resolverScanKick by remember { mutableStateOf(0) }
                var resolverFallbackConnectKick by remember { mutableStateOf(0) }
                var pendingActionAfterVpnPermission by remember { mutableStateOf(PermissionActionNone) }

                fun hasPendingAutomaticTransition(): Boolean =
                    pendingStormDnsAfterWifiOff ||
                        pendingStormDnsAfterResolverScan ||
                        pendingAmneziaFallback ||
                        pendingXrayFallbackAfterAmnezia ||
                        pendingXrayAfterWifiOff ||
                        pendingDnsFallbackAfterAmnezia ||
                        pendingDnsFallbackAfterXray

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
                    pendingActionAfterVpnPermission = PermissionActionNone
                    resolverBenchmarkReconnectJob?.cancel()
                    networkReconnectJob?.cancel()
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
                fun isStormDnsBlockedByWifi(): Boolean = isActiveWifiNetwork()
                fun currentFallbackNetworkState(): FallbackNetworkState = FallbackNetworkState(
                    activeWifi = isActiveWifiNetwork(),
                    mobileAvailable = isMobileNetworkAvailable(),
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
                        subscriptionLink = inboundProfileLink
                        viewModel.updateSubscriptionLink(inboundProfileLink)
                    }
                }

                LaunchedEffect(Unit) {
                    refreshDetectedOperator(
                        preferNetworkOperator = activeBaseNetworkTransport == NetworkTransportMobile || !wifiEnabled,
                        reason = "startup",
                    )
                }

                LaunchedEffect(Unit) {
                    observeWifiState { state ->
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
                    observeBaseNetworkTransport { transport ->
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
                        if (!viewModel.awaitRuntimeStopCompletion()) {
                            connectionWanted = false
                            connectionLaunchStarted = false
                            resolverBenchmarkPhase = ""
                            userStatus = "Не удалось подключиться. Повторите попытку"
                            errorMessage = "VPN туннель не завершил работу"
                            addVisibleLog("Не удалось полностью остановить предыдущий VPN туннель")
                            return@launch
                        }
                        addVisibleLog("Предыдущий VPN туннель полностью остановлен")
                        delay(ResolverBenchmarkReconnectDelayMillis)
                        if (
                            !connectionWanted ||
                            viewModel.uiState.connectionStatus != ConnectionStatus.DISCONNECTED
                        ) {
                            return@launch
                        }
                        postCheckAttempt += 1
                        connectionLaunchStarted = viewModel.beginConnection()
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
                    resolverBenchmarkPhase = ""
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
                        resolverBenchmarkPhase = ""
                        resolverBenchmarkLocalText = ""
                        resolverBenchmarkLocalSpeed = 0L
                        resolverBenchmarkLocalScore = null
                        errorMessage = null
                        userStatus = "Сеть изменилась, переподключаюсь"
                        addVisibleLog("Смена сети: ${networkTransportLabel(nextTransport)}, перезапуск VPN")
                        viewModel.disconnect()
                        delay(NetworkSwitchReconnectDelayMillis)
                        if (
                            currentBaseNetworkTransport() != NetworkTransportNone &&
                            viewModel.uiState.connectionStatus == ConnectionStatus.DISCONNECTED
                        ) {
                            networkReconnectJob = null
                            beginPreparedConnection()
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
                        currentBaseNetworkTransport() == NetworkTransportNone ||
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
                    delay(FallbackTransportRestartDelayMillis)
                    if (
                        !pendingXrayFallbackAfterAmnezia ||
                        viewModel.uiState.connectionStatus != ConnectionStatus.DISCONNECTED ||
                        !connectionWanted
                    ) {
                        return@LaunchedEffect
                    }
                    pendingXrayFallbackAfterAmnezia = false
                    addVisibleLog("AWG туннель закрыт, запускаю Xray")
                    beginXrayFallbackConnection(true)
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
                    delay(FallbackTransportRestartDelayMillis)
                    if (
                        !connectionWanted ||
                        (!pendingDnsFallbackAfterAmnezia && !pendingDnsFallbackAfterXray) ||
                        viewModel.uiState.connectionStatus != ConnectionStatus.DISCONNECTED
                    ) {
                        return@LaunchedEffect
                    }
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
                            if (viewModel.uiState.settings.xrayUri.isNotBlank()) {
                                pendingXrayFallbackAfterAmnezia = true
                                addVisibleLog("AmneziaWG поднялся, но интернет не проходит. Переключаюсь на Xray")
                                userStatus = "Подключение через Xray"
                            } else {
                                pendingDnsFallbackAfterAmnezia = true
                                addVisibleLog("AmneziaWG поднялся, но интернет не проходит. Переключаюсь на DNS канал")
                                userStatus = "Подготовка DNS подключения"
                            }
                            errorMessage = null
                            viewModel.disconnect()
                            return@LaunchedEffect
                        }
                        if (!ok && usingXrayTransport) {
                            val canFallbackToDns = allowDnsFallbackAfterXray || pendingAmneziaFallback
                            pendingAmneziaFallback = false
                            allowDnsFallbackAfterXray = false
                            if (canFallbackToDns) {
                                pendingDnsFallbackAfterXray = true
                                addVisibleLog("Xray поднялся, но проверка не прошла. Переключаюсь на DNS канал")
                                userStatus = "Подготовка DNS подключения"
                                errorMessage = null
                                viewModel.disconnect()
                                return@LaunchedEffect
                            }
                        }
                        if (!ok && usingStormDnsTransport && resolverBenchmarkPhase != "postcheck_yandex") {
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
                                    resolverBenchmarkPhase = ""
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
                                resolverBenchmarkPhase = "postcheck_yandex"
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
                            resolverBenchmarkPhase = ""
                            resolverBenchmarkLocalScore = null
                            resolverBenchmarkReconnectJob?.cancel()
                            networkReconnectJob?.cancel()
                            viewModel.disconnect()
                        }
                        if (ok && usingStormDnsTransport) {
                            when (resolverBenchmarkPhase) {
                                "postcheck_yandex" -> {
                                    resolverBenchmarkPhase = "done"
                                    userStatus = "Подключение успешно"
                                    addVisibleLog("Yandex resolver set прошел Cloudflare-check без закрепления в cache")
                                }
                                "" -> {
                                    if (AutoResolverBenchmarkAfterConnect && viewModel.shouldRunResolverBenchmark()) {
                                        val localResolvers = viewModel.currentResolverEntries()
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
                                        resolverBenchmarkPhase = "testing_yandex"
                                        userStatus = "Оптимизация подключения"
                                        addVisibleLog("Жду 3 секунды перед переключением на Yandex resolver'ы")
                                        delay(ResolverBenchmarkSwitchSettleDelayMillis)
                                        viewModel.applyResolverEntriesForReconnect(viewModel.yandexResolverEntries())
                                        addVisibleLog("Переключаюсь на Yandex resolver'ы")
                                        restartForResolverBenchmark()
                                        return@LaunchedEffect
                                    }
                                }
                                "testing_yandex" -> {
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
                                    viewModel.markResolverBenchmarkCompleted(localResolvers)
                                    val yandexWins = viewModel.shouldPreferYandexResolverScore(
                                        local = localScore,
                                        yandex = yandexScore,
                                        onLog = addVisibleLog,
                                    )
                                    if (yandexWins) {
                                        resolverBenchmarkPhase = "applying_yandex_winner"
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
                                        resolverBenchmarkPhase = "applying_local_winner"
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
                                "applying_yandex_winner" -> {
                                    resolverBenchmarkPhase = "done"
                                    userStatus = "Подключение успешно"
                                    addVisibleLog("Yandex resolver set применен")
                                }
                                "applying_local_winner" -> {
                                    resolverBenchmarkPhase = "done"
                                    userStatus = "Подключение успешно"
                                    addVisibleLog("Local resolver set применен")
                                }
                            }
                            viewModel.reportCurrentResolversToRegistry(addVisibleLog)
                        }
                    }
                }

                SimpleStormDnsScreen(
                    subscriptionLink = subscriptionLink,
                    onSubscriptionClick = { showSubscriptionDialog = true },
                    settings = viewModel.uiState.settings,
                    operatorDisplayLabel = operatorDisplayLabel,
                    connectionStatus = viewModel.uiState.connectionStatus,
                    resolverRuntimeState = viewModel.uiState.resolverRuntimeState,
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
                            beginPreparedConnection()
                            return@SimpleStormDnsScreen
                        }

                        val hasPendingConnectionFlow =
                            pendingStormDnsAfterWifiOff ||
                                pendingStormDnsAfterResolverScan ||
                                pendingAmneziaFallback ||
                                pendingXrayFallbackAfterAmnezia ||
                                pendingXrayAfterWifiOff ||
                                pendingDnsFallbackAfterAmnezia ||
                                pendingDnsFallbackAfterXray ||
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
                            pendingStormDnsAfterWifiOff = false
                            pendingStormDnsAfterResolverScan = false
                            pendingAmneziaFallback = false
                            pendingXrayFallbackAfterAmnezia = false
                            pendingXrayAfterWifiOff = false
                            pendingDnsFallbackAfterAmnezia = false
                            pendingDnsFallbackAfterXray = false
                            allowDnsFallbackAfterXray = false
                            resolverFallbackYandexAllowed = false
                            resolverBenchmarkPhase = ""
                            resolverBenchmarkLocalScore = null
                            errorMessage = null
                            connectionLaunchStarted = false
                            userStatus = "отключено"
                            addVisibleLog("Отключение")
                            viewModel.disconnect()
                            return@SimpleStormDnsScreen
                        }

                        val clickLockedByAutomaticFlow = disconnectingByUser ||
                            userStatus == "производится первичная настройка"
                        if (clickLockedByAutomaticFlow) {
                            addVisibleLog("Автоматическое подключение еще выполняется")
                            return@SimpleStormDnsScreen
                        }

                        when (viewModel.uiState.connectionStatus) {
                            ConnectionStatus.DISCONNECTED -> beginPreparedConnection()
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
                    onSettingsClick = { showSettingsDialog = true },
                    onLogClick = { showLogDialog = true },
                    onSplitTunnelAppsClick = { showSplitTunnelDialog = true },
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

                if (showSettingsDialog) {
                    WhiteZiaSettingsDialog(
                        settings = viewModel.uiState.settings,
                        subscriptionLink = subscriptionLink,
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

                if (showSubscriptionDialog) {
                    SubscriptionDialog(
                        subscriptionLink = subscriptionLink,
                        onDismiss = { showSubscriptionDialog = false },
                        onSave = {
                            subscriptionLink = it
                            viewModel.updateSubscriptionLink(it)
                            showSubscriptionDialog = false
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
        unregisterWifiStateCallback()
        unregisterBaseNetworkTransportCallback()
        super.onDestroy()
    }

    private fun observeWifiState(onChange: (WifiStateSnapshot) -> Unit) {
        val connectivityManager = getSystemService(ConnectivityManager::class.java)
        var lastPublishedState: WifiStateSnapshot? = null
        fun publish(delayMillis: Long = 0L) {
            lifecycleScope.launch {
                if (delayMillis > 0L) {
                    delay(delayMillis)
                }
                val wifiState = WifiStateSnapshot(
                    networkAvailable = isWifiNetworkAvailable(connectivityManager),
                    radioEnabled = isWifiRadioEnabled(),
                )
                if (lastPublishedState == wifiState) {
                    return@launch
                }
                lastPublishedState = wifiState
                onChange(wifiState)
            }
        }
        unregisterWifiStateCallback()
        publish()
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                publish(WifiStateSettleDelayMillis)
            }

            override fun onLost(network: Network) {
                publish(WifiStateSettleDelayMillis)
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                publish()
            }
        }
        wifiStateCallback = callback
        connectivityManager.registerNetworkCallback(
            NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build(),
            callback,
        )
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                publish(WifiStateSettleDelayMillis)
            }
        }
        val receiverRegistered = runCatching {
            val filter = IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                registerReceiver(receiver, filter)
            }
        }.isSuccess
        if (receiverRegistered) {
            wifiStateReceiver = receiver
        }
    }

    private fun unregisterWifiStateCallback() {
        val callback = wifiStateCallback
        wifiStateCallback = null
        if (callback != null) {
            runCatching {
                getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(callback)
            }
        }
        val receiver = wifiStateReceiver
        wifiStateReceiver = null
        if (receiver != null) {
            runCatching { unregisterReceiver(receiver) }
        }
    }

    private fun observeBaseNetworkTransport(onChange: (String) -> Unit) {
        val connectivityManager = getSystemService(ConnectivityManager::class.java)
        var lastPublishedTransport: String? = null
        fun publish(delayMillis: Long = 0L) {
            lifecycleScope.launch {
                if (delayMillis > 0L) {
                    delay(delayMillis)
                }
                val transport = currentBaseNetworkTransport(connectivityManager)
                if (lastPublishedTransport == transport) {
                    return@launch
                }
                lastPublishedTransport = transport
                onChange(transport)
            }
        }
        unregisterBaseNetworkTransportCallback()
        publish()
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                publish(DefaultNetworkSettleDelayMillis)
            }

            override fun onLost(network: Network) {
                publish(DefaultNetworkSettleDelayMillis)
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                publish()
            }
        }
        baseNetworkTransportCallback = callback
        connectivityManager.registerNetworkCallback(
            NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build(),
            callback,
        )
    }

    private fun unregisterBaseNetworkTransportCallback() {
        val callback = baseNetworkTransportCallback
        baseNetworkTransportCallback = null
        if (callback != null) {
            runCatching {
                getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(callback)
            }
        }
    }

    private fun currentBaseNetworkTransport(
        connectivityManager: ConnectivityManager = getSystemService(ConnectivityManager::class.java),
    ): String {
        return when {
            isWifiNetworkAvailable(connectivityManager) -> NetworkTransportWifi
            isMobileNetworkAvailable(connectivityManager) -> NetworkTransportMobile
            connectivityManager.allNetworks.any { network ->
                val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return@any false
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
            } -> NetworkTransportOther
            else -> NetworkTransportNone
        }
    }

    private fun isWifiNetworkAvailable(
        connectivityManager: ConnectivityManager = getSystemService(ConnectivityManager::class.java),
    ): Boolean {
        return connectivityManager.allNetworks.any { network ->
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return@any false
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }
    }

    private fun isWifiRadioEnabled(): Boolean {
        val wifiManager = applicationContext.getSystemService(WifiManager::class.java)
        return wifiManager?.isWifiEnabled ?: isWifiNetworkAvailable()
    }

    private fun isActiveWifiNetwork(
        connectivityManager: ConnectivityManager = getSystemService(ConnectivityManager::class.java),
    ): Boolean {
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun isMobileNetworkAvailable(
        connectivityManager: ConnectivityManager = getSystemService(ConnectivityManager::class.java),
    ): Boolean {
        return connectivityManager.allNetworks.any { network ->
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return@any false
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }
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

private data class WifiStateSnapshot(
    val networkAvailable: Boolean,
    val radioEnabled: Boolean,
)

private data class SplitTunnelAppInfo(
    val packageName: String,
    val label: String,
)

private data class SimOperatorCheckResult(
    val isMismatch: Boolean,
    val message: String,
)

private data class SimOperatorDetectionResult(
    val operatorCode: String?,
    val rawValues: List<String>,
    val isTMobile: Boolean,
)

private fun checkSelectedOperatorAgainstActiveSim(
    context: Context,
    selectedOperatorCode: String,
): SimOperatorCheckResult {
    val selectedLabel = operatorLabel(selectedOperatorCode)
    val detection = readActiveSimOperatorValues(
        context = context,
        preferNetworkOperator = true,
    ).getOrElse { error ->
        return SimOperatorCheckResult(
            isMismatch = false,
            message = "Не удалось проверить SIM: ${error.message ?: error::class.java.simpleName}",
        )
    }
    val rawValues = detection.rawValues
    if (rawValues.isEmpty()) {
        return SimOperatorCheckResult(
            isMismatch = false,
            message = "Не удалось определить активную SIM, продолжаю с выбранным оператором: $selectedLabel",
        )
    }

    val detectedOperator = detection.operatorCode
    if (detectedOperator == null && detection.isTMobile) {
        return SimOperatorCheckResult(
            isMismatch = false,
            message = "SIM T-Mobile: продолжаю с выбранным оператором: $selectedLabel",
        )
    }

    if (detectedOperator == null) {
        return SimOperatorCheckResult(
            isMismatch = false,
            message = "Активная SIM: ${rawValues.joinToString()} — оператор не распознан",
        )
    }

    val detectedLabel = operatorLabel(detectedOperator)
    if (detection.isTMobile) {
        return SimOperatorCheckResult(
            isMismatch = false,
            message = "SIM T-Mobile в сети $detectedLabel",
        )
    }

    return if (detectedOperator == selectedOperatorCode) {
        SimOperatorCheckResult(
            isMismatch = false,
            message = "SIM проверена: $detectedLabel",
        )
    } else {
        SimOperatorCheckResult(
            isMismatch = true,
            message = "Выбран $selectedLabel, но активная SIM: $detectedLabel (${rawValues.joinToString()})",
        )
    }
}

private fun detectActiveSimOperator(
    context: Context,
    preferNetworkOperator: Boolean,
): SimOperatorDetectionResult {
    return readActiveSimOperatorValues(
        context = context,
        preferNetworkOperator = preferNetworkOperator,
    ).getOrElse {
        SimOperatorDetectionResult(operatorCode = null, rawValues = emptyList(), isTMobile = false)
    }
}

private fun readActiveSimOperatorValues(
    context: Context,
    preferNetworkOperator: Boolean,
): Result<SimOperatorDetectionResult> = runCatching {
    val telephonyManager = context.getSystemService(TelephonyManager::class.java)
        ?: return@runCatching SimOperatorDetectionResult(
            operatorCode = null,
            rawValues = emptyList(),
            isTMobile = false,
        )
    val dataTelephonyManager = runCatching {
        val defaultDataSubId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            SubscriptionManager.getDefaultDataSubscriptionId()
        } else {
            SubscriptionManager.INVALID_SUBSCRIPTION_ID
        }
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
            defaultDataSubId != SubscriptionManager.INVALID_SUBSCRIPTION_ID
        ) {
            telephonyManager.createForSubscriptionId(defaultDataSubId)
        } else {
            telephonyManager
        }
    }.getOrDefault(telephonyManager)
    val networkValues = listOf(
        dataTelephonyManager.networkOperatorName,
        dataTelephonyManager.networkOperator,
    ).normalizedOperatorValues()
    val simValues = listOf(
        dataTelephonyManager.simOperatorName,
        dataTelephonyManager.simOperator,
    ).normalizedOperatorValues()
    val subscriptionValues = emptyList<String>()
    val mobileNetworkActive = isMobileNetworkAvailable(context)
    val rawValues = if (preferNetworkOperator || mobileNetworkActive) {
        networkValues + simValues + subscriptionValues
    } else {
        simValues + subscriptionValues + networkValues
    }.distinct()
    val normalizedValues = rawValues.map { it.lowercase(Locale.US) }
    val detectedFromNetwork = detectOperatorCode(networkValues)
    val detectedFromSim = detectOperatorCode(simValues + subscriptionValues)
    val detectedOperator = if (preferNetworkOperator || mobileNetworkActive) {
        detectedFromNetwork ?: detectedFromSim
    } else {
        detectedFromSim ?: detectedFromNetwork
    }
    SimOperatorDetectionResult(
        operatorCode = detectedOperator,
        rawValues = rawValues,
        isTMobile = normalizedValues.any { value -> TMobileOperatorMarkers.any { it in value } },
    )
}

private fun List<String>.normalizedOperatorValues(): List<String> {
    return map(String::trim)
        .filter(String::isNotEmpty)
        .distinct()
}

private fun isMobileNetworkAvailable(context: Context): Boolean {
    val connectivityManager = context.getSystemService(ConnectivityManager::class.java) ?: return false
    return connectivityManager.allNetworks.any { network ->
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return@any false
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}

private fun detectOperatorCode(rawValues: List<String>): String? {
    val normalizedValues = rawValues.map { it.lowercase(Locale.US) }
    if (normalizedValues.any { value -> MtsOperatorMarkers.any { it in value } }) {
        return WhiteZiaOptions.OperatorMts
    }
    if (normalizedValues.any { value -> BeelineOperatorMarkers.any { it in value } }) {
        return WhiteZiaOptions.OperatorBeeline
    }
    if (normalizedValues.any { value -> Tele2OperatorMarkers.any { it in value } }) {
        return WhiteZiaOptions.OperatorTele2
    }
    if (normalizedValues.any { value -> MegafonYotaOperatorMarkers.any { it in value } }) {
        return WhiteZiaOptions.OperatorMegafonYota
    }
    return null
}

@Composable
private fun SimpleStormDnsScreen(
    subscriptionLink: String,
    onSubscriptionClick: () -> Unit,
    settings: WhiteZiaSettings,
    operatorDisplayLabel: String,
    connectionStatus: ConnectionStatus,
    resolverRuntimeState: ResolverRuntimeState,
    wifiEnabled: Boolean,
    errorMessage: String?,
    userStatus: String,
    isDisconnecting: Boolean,
    forceDnsTunnel: Boolean,
    xrayPreflightBlocked: Boolean,
    onConnectClick: () -> Unit,
    onXrayOnlyModeChange: (Boolean) -> Unit,
    onForceDnsTunnelChange: (Boolean) -> Unit,
    onSettingsClick: () -> Unit,
    onLogClick: () -> Unit,
    onSplitTunnelAppsClick: () -> Unit,
) {
    val isRunning = connectionStatus != ConnectionStatus.DISCONNECTED
    val resolverCount = remember(settings.resolverText) {
        settings.resolverText.lineSequence().count { it.isNotBlank() }
    }
    val configuredResolvers = remember(settings.resolverText) {
        settings.resolverText
            .lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()
            .toList()
    }
    val runtimeResolvers = remember(resolverRuntimeState) {
        (
            resolverRuntimeState.activeResolvers +
                resolverRuntimeState.standbyResolvers +
                resolverRuntimeState.validResolvers
            )
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()
    }
    val isPrimarySetup = userStatus == "производится первичная настройка"
    val isDnsPreparation = userStatus == "Подготовка DNS подключения"
    val isOptimizingConnection = userStatus == "Оптимизация подключения"
    val showProgress = isPrimarySetup || connectionStatus == ConnectionStatus.CONNECTING ||
        userStatus == "Подключение" ||
        isOptimizingConnection ||
        userStatus == "Подключение через AmneziaWG" ||
        userStatus == "Подключение через Xray" ||
        userStatus == "Проверка AmneziaWG" ||
        userStatus == "Проверка Xray" ||
        userStatus == "Проверка подключения" ||
        userStatus == "Подготовка DNS подключения"
    val isConnectionFinalizing = connectionStatus == ConnectionStatus.CONNECTED && showProgress
    val isAutomaticConnectionFlow = isPrimarySetup ||
        isDnsPreparation ||
        isDisconnecting ||
        connectionStatus == ConnectionStatus.CONNECTING ||
        isConnectionFinalizing ||
        userStatus == "Подключение" ||
        userStatus == "Подключение через AmneziaWG" ||
        userStatus == "Подключение через Xray" ||
        userStatus == "Проверка AmneziaWG" ||
        userStatus == "Проверка Xray" ||
        userStatus == "Проверка подключения" ||
        userStatus == "Оптимизация подключения"
    val canForceStop = !isPrimarySetup &&
        !isDisconnecting &&
        (
            connectionStatus != ConnectionStatus.DISCONNECTED ||
                isDnsPreparation ||
                isOptimizingConnection
            )
    val canConnect = !isRunning &&
        subscriptionLink.trim().isNotEmpty() &&
        !isAutomaticConnectionFlow &&
        !xrayPreflightBlocked
    val canDisconnect = canForceStop
    val canChangeDnsMode = !isRunning && !isAutomaticConnectionFlow
    val manualMode = settings.manualMode
    val xrayOnlyEnabled = settings.transportMode == WhiteZiaOptions.TransportXray
    val minimalConnectionView = isAutomaticConnectionFlow && !isDisconnecting
    val buttonProgress = when {
        isDisconnecting -> 0.35f
        errorMessage != null -> 1f
        isConnectionFinalizing -> 0.88f
        connectionStatus == ConnectionStatus.CONNECTED -> 1f
        connectionStatus == ConnectionStatus.CONNECTING -> 0.62f
        isPrimarySetup -> 0.18f
        else -> 0.08f
    }
    val statusText = when {
        isDisconnecting -> "отключение"
        errorMessage != null -> {
            if (errorMessage == "Выключите Wi-Fi" || errorMessage == "Включите мобильный интернет") {
                errorMessage.orEmpty()
            } else {
                "ошибка, попробуйте снова"
            }
        }
        minimalConnectionView -> "идет подключение, это может занять пару минут"
        connectionStatus == ConnectionStatus.CONNECTED -> "успешное подключение"
        else -> userStatus.ifBlank { "Готово к подключению" }
    }
    val view = LocalView.current
    SideEffect {
        val window = (view.context as Activity).window
        window.statusBarColor = WhiteZiaBackground.toArgb()
        window.navigationBarColor = WhiteZiaBackground.toArgb()
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = WhiteZiaBackground,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.Top,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    WhiteZiaLogo(
                        modifier = Modifier.align(Alignment.Center),
                    )
                    Row(
                        modifier = Modifier.align(Alignment.CenterEnd),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onLogClick) {
                            Icon(
                                imageVector = Icons.Rounded.Article,
                                contentDescription = "Логи",
                                tint = WhiteZiaTextMuted,
                            )
                        }
                        IconButton(
                            enabled = !isAutomaticConnectionFlow,
                            onClick = onSettingsClick,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Settings,
                                contentDescription = "Настройки",
                                tint = WhiteZiaTextMuted,
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(28.dp))
                if (!minimalConnectionView) {
                    Text(
                        text = "Оператор SIM: $operatorDisplayLabel".uppercase(Locale.US),
                        style = WhiteZiaSmallTextStyle(),
                        color = WhiteZiaSetupOrange,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                }
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = statusText,
                    style = WhiteZiaStatusTextStyle(),
                    textAlign = TextAlign.Center,
                    color = when {
                        errorMessage != null -> WhiteZiaError
                        connectionStatus == ConnectionStatus.CONNECTED -> WhiteZiaSuccess
                        minimalConnectionView -> WhiteZiaBlue
                        isPrimarySetup -> WhiteZiaSetupOrange
                        showProgress -> WhiteZiaBlue
                        statusText == "Готово к подключению" -> WhiteZiaSuccess
                        else -> WhiteZiaTextMuted
                    },
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
                if (manualMode && !minimalConnectionView) {
                    Spacer(modifier = Modifier.height(18.dp))
                    ForceDnsTunnelSwitch(
                        enabled = forceDnsTunnel,
                        interactiveEnabled = canChangeDnsMode,
                        wifiEnabled = wifiEnabled,
                        onToggle = { onForceDnsTunnelChange(!forceDnsTunnel) },
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    XrayOnlySwitch(
                        enabled = xrayOnlyEnabled,
                        interactiveEnabled = canChangeDnsMode,
                        xrayAvailable = settings.xrayUri.isNotBlank(),
                        onToggle = { onXrayOnlyModeChange(!xrayOnlyEnabled) },
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                CircularConnectionButton(
                    connectionStatus = connectionStatus,
                    progress = buttonProgress,
                    enabled = canConnect || canDisconnect,
                    isError = errorMessage != null,
                    isDisconnecting = isDisconnecting,
                    isFinalizing = isConnectionFinalizing,
                    isPrimarySetup = isPrimarySetup,
                    isOptimizing = isOptimizingConnection,
                    canForceStop = canForceStop,
                    onClick = onConnectClick,
                )
            }
        }
    }
}

@Composable
private fun XrayOnlySwitch(
    enabled: Boolean,
    interactiveEnabled: Boolean,
    xrayAvailable: Boolean,
    onToggle: () -> Unit,
) {
    val rowEnabled = interactiveEnabled && xrayAvailable
    val subtitle = when {
        !xrayAvailable -> "Xray ссылка не импортирована"
        enabled -> "AWG и DNS fallback отключены"
        else -> "Можно проверить только Xray"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(WhiteZiaPanel, CircleShape)
            .border(
                width = 1.dp,
                color = if (enabled) WhiteZiaBlue.copy(alpha = 0.65f) else Color.White.copy(alpha = 0.08f),
                shape = CircleShape,
            )
            .clickable(enabled = rowEnabled, onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = "Только Xray",
                style = TextStyle(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.0.sp,
                ),
                color = Color.White.copy(alpha = if (rowEnabled) 0.86f else 0.42f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = TextStyle(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Normal,
                    letterSpacing = 0.4.sp,
                ),
                color = if (xrayAvailable) WhiteZiaTextMuted else WhiteZiaSetupOrange,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = enabled,
            onCheckedChange = null,
            enabled = rowEnabled,
        )
    }
}

@Composable
private fun ForceDnsTunnelSwitch(
    enabled: Boolean,
    interactiveEnabled: Boolean,
    wifiEnabled: Boolean,
    onToggle: () -> Unit,
) {
    val subtitle = when {
        enabled && wifiEnabled -> "DNS канал. Выключите Wi-Fi перед подключением"
        enabled -> "DNS канал будет использоваться сразу"
        else -> "Авто: сначала AmneziaWG, затем DNS fallback"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(WhiteZiaPanel, CircleShape)
            .border(
                width = 1.dp,
                color = if (enabled) WhiteZiaBlue.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.08f),
                shape = CircleShape,
            )
            .clickable(enabled = interactiveEnabled, onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = "Использовать DNS канал",
                style = TextStyle(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.0.sp,
                ),
                color = Color.White.copy(alpha = if (interactiveEnabled) 0.86f else 0.42f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = TextStyle(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Normal,
                    letterSpacing = 0.4.sp,
                ),
                color = when {
                    !interactiveEnabled -> WhiteZiaTextDim
                    enabled && wifiEnabled -> WhiteZiaSetupOrange
                    else -> WhiteZiaTextMuted
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = enabled,
            onCheckedChange = null,
            enabled = interactiveEnabled,
        )
    }
}

@Composable
private fun WhiteZiaLogo(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "White",
            style = WhiteZiaLogoTextStyle(),
            color = Color.White.copy(alpha = 0.92f),
        )
        Text(
            text = "Zia",
            style = WhiteZiaLogoTextStyle(),
            color = WhiteZiaRed,
        )
    }
}

private fun WhiteZiaLogoTextStyle(): TextStyle {
    return TextStyle(
        fontSize = 19.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.8.sp,
    )
}

private fun WhiteZiaSmallTextStyle(): TextStyle {
    return TextStyle(
        fontSize = 11.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 3.sp,
    )
}

private fun WhiteZiaStatusTextStyle(): TextStyle {
    return TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 2.4.sp,
    )
}

private fun WhiteZiaTabTextStyle(): TextStyle {
    return TextStyle(
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 1.1.sp,
    )
}

@Composable
private fun CircularConnectionButton(
    connectionStatus: ConnectionStatus,
    progress: Float,
    enabled: Boolean,
    isError: Boolean,
    isDisconnecting: Boolean,
    isFinalizing: Boolean,
    isPrimarySetup: Boolean,
    isOptimizing: Boolean,
    canForceStop: Boolean,
    onClick: () -> Unit,
) {
    val idleBlue = Color(0xFF5B6AF0)
    val connectedGreen = Color(0xFF00C9A7)
    val disconnectOrange = Color(0xFFFFA726)
    val errorRed = Color(0xFFFF4D4D)
    val ringColor = when {
        isError -> errorRed
        isPrimarySetup -> disconnectOrange
        isDisconnecting -> disconnectOrange
        isFinalizing -> idleBlue
        connectionStatus == ConnectionStatus.CONNECTED -> connectedGreen
        else -> idleBlue
    }
    val innerButtonColor = when {
        isError -> Color(0xFF1E1414)
        isFinalizing -> Color(0xFF16161F)
        connectionStatus == ConnectionStatus.CONNECTED -> Color(0xFF141E1C)
        else -> Color(0xFF16161F)
    }
    val iconBubbleColor = when {
        isError -> errorRed.copy(alpha = 0.13f)
        isPrimarySetup -> disconnectOrange.copy(alpha = 0.16f)
        isDisconnecting -> disconnectOrange.copy(alpha = 0.16f)
        isFinalizing -> idleBlue.copy(alpha = 0.20f)
        connectionStatus == ConnectionStatus.CONNECTED -> connectedGreen.copy(alpha = 0.13f)
        else -> idleBlue.copy(alpha = 0.20f)
    }
    var displayedProgress by remember { mutableStateOf(progress.coerceIn(0f, 1f)) }
    var pulseProgress by remember { mutableStateOf(0f) }
    var activeArcStart by remember { mutableStateOf(-90f) }
    var activePulseProgress by remember { mutableStateOf(0f) }
    val buttonText = when {
        isDisconnecting -> "ОТКЛЮЧЕНИЕ"
        isError -> "ОШИБКА"
        canForceStop && connectionStatus != ConnectionStatus.CONNECTED -> "ОТКЛЮЧИТЬ"
        isFinalizing -> ""
        connectionStatus == ConnectionStatus.CONNECTED -> "ПОДКЛЮЧЕНО"
        connectionStatus == ConnectionStatus.CONNECTING -> ""
        else -> "ПОДКЛЮЧИТЬСЯ"
    }
    val buttonIcon = when {
        isError -> Icons.Rounded.Close
        canForceStop && connectionStatus != ConnectionStatus.CONNECTED -> Icons.Rounded.Stop
        isDisconnecting || isFinalizing -> Icons.Rounded.Sync
        connectionStatus == ConnectionStatus.CONNECTED -> Icons.Rounded.Check
        else -> Icons.Rounded.PowerSettingsNew
    }
    LaunchedEffect(connectionStatus, isError, isDisconnecting, isFinalizing, isPrimarySetup, isOptimizing, progress) {
        when {
            isPrimarySetup || isOptimizing -> {
                displayedProgress = 0f
            }
            isError -> {
                val start = displayedProgress
                repeat(28) { step ->
                    displayedProgress = start * (1f - (step + 1) / 28f)
                    delay(50)
                }
            }
            connectionStatus == ConnectionStatus.CONNECTING || isFinalizing -> {
                displayedProgress = 0f
                repeat(20) { step ->
                    displayedProgress = ((step + 1) / 20f).coerceAtMost(0.96f)
                    delay(18)
                }
            }
            connectionStatus == ConnectionStatus.CONNECTED -> {
                displayedProgress = 1f
            }
            isDisconnecting -> {
                displayedProgress = 0.35f
            }
            else -> {
                displayedProgress = 0f
            }
        }
    }
    LaunchedEffect(isPrimarySetup, isOptimizing) {
        if (!isPrimarySetup && !isOptimizing) {
            activeArcStart = -90f
            activePulseProgress = 0f
            return@LaunchedEffect
        }
        while (true) {
            repeat(72) { step ->
                activeArcStart = -90f + step * 5f
                activePulseProgress = (step + 1) / 72f
                delay(14)
            }
            activePulseProgress = 0f
        }
    }
    LaunchedEffect(connectionStatus, isFinalizing) {
        if (connectionStatus != ConnectionStatus.CONNECTED || isFinalizing) {
            pulseProgress = 0f
            return@LaunchedEffect
        }
        while (true) {
            repeat(36) { step ->
                pulseProgress = (step + 1) / 36f
                delay(50)
            }
            pulseProgress = 0f
        }
    }
    Box(
        modifier = Modifier.size(180.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 4.dp.toPx()
            val inset = strokeWidth / 2f
            val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
            if (isOptimizing) {
                drawCircle(
                    color = ringColor.copy(alpha = 0.18f * (1f - activePulseProgress)),
                    radius = (size.minDimension / 2f - 12.dp.toPx()) * (1f + 0.12f * activePulseProgress),
                    style = Stroke(width = 2.dp.toPx()),
                )
            } else if (connectionStatus == ConnectionStatus.CONNECTED && !isFinalizing) {
                drawCircle(
                    color = connectedGreen.copy(alpha = 0.35f * (1f - pulseProgress)),
                    radius = (size.minDimension / 2f - 12.dp.toPx()) * (1f + 0.18f * pulseProgress),
                    style = Stroke(width = 2.dp.toPx()),
                )
            }
            drawArc(
                color = Color.White.copy(alpha = 0.05f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
            if (isPrimarySetup || isOptimizing) {
                drawArc(
                    color = ringColor,
                    startAngle = activeArcStart,
                    sweepAngle = if (isOptimizing) 116f else 82f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                )
            } else {
                drawArc(
                    color = ringColor,
                    startAngle = -90f,
                    sweepAngle = 360f * displayedProgress.coerceIn(0f, 1f),
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                )
            }
        }
        Box(
            modifier = Modifier
                .size(156.dp)
                .shadow(elevation = 8.dp, shape = CircleShape, clip = false)
                .background(innerButtonColor, CircleShape)
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.04f),
                    shape = CircleShape,
                )
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            if (isOptimizing) {
                                iconBubbleColor.copy(alpha = 0.16f + 0.10f * activePulseProgress)
                            } else {
                                iconBubbleColor
                            },
                            CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        modifier = Modifier.size(22.dp),
                        imageVector = buttonIcon,
                        contentDescription = null,
                        tint = ringColor,
                    )
                }
                if (buttonText.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = buttonText,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal,
                            letterSpacing = 2.5.sp,
                        ),
                        color = if (enabled) ringColor else Color.White.copy(alpha = 0.33f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun SubscriptionControls(
    subscriptionLink: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    TextButton(
        enabled = enabled,
        onClick = onClick,
    ) {
        Text(
            text = if (subscriptionLink.trim().isNotEmpty()) {
                "Подписка добавлена"
            } else {
                "Добавить подписку"
            },
        )
    }
}

@Composable
private fun SubscriptionDialog(
    subscriptionLink: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var draftLink by rememberSaveable(subscriptionLink) {
        mutableStateOf(subscriptionLink)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Подписка") },
        text = {
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = draftLink,
                onValueChange = { draftLink = it },
                label = { Text("Подписка stormdns://") },
                placeholder = { Text("stormdns://...") },
                singleLine = false,
                minLines = 4,
                colors = WhiteZiaTextFieldColors(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(draftLink) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun WhiteZiaLogDialog(
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
            TextButton(onClick = onDismiss) {
                Text("Закрыть")
            }
        },
    )
}

private fun copyTextToClipboard(
    context: Context,
    label: String,
    text: String,
) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
}

@Composable
private fun WhiteZiaSettingsDialog(
    settings: WhiteZiaSettings,
    subscriptionLink: String,
    onDismiss: () -> Unit,
    onOpenSplitTunnelApps: (WhiteZiaSettings, String) -> Unit,
    onScanSubscription: () -> Unit,
    onCheckForUpdates: () -> Unit,
    onSave: (WhiteZiaSettings, String) -> Unit,
) {
    var selectedSectionIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var draftSubscription by rememberSaveable(subscriptionLink) {
        mutableStateOf(subscriptionLink)
    }
    var draftSettings by remember(settings) {
        mutableStateOf(settings)
    }
    val resolverValidation = remember(draftSettings.customResolverText) {
        validateResolverText(draftSettings.customResolverText)
    }
    val customResolversValid = !draftSettings.customResolversEnabled ||
        resolverValidation.normalizedResolvers.isNotEmpty() &&
        resolverValidation.invalidEntries.isEmpty()
    fun normalizedDraftSettings(): WhiteZiaSettings {
        val normalizedResolvers = resolverValidation.normalizedText
        return if (draftSettings.customResolversEnabled) {
            draftSettings.copy(
                customResolverText = normalizedResolvers,
                resolverText = normalizedResolvers,
            )
        } else {
            draftSettings
        }.syncSelectedConnectionProfileFields()
    }
    val sections = listOf(
        SettingsSection("Подписка", "Профиль и split tunnel", Icons.Rounded.Link),
        SettingsSection(
            "DNS resolver'ы",
            if (draftSettings.customResolversEnabled) "Кастомный список" else "Автоматический поиск",
            Icons.Rounded.Dns,
        ),
        SettingsSection(
            "StormDNS",
            if (draftSettings.customConnectionSettingsEnabled) "Кастомные параметры" else "Стандартные параметры",
            Icons.Rounded.Tune,
        ),
        SettingsSection(
            "Системные настройки",
            if (draftSettings.manualMode) "Ручной режим" else "Автоматический режим",
            Icons.Rounded.Settings,
        ),
    )
    val isWideLayout = LocalConfiguration.current.screenWidthDp >= SettingsTwoPaneMinWidthDp
    val effectiveSectionIndex = selectedSectionIndex ?: if (isWideLayout) 0 else null

    BackHandler {
        if (!isWideLayout && selectedSectionIndex != null) {
            selectedSectionIndex = null
        } else {
            onDismiss()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = WhiteZiaBackground,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
            ) {
                SettingsTopBar(
                    title = effectiveSectionIndex
                        ?.takeIf { !isWideLayout }
                        ?.let { sections[it].title }
                        ?: "Настройки",
                    showBack = !isWideLayout && effectiveSectionIndex != null,
                    onBack = { selectedSectionIndex = null },
                    onClose = onDismiss,
                )
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                if (isWideLayout) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    ) {
                        SettingsSectionList(
                            modifier = Modifier
                                .width(248.dp)
                                .fillMaxHeight(),
                            sections = sections,
                            selectedIndex = effectiveSectionIndex,
                            onSectionClick = { selectedSectionIndex = it },
                        )
                        Spacer(
                            modifier = Modifier
                                .width(1.dp)
                                .fillMaxHeight()
                                .background(Color.White.copy(alpha = 0.08f)),
                        )
                        SettingsSectionContent(
                            modifier = Modifier.weight(1f),
                            sectionIndex = effectiveSectionIndex ?: 0,
                            subscriptionLink = draftSubscription,
                            settings = draftSettings,
                            resolverValidation = resolverValidation,
                            customResolversValid = customResolversValid,
                            onSubscriptionChange = { draftSubscription = it },
                            onSettingsChange = { draftSettings = it },
                            onScanSubscription = onScanSubscription,
                            onCheckForUpdates = onCheckForUpdates,
                            onOpenSplitTunnelApps = {
                                onOpenSplitTunnelApps(normalizedDraftSettings(), draftSubscription)
                            },
                        )
                    }
                } else if (effectiveSectionIndex == null) {
                    SettingsSectionList(
                        modifier = Modifier.weight(1f),
                        sections = sections,
                        selectedIndex = null,
                        onSectionClick = { selectedSectionIndex = it },
                    )
                } else {
                    SettingsSectionContent(
                        modifier = Modifier.weight(1f),
                        sectionIndex = effectiveSectionIndex,
                        subscriptionLink = draftSubscription,
                        settings = draftSettings,
                        resolverValidation = resolverValidation,
                        customResolversValid = customResolversValid,
                        onSubscriptionChange = { draftSubscription = it },
                        onSettingsChange = { draftSettings = it },
                        onScanSubscription = onScanSubscription,
                        onCheckForUpdates = onCheckForUpdates,
                        onOpenSplitTunnelApps = {
                            onOpenSplitTunnelApps(normalizedDraftSettings(), draftSubscription)
                        },
                    )
                }
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text("Отмена") }
                    TextButton(
                        enabled = customResolversValid,
                        onClick = { onSave(normalizedDraftSettings(), draftSubscription) },
                    ) {
                        Text("Сохранить")
                    }
                }
            }
        }
    }
}

private data class SettingsSection(
    val title: String,
    val summary: String,
    val icon: ImageVector,
)

@Composable
private fun SettingsTopBar(
    title: String,
    showBack: Boolean,
    onBack: () -> Unit,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showBack) {
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = "Назад", tint = Color.White)
            }
        } else {
            Spacer(modifier = Modifier.width(48.dp))
        }
        Text(
            modifier = Modifier.weight(1f),
            text = title,
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        IconButton(onClick = onClose) {
            Icon(Icons.Rounded.Close, contentDescription = "Закрыть", tint = WhiteZiaTextMuted)
        }
    }
}

@Composable
private fun SettingsSectionList(
    modifier: Modifier,
    sections: List<SettingsSection>,
    selectedIndex: Int?,
    onSectionClick: (Int) -> Unit,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
    ) {
        sections.forEachIndexed { index, section ->
            SettingsSectionRow(
                section = section,
                selected = selectedIndex == index,
                onClick = { onSectionClick(index) },
            )
        }
    }
}

@Composable
private fun SettingsSectionRow(
    section: SettingsSection,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) WhiteZiaBlue.copy(alpha = 0.12f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = section.icon,
            contentDescription = null,
            tint = if (selected) WhiteZiaBlue else WhiteZiaTextMuted,
            modifier = Modifier.size(22.dp),
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = section.title,
                color = Color.White.copy(alpha = 0.88f),
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = section.summary,
                color = WhiteZiaTextMuted,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = WhiteZiaTextDim)
    }
}

@Composable
private fun SettingsSectionContent(
    modifier: Modifier,
    sectionIndex: Int,
    subscriptionLink: String,
    settings: WhiteZiaSettings,
    resolverValidation: shop.whitezia.client.model.ResolverTextValidation,
    customResolversValid: Boolean,
    onSubscriptionChange: (String) -> Unit,
    onSettingsChange: (WhiteZiaSettings) -> Unit,
    onScanSubscription: () -> Unit,
    onCheckForUpdates: () -> Unit,
    onOpenSplitTunnelApps: () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        when (sectionIndex) {
            0 -> SubscriptionSettingsTab(
                subscriptionLink = subscriptionLink,
                settings = settings,
                onSubscriptionChange = onSubscriptionChange,
                onScanSubscription = onScanSubscription,
                onOpenSplitTunnelApps = {
                    if (customResolversValid) onOpenSplitTunnelApps()
                },
            )
            1 -> ResolverSettingsTab(settings, resolverValidation, onSettingsChange)
            2 -> StormDnsAdvancedSettingsTab(settings, onSettingsChange)
            else -> SystemSettingsTab(settings, onSettingsChange, onCheckForUpdates)
        }
    }
}

private const val SettingsTwoPaneMinWidthDp = 600

@Composable
private fun SubscriptionSettingsTab(
    subscriptionLink: String,
    settings: WhiteZiaSettings,
    onSubscriptionChange: (String) -> Unit,
    onScanSubscription: () -> Unit,
    onOpenSplitTunnelApps: () -> Unit,
) {
    SettingsSectionTitle("Подписка")
    OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = subscriptionLink,
        onValueChange = onSubscriptionChange,
        label = { Text("stormbundle:// или stormdns://") },
        singleLine = false,
        minLines = 4,
        colors = WhiteZiaTextFieldColors(),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
    )
    TextButton(onClick = onScanSubscription) {
        Text("Сканировать QR")
    }
    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
    SettingsSectionTitle("Split tunnel")
    Text(
        text = splitTunnelSummary(settings),
        color = WhiteZiaTextMuted,
        style = MaterialTheme.typography.bodySmall,
    )
    TextButton(onClick = onOpenSplitTunnelApps) {
        Icon(imageVector = Icons.Rounded.Apps, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text("Выбрать приложения")
    }
}

@Composable
private fun ResolverSettingsTab(
    settings: WhiteZiaSettings,
    validation: shop.whitezia.client.model.ResolverTextValidation,
    onSettingsChange: (WhiteZiaSettings) -> Unit,
) {
    SettingsSwitchRow(
        title = "Кастомные resolver'ы",
        subtitle = "StormDNS будет работать через этот список вместо автопоиска",
        checked = settings.customResolversEnabled,
        onCheckedChange = {
            onSettingsChange(
                settings.copy(
                    customResolversEnabled = it,
                    customResolverText = settings.customResolverText.ifBlank { settings.resolverText },
                    selectedResolverProfileId = if (it) {
                        ResolverProfile.CustomId
                    } else {
                        settings.selectedResolverProfileId.takeIf { profileId ->
                            profileId != ResolverProfile.CustomId
                        }.orEmpty()
                    },
                ).syncSelectedConnectionProfileFields(),
            )
        },
    )
    OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        enabled = settings.customResolversEnabled,
        value = if (settings.customResolversEnabled) {
            settings.customResolverText
        } else {
            settings.customResolverText.ifBlank { settings.resolverText }
        },
        onValueChange = {
            onSettingsChange(settings.copy(customResolverText = it))
        },
        label = { Text("Resolver list") },
        placeholder = { Text("10.112.250.2\n77.88.8.8") },
        singleLine = false,
        minLines = 6,
        colors = WhiteZiaTextFieldColors(),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
    )
    val statusText = when {
        !settings.customResolversEnabled -> "Автопоиск resolver'ов включен"
        validation.invalidEntries.isNotEmpty() -> "Некорректные строки: ${validation.invalidEntries.joinToString()}"
        validation.normalizedResolvers.isEmpty() -> "Добавьте хотя бы один resolver"
        else -> "Resolver'ов: ${validation.normalizedResolvers.size}"
    }
    Text(
        text = statusText,
        color = if (settings.customResolversEnabled && !validation.isValid) {
            WhiteZiaError
        } else {
            WhiteZiaTextDim
        },
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun SystemSettingsTab(
    settings: WhiteZiaSettings,
    onSettingsChange: (WhiteZiaSettings) -> Unit,
    onCheckForUpdates: () -> Unit,
) {
    SettingsSwitchRow(
        title = "Ручной режим",
        subtitle = "Показывать переключатели каналов на главном экране",
        checked = settings.manualMode,
        onCheckedChange = { enabled ->
            onSettingsChange(
                settings.copy(
                    manualMode = enabled,
                    forceDnsTunnel = if (enabled) settings.forceDnsTunnel else false,
                    transportMode = if (enabled) settings.transportMode else WhiteZiaOptions.TransportAuto,
                ),
            )
        },
    )
    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
    SettingsSectionTitle("Приложение")
    Text(
        text = "Версия ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
        color = WhiteZiaTextMuted,
        style = MaterialTheme.typography.bodyMedium,
    )
    TextButton(onClick = onCheckForUpdates) {
        Icon(Icons.Rounded.Sync, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text("Проверить обновления")
    }
}

@Composable
private fun StormDnsAdvancedSettingsTab(
    settings: WhiteZiaSettings,
    onSettingsChange: (WhiteZiaSettings) -> Unit,
) {
    val customSettingsEnabled = settings.customConnectionSettingsEnabled
    var expandedGroup by rememberSaveable { mutableStateOf("mtu") }

    fun updateString(update: WhiteZiaSettings.(String) -> WhiteZiaSettings): (String) -> Unit {
        return { value -> onSettingsChange(settings.update(value)) }
    }

    @Composable
    fun StormSettingsTextField(
        label: String,
        value: String,
        onValueChange: (String) -> Unit,
    ) {
        SettingsTextField(
            label = label,
            value = value,
            onValueChange = onValueChange,
            enabled = customSettingsEnabled,
        )
    }

    SettingsSwitchRow(
        title = "Кастомные настройки",
        subtitle = "Не перезаписывать MTU, duplication, workers и timeout при подключении",
        checked = settings.customConnectionSettingsEnabled,
        onCheckedChange = { onSettingsChange(settings.copy(customConnectionSettingsEnabled = it)) },
    )
    StormSettingsGroup("MTU", expandedGroup == "mtu", { expandedGroup = toggleGroup(expandedGroup, "mtu") }) {
        SettingsFieldGrid {
            StormSettingsTextField("Upload MTU: минимум", settings.minUploadMtu, updateString { copy(minUploadMtu = it) })
            StormSettingsTextField("Upload MTU: максимум", settings.maxUploadMtu, updateString { copy(maxUploadMtu = it) })
            StormSettingsTextField("Download MTU: минимум", settings.minDownloadMtu, updateString { copy(minDownloadMtu = it) })
            StormSettingsTextField("Download MTU: максимум", settings.maxDownloadMtu, updateString { copy(maxDownloadMtu = it) })
            StormSettingsTextField("Повторы проверки resolver'ов", settings.mtuTestRetriesResolvers, updateString { copy(mtuTestRetriesResolvers = it) })
            StormSettingsTextField("Timeout проверки resolver'ов", settings.mtuTestTimeoutResolvers, updateString { copy(mtuTestTimeoutResolvers = it) })
            StormSettingsTextField("Параллельные проверки resolver'ов", settings.mtuTestParallelismResolvers, updateString { copy(mtuTestParallelismResolvers = it) })
            StormSettingsTextField("Повторы проверки логов", settings.mtuTestRetriesLogs, updateString { copy(mtuTestRetriesLogs = it) })
            StormSettingsTextField("Timeout проверки логов", settings.mtuTestTimeoutLogs, updateString { copy(mtuTestTimeoutLogs = it) })
            StormSettingsTextField("Параллельные проверки логов", settings.mtuTestParallelismLogs, updateString { copy(mtuTestParallelismLogs = it) })
        }
    }

    StormSettingsGroup("Туннель", expandedGroup == "tunnel", { expandedGroup = toggleGroup(expandedGroup, "tunnel") }) {
        SettingsFieldGrid {
            StormSettingsTextField("UPLOAD_DUPLICATION", settings.uploadDuplication, updateString { copy(uploadDuplication = it) })
            StormSettingsTextField("DOWNLOAD_DUPLICATION", settings.downloadDuplication, updateString { copy(downloadDuplication = it) })
            StormSettingsTextField("UPLOAD_COMPRESSION", settings.uploadCompression.toString(), { onSettingsChange(settings.copy(uploadCompression = it.toIntOrNull() ?: settings.uploadCompression)) })
            StormSettingsTextField("DOWNLOAD_COMPRESSION", settings.downloadCompression.toString(), { onSettingsChange(settings.copy(downloadCompression = it.toIntOrNull() ?: settings.downloadCompression)) })
            StormSettingsTextField("BALANCING_STRATEGY", settings.balancingStrategy.toString(), { onSettingsChange(settings.copy(balancingStrategy = it.toIntOrNull() ?: settings.balancingStrategy)) })
            StormSettingsTextField("RX_TX_WORKERS", settings.rxTxWorkers, updateString { copy(rxTxWorkers = it) })
            StormSettingsTextField("TUNNEL_WORKERS", settings.tunnelProcessWorkers, updateString { copy(tunnelProcessWorkers = it) })
            StormSettingsTextField("PACKET_TIMEOUT", settings.tunnelPacketTimeoutSeconds, updateString { copy(tunnelPacketTimeoutSeconds = it) })
            StormSettingsTextField("IDLE_POLL_INTERVAL", settings.dispatcherIdlePollIntervalSeconds, updateString { copy(dispatcherIdlePollIntervalSeconds = it) })
        }
        SettingsSwitchRow(
            title = "BASE_ENCODE_DATA",
            subtitle = "Кодировать полезную нагрузку перед отправкой",
            checked = settings.baseEncodeData,
            enabled = customSettingsEnabled,
            onCheckedChange = { onSettingsChange(settings.copy(baseEncodeData = it)) },
        )
    }

    StormSettingsGroup("Очереди", expandedGroup == "queues", { expandedGroup = toggleGroup(expandedGroup, "queues") }) {
        SettingsFieldGrid {
            StormSettingsTextField("TX_CHANNEL_SIZE", settings.txChannelSize, updateString { copy(txChannelSize = it) })
            StormSettingsTextField("RX_CHANNEL_SIZE", settings.rxChannelSize, updateString { copy(rxChannelSize = it) })
            StormSettingsTextField("UDP_POOL_SIZE", settings.resolverUdpConnectionPoolSize, updateString { copy(resolverUdpConnectionPoolSize = it) })
            StormSettingsTextField("STREAM_QUEUE", settings.streamQueueInitialCapacity, updateString { copy(streamQueueInitialCapacity = it) })
            StormSettingsTextField("ORPHAN_QUEUE", settings.orphanQueueInitialCapacity, updateString { copy(orphanQueueInitialCapacity = it) })
            StormSettingsTextField("DNS_FRAGMENT_STORE", settings.dnsResponseFragmentStoreCapacity, updateString { copy(dnsResponseFragmentStoreCapacity = it) })
            StormSettingsTextField("MAX_ACTIVE_STREAMS", settings.maxActiveStreams, updateString { copy(maxActiveStreams = it) })
        }
    }

    StormSettingsGroup("Сессия", expandedGroup == "session", { expandedGroup = toggleGroup(expandedGroup, "session") }) {
        SettingsFieldGrid {
            StormSettingsTextField("HANDSHAKE_TIMEOUT", settings.localHandshakeTimeoutSeconds, updateString { copy(localHandshakeTimeoutSeconds = it) })
            StormSettingsTextField("SOCKS_UDP_TIMEOUT", settings.socksUdpAssociateReadTimeoutSeconds, updateString { copy(socksUdpAssociateReadTimeoutSeconds = it) })
            StormSettingsTextField("TERMINAL_RETENTION", settings.clientTerminalStreamRetentionSeconds, updateString { copy(clientTerminalStreamRetentionSeconds = it) })
            StormSettingsTextField("CANCELLED_RETENTION", settings.clientCancelledSetupRetentionSeconds, updateString { copy(clientCancelledSetupRetentionSeconds = it) })
            StormSettingsTextField("INIT_RETRY_BASE", settings.sessionInitRetryBaseSeconds, updateString { copy(sessionInitRetryBaseSeconds = it) })
            StormSettingsTextField("INIT_RETRY_STEP", settings.sessionInitRetryStepSeconds, updateString { copy(sessionInitRetryStepSeconds = it) })
            StormSettingsTextField("INIT_LINEAR_AFTER", settings.sessionInitRetryLinearAfter, updateString { copy(sessionInitRetryLinearAfter = it) })
            StormSettingsTextField("INIT_RETRY_MAX", settings.sessionInitRetryMaxSeconds, updateString { copy(sessionInitRetryMaxSeconds = it) })
            StormSettingsTextField("BUSY_RETRY_INTERVAL", settings.sessionInitBusyRetryIntervalSeconds, updateString { copy(sessionInitBusyRetryIntervalSeconds = it) })
            StormSettingsTextField("PING_WATCHDOG", settings.pingWatchdogSeconds, updateString { copy(pingWatchdogSeconds = it) })
        }
    }

    StormSettingsGroup("Локальные параметры", expandedGroup == "local", { expandedGroup = toggleGroup(expandedGroup, "local") }) {
        SettingsFieldGrid {
            StormSettingsTextField("LISTEN_IP", settings.listenIp, updateString { copy(listenIp = it) })
            StormSettingsTextField("LISTEN_PORT", settings.listenPort, updateString { copy(listenPort = it) })
            StormSettingsTextField("HTTP_PROXY_PORT", settings.httpProxyPort, updateString { copy(httpProxyPort = it) })
            StormSettingsTextField("LOCAL_DNS_PORT", settings.localDnsPort, updateString { copy(localDnsPort = it) })
            StormSettingsTextField("STARTUP_MODE", settings.startupMode, updateString { copy(startupMode = it) })
            StormSettingsTextField("LOG_LEVEL", settings.logLevel, updateString { copy(logLevel = it) })
        }
        SettingsSwitchRow("HTTP_PROXY_ENABLED", "", settings.httpProxyEnabled, customSettingsEnabled) {
            onSettingsChange(settings.copy(httpProxyEnabled = it))
        }
        SettingsSwitchRow("LOCAL_DNS_ENABLED", "", settings.localDnsEnabled, customSettingsEnabled) {
            onSettingsChange(settings.copy(localDnsEnabled = it))
        }
        SettingsSwitchRow("SOCKS5_AUTH", "", settings.socks5Authentication, customSettingsEnabled) {
            onSettingsChange(settings.copy(socks5Authentication = it))
        }
        SettingsSwitchRow("TRAFFIC_WARMUP", "", settings.trafficWarmupEnabled, customSettingsEnabled) {
            onSettingsChange(settings.copy(trafficWarmupEnabled = it))
        }
        StormSettingsTextField("TRAFFIC_WARMUP_PROBE_COUNT", settings.trafficWarmupProbeCount, updateString { copy(trafficWarmupProbeCount = it) })
        StormSettingsTextField("TRAFFIC_KEEPALIVE_INTERVAL", settings.trafficKeepaliveIntervalSeconds, updateString { copy(trafficKeepaliveIntervalSeconds = it) })
    }
}

private fun toggleGroup(current: String, requested: String): String =
    if (current == requested) "" else requested

@Composable
private fun StormSettingsGroup(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                modifier = Modifier.weight(1f),
                text = title,
                color = Color.White.copy(alpha = 0.82f),
                style = MaterialTheme.typography.bodyLarge,
            )
            Icon(
                imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = if (expanded) "Свернуть" else "Развернуть",
                tint = WhiteZiaTextMuted,
            )
        }
        if (expanded) {
            Column(
                modifier = Modifier.padding(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                content()
            }
        }
        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
    }
}

@Composable
private fun SettingsSectionTitle(text: String) {
    Text(
        text = text.uppercase(Locale.US),
        color = WhiteZiaTextDim,
        style = WhiteZiaSmallTextStyle(),
    )
}

@Composable
private fun SettingsFieldGrid(content: @Composable () -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = { content() },
    )
}

@Composable
private fun SettingsTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean = true,
) {
    var draftValue by rememberSaveable(label) { mutableStateOf(value) }
    var focused by remember { mutableStateOf(false) }

    LaunchedEffect(value, focused) {
        if (!focused && draftValue != value) {
            draftValue = value
        }
    }

    OutlinedTextField(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focusState ->
                focused = focusState.isFocused
                if (!focusState.isFocused && draftValue != value) {
                    draftValue = value
                }
            },
        enabled = enabled,
        value = draftValue,
        onValueChange = { nextValue ->
            draftValue = nextValue
            onValueChange(nextValue)
        },
        label = { Text(label) },
        singleLine = true,
        colors = WhiteZiaTextFieldColors(),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
    )
}

@Composable
private fun WhiteZiaTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White.copy(alpha = 0.88f),
    unfocusedTextColor = Color.White.copy(alpha = 0.78f),
    disabledTextColor = Color.White.copy(alpha = 0.34f),
    focusedLabelColor = WhiteZiaBlue,
    unfocusedLabelColor = WhiteZiaTextMuted,
    disabledLabelColor = WhiteZiaTextDim,
    focusedBorderColor = WhiteZiaBlue,
    unfocusedBorderColor = Color.White.copy(alpha = 0.45f),
    disabledBorderColor = Color.White.copy(alpha = 0.18f),
    cursorColor = WhiteZiaBlue,
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    disabledContainerColor = Color.Transparent,
    focusedPlaceholderColor = WhiteZiaTextDim,
    unfocusedPlaceholderColor = WhiteZiaTextDim,
    disabledPlaceholderColor = WhiteZiaTextDim,
)

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                color = Color.White.copy(alpha = if (enabled) 0.84f else 0.34f),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    color = if (enabled) WhiteZiaTextDim else Color.White.copy(alpha = 0.18f),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            enabled = enabled,
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
private fun ConnectionStatusPanel(
    statusText: String,
    showProgress: Boolean,
    isError: Boolean,
    connectionStatus: ConnectionStatus,
) {
    val progress = when {
        statusText == "Оптимизация подключения" -> 0.85f
        connectionStatus == ConnectionStatus.DISCONNECTED -> 0.15f
        connectionStatus == ConnectionStatus.CONNECTING -> 0.62f
        else -> 1f
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = statusText,
                color = if (isError) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            )
        }
    }
}

@Composable
private fun ActiveResolversPanel(
    resolvers: List<String>,
    runtimeActiveCount: Int,
    runtimeStandbyCount: Int,
    runtimeValidCount: Int,
) {
    if (resolvers.isEmpty()) {
        return
    }
    Spacer(modifier = Modifier.height(8.dp))
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 120.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val runtimeReady = runtimeActiveCount + runtimeStandbyCount + runtimeValidCount > 0
            Text(
                text = if (runtimeReady) {
                    "Активные resolver'ы: $runtimeActiveCount, standby: $runtimeStandbyCount, valid: $runtimeValidCount"
                } else {
                    "Resolver'ы для подключения: ${resolvers.size}"
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
            )
            resolvers.forEachIndexed { index, resolver ->
                Text(
                    text = "${index + 1}. $resolver",
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.86f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ConnectionLogPanel(visibleLog: String) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 96.dp, max = 180.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 1.dp,
    ) {
        Text(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .verticalScroll(rememberScrollState()),
            text = visibleLog.ifBlank { "Готов к подключению" },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun SplitTunnelControls(
    settings: WhiteZiaSettings,
    enabled: Boolean,
    onAppsClick: () -> Unit,
) {
    TextButton(
        enabled = enabled,
        onClick = onAppsClick,
    ) {
        Icon(imageVector = Icons.Rounded.Apps, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text(splitTunnelSummary(settings))
    }
}

@Composable
private fun SplitTunnelDialog(
    settings: WhiteZiaSettings,
    onDismiss: () -> Unit,
    onSettingsChange: (WhiteZiaSettings) -> Unit,
) {
    val context = LocalContext.current
    val apps = remember { loadSplitTunnelAppOptions(context) }
    var selectedMode by remember(settings.splitTunnelMode) {
        mutableStateOf(settings.splitTunnelMode)
    }
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
                SplitTunnelModeOptions(
                    selectedMode = selectedMode,
                    onSelectedModeChange = { selectedMode = it },
                )
                if (selectedMode != WhiteZiaOptions.SplitTunnelModeOff) {
                    Spacer(modifier = Modifier.height(12.dp))
                    apps.forEach { app ->
                        val checked = app.packageName in selectedPackages
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedPackages = if (checked) {
                                        selectedPackages - app.packageName
                                    } else {
                                        selectedPackages + app.packageName
                                    }
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = {
                                    selectedPackages = if (checked) {
                                        selectedPackages - app.packageName
                                    } else {
                                        selectedPackages + app.packageName
                                    }
                                },
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = app.label,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
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
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun SplitTunnelModeOptions(
    selectedMode: String,
    onSelectedModeChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SplitTunnelModeOption(
            label = "All apps",
            selected = selectedMode == WhiteZiaOptions.SplitTunnelModeOff,
            onClick = { onSelectedModeChange(WhiteZiaOptions.SplitTunnelModeOff) },
        )
        SplitTunnelModeOption(
            label = "Only selected apps",
            selected = selectedMode == WhiteZiaOptions.SplitTunnelModeInclude,
            onClick = { onSelectedModeChange(WhiteZiaOptions.SplitTunnelModeInclude) },
        )
        SplitTunnelModeOption(
            label = "Bypass selected apps",
            selected = selectedMode == WhiteZiaOptions.SplitTunnelModeExclude,
            onClick = { onSelectedModeChange(WhiteZiaOptions.SplitTunnelModeExclude) },
        )
    }
}

@Composable
private fun SplitTunnelModeOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        modifier = Modifier.fillMaxWidth(),
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
    )
}

private fun splitTunnelSummary(settings: WhiteZiaSettings): String {
    return when (settings.splitTunnelMode) {
        WhiteZiaOptions.SplitTunnelModeInclude -> "Split tunnel: only ${settings.splitTunnelPackages.size} app(s)"
        WhiteZiaOptions.SplitTunnelModeExclude -> "Split tunnel: bypass ${settings.splitTunnelPackages.size} app(s)"
        else -> "Split tunnel: all apps"
    }
}

private fun operatorLabel(operatorCode: String): String {
    return when (operatorCode) {
        WhiteZiaOptions.OperatorMts -> "МТС"
        WhiteZiaOptions.OperatorBeeline -> "Билайн"
        WhiteZiaOptions.OperatorTele2 -> "Tele2"
        else -> "Мегафон/Йота"
    }
}

private fun formatMbps(bytesPerSecond: Long): String {
    return if (bytesPerSecond <= 0L) {
        "0 Мбит/с"
    } else {
        "${"%.2f".format(Locale.US, bytesPerSecond * 8.0 / 1_000_000.0)} Мбит/с"
    }
}

private fun networkTransportLabel(transport: String): String {
    return when (transport) {
        NetworkTransportWifi -> "Wi-Fi"
        NetworkTransportMobile -> "мобильная сеть"
        NetworkTransportOther -> "другая сеть"
        else -> "нет сети"
    }
}

private val TMobileOperatorMarkers = listOf("t-mobile", "tmobile")
private val WhiteZiaBackground = Color(0xFF0F0F14)
private val WhiteZiaPanel = Color(0xFF16161F)
private val WhiteZiaBlue = Color(0xFF5B6AF0)
private val WhiteZiaRed = Color(0xFFE53935)
private val WhiteZiaSuccess = Color(0xFF00C9A7)
private val WhiteZiaError = Color(0xFFFF4D4D)
private val WhiteZiaSetupOrange = Color(0xFFFFA726)
private val WhiteZiaTextMuted = Color.White.copy(alpha = 0.55f)
private val WhiteZiaTextDim = Color.White.copy(alpha = 0.22f)
private val MtsOperatorMarkers = listOf("mts", "мтс", "25001")
private val BeelineOperatorMarkers = listOf("beeline", "билайн", "vimpelcom", "вымпелком", "25099")
private val Tele2OperatorMarkers = listOf("tele2", "теле2", "t2", "25020")
private val MegafonYotaOperatorMarkers = listOf("megafon", "мегафон", "yota", "йота", "25002", "25011")
private const val PermissionActionNone = ""
private const val PermissionActionConnectNow = "connect_now"
private const val WifiStateSettleDelayMillis = 250L
private const val DefaultNetworkSettleDelayMillis = 600L
private const val NetworkSwitchReconnectDelayMillis = 1_000L
private const val FallbackTransportRestartDelayMillis = 3_000L
private const val ResolverBenchmarkReconnectDelayMillis = 3_000L
private const val ResolverBenchmarkSwitchSettleDelayMillis = 3_000L
private const val AutoResolverBenchmarkAfterConnect = true
private const val NetworkTransportNone = "none"
private const val NetworkTransportWifi = "wifi"
private const val NetworkTransportMobile = "mobile"
private const val NetworkTransportOther = "other"
private const val WhiteZiaVisibleLogTailLimit = 10
private const val WhiteZiaFullVisibleLogLimit = 300

private fun buildVisibleLog(
    localLog: String,
    runtimeLogs: List<String>,
): String {
    val runtimeLines = runtimeLogs
        .map(String::trim)
        .filter(String::isNotBlank)
        .filter { it != "Idle" }
    val localLines = localLog
        .lineSequence()
        .map(String::trim)
        .filter(String::isNotBlank)
        .toList()
    val lines = if (runtimeLines.isNotEmpty()) {
        runtimeLines
    } else {
        localLines
    }
    return lines
        .fold(mutableListOf<String>()) { acc, line ->
            if (acc.lastOrNull() != line) {
                acc += line
            }
            acc
        }
        .joinToString(separator = "\n")
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
