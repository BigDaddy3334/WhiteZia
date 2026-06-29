package shop.whitezia.client

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.activity.ComponentActivity
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Article
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Sync
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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import shop.whitezia.client.model.ConnectionStatus
import shop.whitezia.client.model.ResolverRuntimeState
import shop.whitezia.client.model.WhiteZiaOptions
import shop.whitezia.client.model.WhiteZiaSettings
import shop.whitezia.client.model.WhiteZiaThemeMode
import shop.whitezia.client.model.validateResolverText
import shop.whitezia.client.ui.ResolverBenchmarkScore
import shop.whitezia.client.ui.WhiteZiaTheme
import shop.whitezia.client.ui.WhiteZiaViewModel

class MainActivity : ComponentActivity() {

    private val viewModel by viewModels<WhiteZiaViewModel>()
    private var wifiStateCallback: ConnectivityManager.NetworkCallback? = null
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
                var subscriptionLink by rememberSaveable { mutableStateOf(initialProfileLink) }
                var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
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
                var userStatus by rememberSaveable { mutableStateOf("производится первичная настройка") }
                var operatorDisplayLabel by rememberSaveable {
                    mutableStateOf(operatorLabel(viewModel.uiState.settings.operatorCode))
                }
                var wifiEnabled by rememberSaveable { mutableStateOf(isWifiNetworkAvailable()) }
                var activeBaseNetworkTransport by rememberSaveable { mutableStateOf(currentBaseNetworkTransport()) }
                var lastNetworkReconnectTransport by rememberSaveable {
                    mutableStateOf(activeBaseNetworkTransport)
                }
                var showSplitTunnelDialog by rememberSaveable { mutableStateOf(false) }
                var showSubscriptionDialog by rememberSaveable { mutableStateOf(false) }
                var showSettingsDialog by rememberSaveable { mutableStateOf(false) }
                var showLogDialog by rememberSaveable { mutableStateOf(false) }
                var postCheckAttempt by rememberSaveable { mutableStateOf(0) }
                var completedPostCheckAttempt by rememberSaveable { mutableStateOf(0) }
                var connectionLaunchStarted by rememberSaveable { mutableStateOf(false) }
                var connectionWanted by rememberSaveable {
                    mutableStateOf(viewModel.uiState.connectionStatus != ConnectionStatus.DISCONNECTED)
                }
                var disconnectingByUser by rememberSaveable { mutableStateOf(false) }
                var resolverScanOperator by rememberSaveable { mutableStateOf("") }
                var resolverBenchmarkPhase by rememberSaveable { mutableStateOf("") }
                var resolverBenchmarkLocalText by rememberSaveable { mutableStateOf("") }
                var resolverBenchmarkLocalSpeed by rememberSaveable { mutableStateOf(0L) }
                var resolverBenchmarkLocalScore by remember { mutableStateOf<ResolverBenchmarkScore?>(null) }
                var resolverBenchmarkReconnectJob by remember { mutableStateOf<Job?>(null) }
                var networkReconnectJob by remember { mutableStateOf<Job?>(null) }
                var pendingNetworkReconnectTransport by rememberSaveable { mutableStateOf("") }
                var pendingStormDnsAfterWifiOff by rememberSaveable { mutableStateOf(false) }
                var pendingStormDnsAfterResolverScan by rememberSaveable { mutableStateOf(false) }
                var pendingAmneziaFallback by rememberSaveable { mutableStateOf(false) }
                var pendingDnsFallbackAfterAmnezia by rememberSaveable { mutableStateOf(false) }
                var resolverFallbackYandexAllowed by rememberSaveable { mutableStateOf(false) }
                var resolverScanKick by rememberSaveable { mutableStateOf(0) }
                var resolverFallbackConnectKick by rememberSaveable { mutableStateOf(0) }
                var pendingActionAfterNotificationPermission by rememberSaveable {
                    mutableStateOf(PermissionActionNone)
                }
                var pendingActionAfterVpnPermission by rememberSaveable { mutableStateOf(PermissionActionNone) }

