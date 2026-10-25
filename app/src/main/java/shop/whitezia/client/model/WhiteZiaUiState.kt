package shop.whitezia.client.model

data class WhiteZiaUiState(
    val connectionStatus: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val activeTransportMode: String = WhiteZiaOptions.TransportAuto,
    val settings: WhiteZiaSettings = WhiteZiaSettings(),
    val serverPool: List<StormDnsServerProfile> = emptyList(),
    val networkIpAddress: String = "127.0.0.1",
    val batteryOptimizationIgnored: Boolean = true,
    val notificationsEnabled: Boolean = true,
    val activeConnectionProfileId: String? = null,
    val connectionLogs: List<String> = listOf("Idle"),
    val connectionStats: ConnectionStats = ConnectionStats(),
    val resolverRuntimeState: ResolverRuntimeState = ResolverRuntimeState(),
    val connectionProgress: ConnectionProgressState = ConnectionProgressState(),
    val connectionVerification: ConnectionVerificationState = ConnectionVerificationState(),
    val autoTuneTrialResults: List<AutoTuneTrialResult> = emptyList(),
    val serverTestState: ServerTestState = ServerTestState(),
    val scanState: WhiteZiaScanState = WhiteZiaScanState(),
    val scanWorkerCount: String = WhiteZiaScanDefaults.DefaultWorkerCount.toString(),
    val scanConnectionProfileId: String = ConnectionProfile.DefaultId,
)

internal data class WhiteZiaRuntimePorts(
    val listenPort: Int,
    val httpProxyPort: Int,
    val localDnsPort: Int,
)
