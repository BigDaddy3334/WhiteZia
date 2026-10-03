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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import shop.whitezia.client.model.ConnectionStatus
import shop.whitezia.client.model.WhiteZiaOptions
import shop.whitezia.client.model.WhiteZiaSettings
import shop.whitezia.client.model.withForceDnsTunnel
import shop.whitezia.client.ui.connect.WhiteZiaConnectScreen
import shop.whitezia.client.ui.WhiteZiaTheme
import shop.whitezia.client.ui.WhiteZiaViewModel
import shop.whitezia.client.ui.connection.ConnectionUiPhase
import shop.whitezia.client.ui.connection.WhiteZiaConnectionAccount
import shop.whitezia.client.ui.settings.WhiteZiaSettingsDialog
import shop.whitezia.client.account.WhiteZiaAccountDialog
import shop.whitezia.client.account.WhiteZiaAccountViewModel
import shop.whitezia.client.update.AppUpdateDialog
import shop.whitezia.client.update.AppUpdateInstaller
import shop.whitezia.client.update.AppUpdateState
import shop.whitezia.client.update.AppUpdateViewModel
import shop.whitezia.client.vpn.openVpnBackgroundPermission

class MainActivity : ComponentActivity() {

    private val viewModel by viewModels<WhiteZiaViewModel>()
    private val updateViewModel by viewModels<AppUpdateViewModel>()
    private val accountViewModel by viewModels<WhiteZiaAccountViewModel>()
    private val connectionCoordinator get() = viewModel.connectionUiCoordinator
    private val connectionAccount by lazy { WhiteZiaConnectionAccount(accountViewModel) }
    private val networkMonitor by lazy { MainNetworkMonitor(this) }
    private var inboundProfileLink by mutableStateOf("")
    private var connectionRequestedFromTile by mutableStateOf(false)

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
        connectionRequestedFromTile = savedInstanceState == null && intent?.action == ActionConnectFromTile