                val appendFullVisibleLog: (String) -> Unit = { message ->
                    val cleanMessage = message.trim()
                    if (cleanMessage.isNotEmpty()) {
                        fullVisibleLog = (fullVisibleLog.lineSequence().toList() + cleanMessage)
                            .takeLast(WhiteZiaFullVisibleLogLimit)
                            .joinToString(separator = "\n")
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
                var lastSimDetectionLogKey by rememberSaveable { mutableStateOf("") }

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
                    }
                }

                LaunchedEffect(Unit) {
                    refreshDetectedOperator(
                        preferNetworkOperator = activeBaseNetworkTransport == NetworkTransportMobile || !wifiEnabled,
                        reason = "startup",
                    )
                }

                LaunchedEffect(Unit) {
                    observeWifiState { enabled ->
                        wifiEnabled = enabled
                        if (enabled) {
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
                    wifiEnabled,
                    activeBaseNetworkTransport,
                    resolverScanKick,
                ) {
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
                        resolverFallbackYandexAllowed = false
                        if (!hasCachedResolvers) {
                            errorMessage = discoveryError
                            userStatus = "Не удалось выполнить первичную настройку"
                        }
                        addVisibleLog(discoveryError)
                    } else {
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

                fun runPermissionAction(action: String) {
                    when (action) {
                        PermissionActionConnectNow -> {
                            connectionLaunchStarted = true
                            viewModel.beginConnection()
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

                val notificationPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission(),
                ) { granted ->
                    viewModel.refreshNotificationStatus()
                    val action = pendingActionAfterNotificationPermission
                    pendingActionAfterNotificationPermission = PermissionActionNone
                    if (action == PermissionActionNone) {
                        return@rememberLauncherForActivityResult
                    }
                    if (granted) {
                        addVisibleLog("Разрешение уведомлений получено")
                        requestVpnPermission(action)
                    } else {
                        errorMessage = "Notification permission is required for the VPN service"
                        addVisibleLog("Ошибка: разрешение уведомлений не выдано")
                    }
                }

                val requestPermissionsThen: (String) -> Unit = { action ->
                    if (
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.POST_NOTIFICATIONS,
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        addVisibleLog("Запрашиваю разрешение уведомлений заранее")
                        pendingActionAfterNotificationPermission = action
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        requestVpnPermission(action)
                    }
                }

                fun restartForResolverBenchmark() {
                    resolverBenchmarkReconnectJob?.cancel()
                    networkReconnectJob?.cancel()
                    resolverBenchmarkReconnectJob = lifecycleScope.launch {
                        viewModel.disconnect()
                        delay(2_500)
                        postCheckAttempt += 1
                        connectionLaunchStarted = true
                        viewModel.beginConnection()
                    }
                }

                val beginStormDnsFallbackConnection = {
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

                val beginPreparedConnection = beginPreparedConnection@{
                    val activeOperatorCode = refreshDetectedOperator(
                        preferNetworkOperator = activeBaseNetworkTransport == NetworkTransportMobile || !wifiEnabled,
                        reason = "connect",
                    ) ?: viewModel.uiState.settings.operatorCode
                    connectionWanted = true
                    disconnectingByUser = false
                    errorMessage = null
                    pendingStormDnsAfterWifiOff = false
                    pendingStormDnsAfterResolverScan = false
                    pendingAmneziaFallback = false
                    pendingDnsFallbackAfterAmnezia = false
                    resolverFallbackYandexAllowed = false
                    resolverBenchmarkReconnectJob?.cancel()
                    networkReconnectJob?.cancel()
                    viewModel.resetConnectionLog("Новая попытка подключения")
                    setVisibleLog("Connect нажата")
                    val trimmedLink = subscriptionLink.trim()
                    if (viewModel.uiState.settings.forceDnsTunnel) {
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
                        errorMessage = preparationError
                        userStatus = "Ошибка подключения. Повторите попытку"
                        addVisibleLog(preparationError)
                    } else if (viewModel.uiState.settings.amneziaWgConfig.isBlank()) {
                        addVisibleLog("В подписке нет AmneziaWG, запускаю DNS канал")
                        if (isStormDnsBlockedByWifi()) {
                            pendingStormDnsAfterWifiOff = true
                            errorMessage = "Выключите Wi-Fi"
                            userStatus = "Выключите Wi-Fi"
                        } else {
                            beginStormDnsFallbackConnection()
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
                        pendingDnsFallbackAfterAmnezia = false
                        resolverFallbackYandexAllowed = false
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
                    if (transportChanged && meaningfulSwitch && connectionWanted) {
                        pendingNetworkReconnectTransport = currentTransport
                    } else if (!connectionWanted || previousTransport == NetworkTransportNone) {
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
                ) {
                    if (!pendingStormDnsAfterWifiOff || isStormDnsBlockedByWifi()) {
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
                ) {
                    if (
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
                    pendingDnsFallbackAfterAmnezia,
                    viewModel.uiState.connectionStatus,
                    wifiEnabled,
                ) {
                    if (
                        !pendingDnsFallbackAfterAmnezia ||
                        viewModel.uiState.connectionStatus != ConnectionStatus.DISCONNECTED
                    ) {
                        return@LaunchedEffect
                    }
                    userStatus = "Подготовка DNS подключения"
                    errorMessage = null
                    delay(2_000)
                    if (isStormDnsBlockedByWifi()) {
                        pendingDnsFallbackAfterAmnezia = false
                        pendingStormDnsAfterWifiOff = true
                        userStatus = "Выключите Wi-Fi"
                        errorMessage = "Выключите Wi-Fi"
                        addVisibleLog("Для StormDNS отключите Wi-Fi")
                    } else {
                        pendingDnsFallbackAfterAmnezia = false
                        beginStormDnsFallbackConnection()
                    }
                }

                LaunchedEffect(viewModel.uiState.connectionStatus, postCheckAttempt) {
                    if (viewModel.uiState.connectionStatus == ConnectionStatus.CONNECTED) {
                        connectionWanted = true
                        disconnectingByUser = false
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
                            addVisibleLog("AmneziaWG недоступен, переключаюсь на DNS канал")
                            if (isStormDnsBlockedByWifi()) {
                                pendingStormDnsAfterWifiOff = true
                                userStatus = "Выключите Wi-Fi"
                                errorMessage = "Выключите Wi-Fi"
                            } else {
                                beginStormDnsFallbackConnection()
                            }
                        } else {
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
	                        val usingStormDnsTransport =
	                            viewModel.uiState.settings.transportMode == WhiteZiaOptions.TransportDns ||
	                                viewModel.uiState.settings.amneziaWgConfig.isBlank()
	                        userStatus = if (usingStormDnsTransport) {
	                            "Проверка подключения"
	                        } else {
	                            "Проверка AmneziaWG"
	                        }
                        val ok = if (usingStormDnsTransport) {
                            viewModel.runDnsPostConnectionCheck(addVisibleLog)
                        } else {
                            viewModel.runAmneziaPostConnectionCheck(addVisibleLog)
                        }
                        if (!ok && !usingStormDnsTransport) {
                            pendingAmneziaFallback = false
                            pendingDnsFallbackAfterAmnezia = true
                            addVisibleLog("AmneziaWG поднялся, но интернет не проходит. Переключаюсь на DNS канал")
                            userStatus = "Подготовка DNS подключения"
                            errorMessage = null
                            viewModel.disconnect()
                            return@LaunchedEffect
                        }
                        if (!ok && usingStormDnsTransport && resolverBenchmarkPhase != "postcheck_yandex") {
                            val currentResolvers = viewModel.currentResolverEntries()
                            if (currentResolvers != viewModel.yandexResolverEntries()) {
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
                            pendingDnsFallbackAfterAmnezia = false
                            resolverFallbackYandexAllowed = false
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
                                    viewModel.cacheResolverBenchmarkWinner(
                                        localResolvers = resolverBenchmarkLocalText
                                            .lineSequence()
                                            .map(String::trim)
                                            .filter(String::isNotEmpty)
                                            .toList(),
                                        winnerId = "yandex",
                                        winnerResolvers = viewModel.yandexResolverEntries(),
                                        onLog = addVisibleLog,
                                    )
                                    addVisibleLog("Yandex resolver set прошел Cloudflare-check")
                                }
                                "" -> {
                                    if (viewModel.shouldRunResolverBenchmark()) {
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
                                        viewModel.applyResolverEntriesForReconnect(viewModel.yandexResolverEntries())
                                        userStatus = "Оптимизация подключения"
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
                                    val yandexWins = viewModel.shouldPreferYandexResolverScore(
                                        local = localScore,
                                        yandex = yandexScore,
                                        onLog = addVisibleLog,
                                    )
                                    if (yandexWins) {
                                        resolverBenchmarkPhase = "done"
                                        resolverBenchmarkLocalScore = null
                                        viewModel.cacheResolverBenchmarkWinner(
                                            localResolvers = localResolvers,
                                            winnerId = "yandex",
                                            winnerResolvers = viewModel.yandexResolverEntries(),
                                            onLog = addVisibleLog,
                                        )
                                        userStatus = "Подключение успешно"
                                        addVisibleLog("Выбран Yandex resolver set")
                                    } else {
                                        resolverBenchmarkPhase = "applying_local_winner"
                                        resolverBenchmarkLocalScore = null
                                        viewModel.cacheResolverBenchmarkWinner(
                                            localResolvers = localResolvers,
                                            winnerId = "local",
                                            winnerResolvers = localResolvers,
                                            onLog = addVisibleLog,
                                        )
                                        viewModel.applyResolverEntriesForReconnect(localResolvers)
                                        userStatus = "Оптимизация подключения"
                                        addVisibleLog("Выбран local resolver set, переподключаюсь")
                                        restartForResolverBenchmark()
                                        return@LaunchedEffect
                                    }
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
                    onConnectClick = {
                        val clickLockedByAutomaticFlow = disconnectingByUser ||
                            pendingStormDnsAfterWifiOff ||
                            pendingStormDnsAfterResolverScan ||
                            pendingAmneziaFallback ||
                            pendingDnsFallbackAfterAmnezia ||
                            resolverBenchmarkReconnectJob?.isActive == true ||
                            networkReconnectJob?.isActive == true ||
                            viewModel.uiState.connectionStatus == ConnectionStatus.CONNECTING ||
                            userStatus == "производится первичная настройка" ||
                            userStatus == "Подключение" ||
                            userStatus == "Подключение через AmneziaWG" ||
                            userStatus == "Проверка AmneziaWG" ||
                            userStatus == "Проверка подключения" ||
                            userStatus == "Подготовка DNS подключения" ||
                            userStatus == "Оптимизация подключения"
                        if (clickLockedByAutomaticFlow) {
                            addVisibleLog("Автоматическое подключение еще выполняется")
                        } else {
                            when (viewModel.uiState.connectionStatus) {
                                ConnectionStatus.DISCONNECTED -> beginPreparedConnection()
                                ConnectionStatus.CONNECTING,
                                ConnectionStatus.CONNECTED -> {
                                    connectionWanted = false
                                    disconnectingByUser = true
                                    pendingNetworkReconnectTransport = ""
                                    resolverBenchmarkReconnectJob?.cancel()
                                    networkReconnectJob?.cancel()
                                    pendingStormDnsAfterWifiOff = false
                                    pendingStormDnsAfterResolverScan = false
                                    pendingAmneziaFallback = false
                                    resolverFallbackYandexAllowed = false
                                    errorMessage = null
                                    connectionLaunchStarted = false
                                    userStatus = "Отключено"
                                    addVisibleLog("Отключение")
                                    viewModel.disconnect()
                                }
                            }
                        }
                    },
                    onForceDnsTunnelChange = { enabled ->
                        viewModel.setForceDnsTunnel(enabled)
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
                        onOpenSplitTunnelApps = { showSplitTunnelDialog = true },
                        onScanSubscription = {
                            subscriptionQrScanner.launch(Intent(context, QrScannerActivity::class.java))
                        },
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

    private fun observeWifiState(onChange: (Boolean) -> Unit) {
        val connectivityManager = getSystemService(ConnectivityManager::class.java)
        var lastPublishedState: Boolean? = null
        fun publish(delayMillis: Long = 0L) {
            lifecycleScope.launch {
                if (delayMillis > 0L) {
                    delay(delayMillis)
                }
                val wifiAvailable = isWifiNetworkAvailable(connectivityManager)
                if (lastPublishedState == wifiAvailable) {
                    return@launch
                }
                lastPublishedState = wifiAvailable
                onChange(wifiAvailable)
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
    }

    private fun unregisterWifiStateCallback() {
        val callback = wifiStateCallback
        wifiStateCallback = null
        if (callback != null) {
            runCatching {
                getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(callback)
            }
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
    val subscriptionValues = readSubscriptionOperatorValues(context).normalizedOperatorValues()
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

private fun readSubscriptionOperatorValues(context: Context): List<String> {
    if (
        Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED
    ) {
        return emptyList()
    }
    return runCatching {
        val subscriptionManager = context.getSystemService(SubscriptionManager::class.java)
            ?: return@runCatching emptyList<String>()
        val subscriptions = subscriptionManager.activeSubscriptionInfoList.orEmpty()
        if (subscriptions.isEmpty()) {
            return@runCatching emptyList<String>()
        }
        val defaultDataSubId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            SubscriptionManager.getDefaultDataSubscriptionId()
        } else {
            SubscriptionManager.INVALID_SUBSCRIPTION_ID
        }
        val primarySubscription = subscriptions.firstOrNull { it.subscriptionId == defaultDataSubId }
            ?: subscriptions.first()
        buildList {
            add(primarySubscription.carrierName?.toString().orEmpty())
            add(primarySubscription.displayName?.toString().orEmpty())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(primarySubscription.mccString.orEmpty() + primarySubscription.mncString.orEmpty())
            } else {
                add("${primarySubscription.mcc}${primarySubscription.mnc}")
            }
        }
    }.getOrDefault(emptyList())
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
    onConnectClick: () -> Unit,
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
        userStatus == "Проверка AmneziaWG" ||
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
        userStatus == "Проверка AmneziaWG" ||
        userStatus == "Проверка подключения" ||
        userStatus == "Оптимизация подключения"
    val canConnect = !isRunning && subscriptionLink.trim().isNotEmpty() && !isAutomaticConnectionFlow
    val canDisconnect = connectionStatus == ConnectionStatus.CONNECTED && !isAutomaticConnectionFlow && errorMessage == null
    val canChangeDnsMode = !isRunning && !isAutomaticConnectionFlow
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
        isDisconnecting -> "ОТКЛЮЧЕНИЕ"
        wifiEnabled && errorMessage == "Выключите Wi-Fi" -> "Выключите Wi-Fi"
        isPrimarySetup -> "производится первичная настройка"
        connectionStatus == ConnectionStatus.CONNECTING -> "Подключение"
        userStatus == "Оптимизация подключения" -> "Оптимизация подключения.\nПожалуйста подождите, это может занять до двух минут"
        connectionStatus == ConnectionStatus.CONNECTED -> userStatus.ifBlank { "Подключение успешно" }
        errorMessage != null -> "Не удалось подключиться. Повторите попытку"
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
                            enabled = !isRunning && !isAutomaticConnectionFlow,
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
                Text(
                    text = "Оператор SIM: $operatorDisplayLabel".uppercase(Locale.US),
                    style = WhiteZiaSmallTextStyle(),
                    color = WhiteZiaTextDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(18.dp))
                Text(
                    text = statusText.uppercase(Locale.US),
                    style = WhiteZiaStatusTextStyle(),
                    color = when {
                        errorMessage != null -> WhiteZiaError
                        isPrimarySetup -> WhiteZiaSetupOrange
                        showProgress -> WhiteZiaBlue
                        connectionStatus == ConnectionStatus.CONNECTED -> WhiteZiaSuccess
                        statusText == "Готово к подключению" -> WhiteZiaSuccess
                        else -> WhiteZiaTextMuted
                    },
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(18.dp))
                ForceDnsTunnelSwitch(
                    enabled = forceDnsTunnel,
                    interactiveEnabled = canChangeDnsMode,
                    wifiEnabled = wifiEnabled,
                    onToggle = { onForceDnsTunnelChange(!forceDnsTunnel) },
                )
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
                    onClick = onConnectClick,
                )
            }
        }
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
        isFinalizing -> "ПОДКЛЮЧЕНИЕ"
        connectionStatus == ConnectionStatus.CONNECTED -> "ПОДКЛЮЧЕНО"
        connectionStatus == ConnectionStatus.CONNECTING -> "ПОДКЛЮЧЕНИЕ"
        else -> "ПОДКЛЮЧИТЬСЯ"
    }
    val buttonIcon = when {
        isError -> Icons.Rounded.Close
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
                repeat(56) { step ->
                    displayedProgress = ((step + 1) / 56f).coerceAtMost(0.96f)
                    delay(50)
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
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Закрыть")
            }
        },
    )
}

@Composable
private fun WhiteZiaSettingsDialog(
    settings: WhiteZiaSettings,
    subscriptionLink: String,
    onDismiss: () -> Unit,
    onOpenSplitTunnelApps: () -> Unit,
    onScanSubscription: () -> Unit,
    onSave: (WhiteZiaSettings, String) -> Unit,
) {
    var tabIndex by rememberSaveable { mutableStateOf(0) }
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
    val tabs = listOf("Подписка", "DNS", "Storm")

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = WhiteZiaPanel,
        titleContentColor = Color.White,
        textContentColor = WhiteZiaTextMuted,
        title = {
            WhiteZiaLogo()
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp),
            ) {
                TabRow(
                    selectedTabIndex = tabIndex,
                    containerColor = WhiteZiaPanel,
                    contentColor = WhiteZiaBlue,
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = tabIndex == index,
                            onClick = { tabIndex = index },
                            text = {
                                Text(
                                    text = title.uppercase(Locale.US),
                                    style = WhiteZiaTabTextStyle(),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    when (tabIndex) {
                        0 -> SubscriptionSettingsTab(
                            subscriptionLink = draftSubscription,
                            settings = draftSettings,
                            onSubscriptionChange = { draftSubscription = it },
                            onScanSubscription = onScanSubscription,
                            onOpenSplitTunnelApps = onOpenSplitTunnelApps,
                        )
                        1 -> ResolverSettingsTab(
                            settings = draftSettings,
                            validation = resolverValidation,
                            onSettingsChange = { draftSettings = it },
                        )
                        else -> StormDnsAdvancedSettingsTab(
                            settings = draftSettings,
                            onSettingsChange = { draftSettings = it },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = customResolversValid,
                onClick = {
                    val normalizedResolvers = resolverValidation.normalizedText
                    val updatedSettings = if (draftSettings.customResolversEnabled) {
                        draftSettings.copy(
                            customResolverText = normalizedResolvers,
                            resolverText = normalizedResolvers,
                            selectedResolverProfileId = "",
                        )
                    } else {
                        draftSettings
                    }
                    onSave(updatedSettings, draftSubscription)
                },
            ) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        },
    )
}

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
                ),
            )
        },
    )
    OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        enabled = settings.customResolversEnabled,
        value = settings.customResolverText.ifBlank { settings.resolverText },
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
private fun StormDnsAdvancedSettingsTab(
    settings: WhiteZiaSettings,
    onSettingsChange: (WhiteZiaSettings) -> Unit,
) {
    fun updateString(update: WhiteZiaSettings.(String) -> WhiteZiaSettings): (String) -> Unit {
        return { value -> onSettingsChange(settings.update(value)) }
    }

    SettingsSwitchRow(
        title = "Кастомные настройки",
        subtitle = "Не перезаписывать MTU, duplication, workers и timeout при подключении",
        checked = settings.customConnectionSettingsEnabled,
        onCheckedChange = { onSettingsChange(settings.copy(customConnectionSettingsEnabled = it)) },
    )
    SettingsSectionTitle("MTU")
    SettingsFieldGrid {
        SettingsTextField("MIN_UPLOAD_MTU", settings.minUploadMtu, updateString { copy(minUploadMtu = it) })
        SettingsTextField("MAX_UPLOAD_MTU", settings.maxUploadMtu, updateString { copy(maxUploadMtu = it) })
        SettingsTextField("MIN_DOWNLOAD_MTU", settings.minDownloadMtu, updateString { copy(minDownloadMtu = it) })
        SettingsTextField("MAX_DOWNLOAD_MTU", settings.maxDownloadMtu, updateString { copy(maxDownloadMtu = it) })
        SettingsTextField("MTU_RETRIES_RESOLVERS", settings.mtuTestRetriesResolvers, updateString { copy(mtuTestRetriesResolvers = it) })
        SettingsTextField("MTU_TIMEOUT_RESOLVERS", settings.mtuTestTimeoutResolvers, updateString { copy(mtuTestTimeoutResolvers = it) })
        SettingsTextField("MTU_PARALLELISM_RESOLVERS", settings.mtuTestParallelismResolvers, updateString { copy(mtuTestParallelismResolvers = it) })
        SettingsTextField("MTU_RETRIES_LOGS", settings.mtuTestRetriesLogs, updateString { copy(mtuTestRetriesLogs = it) })
        SettingsTextField("MTU_TIMEOUT_LOGS", settings.mtuTestTimeoutLogs, updateString { copy(mtuTestTimeoutLogs = it) })
        SettingsTextField("MTU_PARALLELISM_LOGS", settings.mtuTestParallelismLogs, updateString { copy(mtuTestParallelismLogs = it) })
    }

    SettingsSectionTitle("Tunnel")
    SettingsFieldGrid {
        SettingsTextField("UPLOAD_DUPLICATION", settings.uploadDuplication, updateString { copy(uploadDuplication = it) })
        SettingsTextField("DOWNLOAD_DUPLICATION", settings.downloadDuplication, updateString { copy(downloadDuplication = it) })
        SettingsTextField("UPLOAD_COMPRESSION", settings.uploadCompression.toString(), { onSettingsChange(settings.copy(uploadCompression = it.toIntOrNull() ?: settings.uploadCompression)) })
        SettingsTextField("DOWNLOAD_COMPRESSION", settings.downloadCompression.toString(), { onSettingsChange(settings.copy(downloadCompression = it.toIntOrNull() ?: settings.downloadCompression)) })
        SettingsTextField("BALANCING_STRATEGY", settings.balancingStrategy.toString(), { onSettingsChange(settings.copy(balancingStrategy = it.toIntOrNull() ?: settings.balancingStrategy)) })
        SettingsTextField("RX_TX_WORKERS", settings.rxTxWorkers, updateString { copy(rxTxWorkers = it) })
        SettingsTextField("TUNNEL_WORKERS", settings.tunnelProcessWorkers, updateString { copy(tunnelProcessWorkers = it) })
        SettingsTextField("PACKET_TIMEOUT", settings.tunnelPacketTimeoutSeconds, updateString { copy(tunnelPacketTimeoutSeconds = it) })
        SettingsTextField("IDLE_POLL_INTERVAL", settings.dispatcherIdlePollIntervalSeconds, updateString { copy(dispatcherIdlePollIntervalSeconds = it) })
    }
    SettingsSwitchRow(
        title = "BASE_ENCODE_DATA",
        subtitle = "Кодировать полезную нагрузку перед отправкой",
        checked = settings.baseEncodeData,
        onCheckedChange = { onSettingsChange(settings.copy(baseEncodeData = it)) },
    )

    SettingsSectionTitle("Queues")
    SettingsFieldGrid {
        SettingsTextField("TX_CHANNEL_SIZE", settings.txChannelSize, updateString { copy(txChannelSize = it) })
        SettingsTextField("RX_CHANNEL_SIZE", settings.rxChannelSize, updateString { copy(rxChannelSize = it) })
        SettingsTextField("UDP_POOL_SIZE", settings.resolverUdpConnectionPoolSize, updateString { copy(resolverUdpConnectionPoolSize = it) })
        SettingsTextField("STREAM_QUEUE", settings.streamQueueInitialCapacity, updateString { copy(streamQueueInitialCapacity = it) })
        SettingsTextField("ORPHAN_QUEUE", settings.orphanQueueInitialCapacity, updateString { copy(orphanQueueInitialCapacity = it) })
        SettingsTextField("DNS_FRAGMENT_STORE", settings.dnsResponseFragmentStoreCapacity, updateString { copy(dnsResponseFragmentStoreCapacity = it) })
        SettingsTextField("MAX_ACTIVE_STREAMS", settings.maxActiveStreams, updateString { copy(maxActiveStreams = it) })
    }

    SettingsSectionTitle("Session")
    SettingsFieldGrid {
        SettingsTextField("HANDSHAKE_TIMEOUT", settings.localHandshakeTimeoutSeconds, updateString { copy(localHandshakeTimeoutSeconds = it) })
        SettingsTextField("SOCKS_UDP_TIMEOUT", settings.socksUdpAssociateReadTimeoutSeconds, updateString { copy(socksUdpAssociateReadTimeoutSeconds = it) })
        SettingsTextField("TERMINAL_RETENTION", settings.clientTerminalStreamRetentionSeconds, updateString { copy(clientTerminalStreamRetentionSeconds = it) })
        SettingsTextField("CANCELLED_RETENTION", settings.clientCancelledSetupRetentionSeconds, updateString { copy(clientCancelledSetupRetentionSeconds = it) })
        SettingsTextField("INIT_RETRY_BASE", settings.sessionInitRetryBaseSeconds, updateString { copy(sessionInitRetryBaseSeconds = it) })
        SettingsTextField("INIT_RETRY_STEP", settings.sessionInitRetryStepSeconds, updateString { copy(sessionInitRetryStepSeconds = it) })
        SettingsTextField("INIT_LINEAR_AFTER", settings.sessionInitRetryLinearAfter, updateString { copy(sessionInitRetryLinearAfter = it) })
        SettingsTextField("INIT_RETRY_MAX", settings.sessionInitRetryMaxSeconds, updateString { copy(sessionInitRetryMaxSeconds = it) })
        SettingsTextField("BUSY_RETRY_INTERVAL", settings.sessionInitBusyRetryIntervalSeconds, updateString { copy(sessionInitBusyRetryIntervalSeconds = it) })
        SettingsTextField("PING_WATCHDOG", settings.pingWatchdogSeconds, updateString { copy(pingWatchdogSeconds = it) })
    }

    SettingsSectionTitle("Local")
    SettingsFieldGrid {
        SettingsTextField("LISTEN_IP", settings.listenIp, updateString { copy(listenIp = it) })
        SettingsTextField("LISTEN_PORT", settings.listenPort, updateString { copy(listenPort = it) })
        SettingsTextField("HTTP_PROXY_PORT", settings.httpProxyPort, updateString { copy(httpProxyPort = it) })
        SettingsTextField("LOCAL_DNS_PORT", settings.localDnsPort, updateString { copy(localDnsPort = it) })
        SettingsTextField("STARTUP_MODE", settings.startupMode, updateString { copy(startupMode = it) })
        SettingsTextField("LOG_LEVEL", settings.logLevel, updateString { copy(logLevel = it) })
    }
    SettingsSwitchRow("HTTP_PROXY_ENABLED", "", settings.httpProxyEnabled) {
        onSettingsChange(settings.copy(httpProxyEnabled = it))
    }
    SettingsSwitchRow("LOCAL_DNS_ENABLED", "", settings.localDnsEnabled) {
        onSettingsChange(settings.copy(localDnsEnabled = it))
    }
    SettingsSwitchRow("SOCKS5_AUTH", "", settings.socks5Authentication) {
        onSettingsChange(settings.copy(socks5Authentication = it))
    }
    SettingsSwitchRow("TRAFFIC_WARMUP", "", settings.trafficWarmupEnabled) {
        onSettingsChange(settings.copy(trafficWarmupEnabled = it))
    }
    SettingsTextField("TRAFFIC_WARMUP_PROBE_COUNT", settings.trafficWarmupProbeCount, updateString { copy(trafficWarmupProbeCount = it) })
    SettingsTextField("TRAFFIC_KEEPALIVE_INTERVAL", settings.trafficKeepaliveIntervalSeconds, updateString { copy(trafficKeepaliveIntervalSeconds = it) })
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
    OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        value = value,
        onValueChange = onValueChange,
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
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
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
                color = Color.White.copy(alpha = 0.84f),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    color = WhiteZiaTextDim,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
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
    val lines = mutableListOf<String>()
    runtimeLogs
        .asReversed()
        .filter(String::isNotBlank)
        .filter { it != "Idle" }
        .forEach { lines += it }
    localLog
        .lineSequence()
        .map(String::trim)
        .filter(String::isNotBlank)
        .forEach { lines += it }
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