        setContent {
            WhiteZiaTheme(
                themeMode = viewModel.uiState.settings.themeMode,
                languageCode = viewModel.uiState.settings.languageCode,
            ) {
                val context = LocalContext.current
                val updateState = updateViewModel.state
                val accountState = accountViewModel.state
                val connectionUiState by connectionCoordinator.state.collectAsState()
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
                LaunchedEffect(accountState.profileInvalidationRevision) {
                    if (accountState.profileInvalidationRevision > 0L) {
                        connectionCoordinator.disconnect(clearSubscription = true)
                        subscriptionLink = ""
                        accountViewModel.profileInvalidationHandled(accountState.profileInvalidationRevision)
                    }
                }
                LaunchedEffect(accountState.pendingProfileBundle, connectionUiState.phase) {
                    accountState.pendingProfileBundle?.takeIf(String::isNotBlank)?.let { bundle ->
                        connectionCoordinator.applyAccountProfile(bundle, connectionAccount)
                    }
                }
                LaunchedEffect(viewModel.uiState.settings.subscriptionLink) {
                    subscriptionLink = viewModel.uiState.settings.subscriptionLink
                }
                var errorMessage by remember { mutableStateOf<String?>(null) }
                val subscriptionQrScanner = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult(),
                ) { result ->
                    val decoded = result.data?.getStringExtra(QrScannerActivity.EXTRA_QR_VALUE)?.trim().orEmpty()
                    when {
                        result.resultCode == Activity.RESULT_OK &&
                            (decoded.startsWith("stormbundle://") || decoded.startsWith("stormdns://")) -> {
                            connectionCoordinator.importProfile(decoded)
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
                var wifiEnabled by remember { mutableStateOf(networkMonitor.isWifiNetworkAvailable()) }
                var showSplitTunnelDialog by rememberSaveable { mutableStateOf(false) }
                var showSettingsDialog by rememberSaveable { mutableStateOf(false) }
                var showAccountDialog by rememberSaveable { mutableStateOf(false) }
                var showLogDialog by rememberSaveable { mutableStateOf(false) }

                val vpnPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult(),
                ) { result ->
                    connectionCoordinator.onVpnPermissionResult(result.resultCode == Activity.RESULT_OK)
                }
                LaunchedEffect(connectionUiState.permissionRequest) {
                    val request = connectionUiState.permissionRequest ?: return@LaunchedEffect
                    if (!connectionCoordinator.claimPermissionRequest(request)) return@LaunchedEffect
                    val permissionIntent = VpnService.prepare(context)
                    if (permissionIntent == null) {
                        connectionCoordinator.onVpnPermissionResult(true)
                    } else {
                        vpnPermissionLauncher.launch(permissionIntent)
                    }
                }
                LaunchedEffect(connectionUiState.openSplitTunnelRequest) {
                    val request = connectionUiState.openSplitTunnelRequest ?: return@LaunchedEffect
                    showSplitTunnelDialog = true
                    connectionCoordinator.consumeSplitTunnelRequest(request)
                }
                LaunchedEffect(inboundProfileLink) {
                    if (inboundProfileLink.isNotBlank()) {
                        connectionCoordinator.importProfile(inboundProfileLink)
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
                    networkMonitor.observeWifiState { state -> wifiEnabled = state.networkAvailable }
                    networkMonitor.observeBaseNetworkTransport { connectionCoordinator.onNetworkChanged() }
                }
                LaunchedEffect(connectionRequestedFromTile) {
                    if (!connectionRequestedFromTile) return@LaunchedEffect
                    connectionRequestedFromTile = false
                    connectionCoordinator.connect(connectionAccount, subscriptionLink)
                }

                fun requestConnectionModeChange(
                    updatedSettings: WhiteZiaSettings,
                    logMessage: String,
                    openSplitTunnelAfterApply: Boolean = false,
                ) {
                    errorMessage = null
                    connectionCoordinator.changeMode(updatedSettings, openSplitTunnelAfterApply)
                    viewModel.appendConnectionLog(logMessage)
                }

                val userStatus = when (connectionUiState.phase) {
                    ConnectionUiPhase.Preparing -> "Проверяю конфигурацию"
                    ConnectionUiPhase.WaitingForPermission -> "Проверяю разрешение VPN"
                    ConnectionUiPhase.Disconnecting -> "Отключение"
                    ConnectionUiPhase.SwitchingMode -> "Останавливаю предыдущий VPN туннель"
                    ConnectionUiPhase.Idle -> when {
                        connectionUiState.error != null -> "Ошибка подключения. Повторите попытку"
                        viewModel.uiState.connectionStatus == ConnectionStatus.CONNECTING -> "Подключение"
                        viewModel.uiState.connectionStatus == ConnectionStatus.CONNECTED -> "Подключение успешно"
                        else -> "Готово к подключению"
                    }
                }

                WhiteZiaConnectScreen(
                    subscriptionLink = subscriptionLink,
                    settings = viewModel.uiState.settings,
                    connectionStatus = viewModel.uiState.connectionStatus,
                    wifiEnabled = wifiEnabled,
                    errorMessage = errorMessage ?: connectionUiState.error,
                    userStatus = userStatus,
                    isDisconnecting = connectionUiState.phase == ConnectionUiPhase.Disconnecting,
                    isSwitchingMode = connectionUiState.phase == ConnectionUiPhase.SwitchingMode,
                    forceDnsTunnel = viewModel.uiState.settings.forceDnsTunnel,
                    xrayPreflightBlocked = false,
                    onConnectClick = {
                        errorMessage = null
                        connectionCoordinator.toggleConnection(connectionAccount, subscriptionLink)
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
                        requestConnectionModeChange(
                            updatedSettings = updatedSettings,
                            logMessage = if (enabled) {
                                "Ручной режим Xray включен"
                            } else {
                                "Ручной режим Xray выключен"
                            },
                        )
                    },
                    onForceDnsTunnelChange = { enabled ->
                        val updatedSettings = viewModel.uiState.settings.withForceDnsTunnel(enabled)
                        requestConnectionModeChange(
                            updatedSettings = updatedSettings,
                            logMessage = if (enabled) {
                                "Принудительный DNS канал включен"
                            } else {
                                "Автоматический выбор канала включен"
                            },
                        )
                    },
                    onAccountClick = {
                        accountViewModel.retrySessionRestore()
                        showAccountDialog = true
                    },
                    onSettingsClick = { showSettingsDialog = true },
                    onLogClick = { showLogDialog = true },
                    onSplitTunnelClick = { showSplitTunnelDialog = true },
                    backgroundVpnAllowed = viewModel.uiState.batteryOptimizationIgnored,
                    onBackgroundPermissionClick = { context.openVpnBackgroundPermission() },
                )

                if (showLogDialog) {
                    WhiteZiaLogDialog(
                        logText = buildVisibleLog(
                            localLog = "",
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
                            connectionCoordinator.cancelPreparation()
                            if (accountState.managedProfileInstalled) {
                                connectionCoordinator.disconnect(clearSubscription = true)
                                errorMessage = null
                                subscriptionLink = ""
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
                            requestConnectionModeChange(
                                updatedSettings = updatedSettings.copy(
                                    subscriptionLink = updatedSubscriptionLink,
                                ),
                                logMessage = if (updatedSettings.manualMode) {
                                    "Ручной режим включен"
                                } else {
                                    "Автоматический режим включен"
                                },
                                openSplitTunnelAfterApply = true,
                            )
                            showSettingsDialog = false
                        },
                        onScanSubscription = {
                            subscriptionQrScanner.launch(Intent(context, QrScannerActivity::class.java))
                        },
                        isCheckingForUpdates = updateState is AppUpdateState.Checking,
                        onCheckForUpdates = { updateViewModel.checkForUpdate() },
                        onSave = { updatedSettings, updatedSubscriptionLink ->
                            subscriptionLink = updatedSubscriptionLink
                            requestConnectionModeChange(
                                updatedSettings = updatedSettings.copy(
                                    subscriptionLink = updatedSubscriptionLink,
                                ),
                                logMessage = if (updatedSettings.manualMode) {
                                    "Ручной режим включен"
                                } else {
                                    "Автоматический режим включен"
                                },
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
        if (intent.action == ActionConnectFromTile) connectionRequestedFromTile = true
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

    companion object {
        internal const val ActionConnectFromTile = "shop.whitezia.client.CONNECT_FROM_TILE"
        private const val StormDnsScheme = "stormdns"
        private const val StormBundleScheme = "stormbundle"
    }
}
