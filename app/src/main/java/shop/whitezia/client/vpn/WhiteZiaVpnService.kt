package shop.whitezia.client.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.channels.Channel
import shop.whitezia.client.MainActivity
import shop.whitezia.client.R
import shop.whitezia.client.model.ResolvedWhiteZiaSettings
import shop.whitezia.client.model.StormDnsServerProfile
import shop.whitezia.client.model.WhiteZiaOptions
import shop.whitezia.client.model.WhiteZiaSettings
import shop.whitezia.client.model.WhiteZiaSettingsStore
import shop.whitezia.client.model.resolve
import shop.whitezia.client.model.runtimeConnectionSettings
import shop.whitezia.client.model.selectedConnectionProfile
import shop.whitezia.client.proxy.WhiteZiaProxyService
import shop.whitezia.client.runtime.RuntimeLaunchRequestStore
import shop.whitezia.client.runtime.WhiteZiaRuntimeStateStore
import shop.whitezia.client.runtime.WhiteZiaTrafficWarmup
import shop.whitezia.client.runtime.ConnectionProbeClient
import shop.whitezia.client.runtime.formatTrafficNotificationText
import shop.whitezia.client.runtime.parseStormDnsTrafficStatsLine
import shop.whitezia.client.storm.StormDnsProcessManager
import shop.whitezia.client.xray.XrayProcessManager

class WhiteZiaVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var foregroundStarted = false
    @Volatile
    private var startJob: Job? = null
    @Volatile
    private var stopJob: Job? = null
    private var xrayMonitorJob: Job? = null
    private var keepaliveJob: Job? = null
    private var livenessJob: Job? = null
    @Volatile
    private var activeSettings: WhiteZiaSettings? = null
    @Volatile
    private var lastStopComplete = true
    @Volatile
    private var runtimeReady = false
    @Volatile
    private var connectionRequested = false
    @Volatile
    private var requestedSessionId = ""
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private val networkChanges = Channel<Unit>(Channel.CONFLATED)
    private val networkReconnectPolicy = NetworkReconnectPolicy()
    private val reconnectBackoff = NetworkReconnectBackoff()
    @Volatile
    private var sessionWasReady = false
    @Volatile
    private var networkRecoveryPending = false
    @Volatile
    private var nextRecoveryAtMillis = 0L
    private var recoveryWakeLock: PowerManager.WakeLock? = null
    private val connectivityManager by lazy { getSystemService(ConnectivityManager::class.java) }
    private var lastTrafficNotificationUpdateMillis = 0L
    @Volatile
    private var currentSessionId = ""
    @Volatile
    private var runtimeFailureMessage: String? = null
    private val stopLock = Any()
    @Volatile
    private var routingStartupFailure = AtomicReference<String?>(null)
    @Volatile
    private var requestGeneration = 0L
    @Volatile
    private var stopping = false
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val stormDnsProcessManager by lazy {
        StormDnsProcessManager(applicationContext)
    }
    private val xrayProcessManager by lazy {
        XrayProcessManager(applicationContext)
    }
    private val amneziaWgBackend by lazy {
        AmneziaWgBackend()
    }
    private val tun2SocksProcessManager by lazy {
        Tun2SocksProcessManager(applicationContext)
    }
    private val serviceResolvers by lazy { ServiceResolverCoordinator(applicationContext) }

    override fun onBind(intent: Intent): IBinder? {
        return super.onBind(intent)
    }

    override fun onCreate() {
        super.onCreate()
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                if (connectionRequested && networkRecoveryPending) holdRecoveryCpu()
                networkChanges.trySend(Unit)
            }
            override fun onLost(network: Network) {
                networkReconnectPolicy.networkLost(network.toString())
                networkChanges.trySend(Unit)
            }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                networkReconnectPolicy.capabilitiesChanged(
                    network.toString(),
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
                )
                networkChanges.trySend(Unit)
            }
        }
        runCatching {
            connectivityManager.registerNetworkCallback(
                NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                    .build(),
                callback,
            )
            networkCallback = callback
        }.onFailure { Log.w(Tag, "Could not observe physical networks", it) }
        // Handler-backed timers must keep recovery independent of the Activity and IO timer thread.
        serviceScope.launch(Dispatchers.Main.immediate) {
            monitorNetworkRecovery(
                networkChanges, NetworkRecoveryPollMillis, 750L,
                onError = { Log.w(Tag, "Network recovery observation failed; retrying", it) },
            ) {
                withContext(Dispatchers.IO) { checkNetworkRecovery() }
            }
        }
    }

    private suspend fun checkNetworkRecovery() {
        if (!connectionRequested || stopping || (!runtimeReady && !networkRecoveryPending)) return
        val network = connectivityManager.physicalInternetNetwork()
        val sessionId = currentSessionId
        val request = RuntimeLaunchRequestStore.loadOrRecover(applicationContext, sessionId) ?: return
        val capabilities = network?.let { connectivityManager.getNetworkCapabilities(it) }
        val usable = capabilities != null &&
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.P ||
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED))
        val networkRecovered = networkReconnectPolicy.shouldReconnect(network?.toString(), usable)
        if (network == null || !usable) {
            if (!networkRecoveryPending) {
                logInfo("Сеть потеряна: жду восстановления соединения")
                networkRecoveryPending = true
                holdRecoveryCpu()
                nextRecoveryAtMillis = 0L
                sendVpnEvent(BroadcastTypeReconnecting, "Сеть потеряна, жду восстановления")
                updateForegroundNotification("Ожидание сети")
            }
            markNetworkRecoveryWaiting(request.settings, sessionId)
            return
        }
        if (request.settings.manualMode && request.settings.forceDnsTunnel && hasActiveWifiNetwork()) {
            if (!networkRecoveryPending) {
                logInfo("StormDNS: выключите Wi-Fi для восстановления подключения")
                sendVpnEvent(BroadcastTypeReconnecting, "StormDNS ждёт отключения Wi-Fi")
                networkRecoveryPending = true
            }
            markNetworkRecoveryWaiting(request.settings, sessionId)
            return
        }
        if (startJob?.isActive == true) {
            markNetworkRecoveryWaiting(request.settings, sessionId)
            return
        }
        if (!networkRecovered && (!networkRecoveryPending || SystemClock.elapsedRealtime() < nextRecoveryAtMillis)) {
            if (networkRecoveryPending) markNetworkRecoveryWaiting(request.settings, sessionId)
            return
        }
        if (!connectionRequested || currentSessionId != sessionId || requestedSessionId != sessionId) return
        networkRecoveryPending = true
        logInfo("Восстановление сети: заново проверяю доступные VPN маршруты")
        startVpn(sessionId, settleDelayMillis = 3_000L)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return when (intent?.action) {
            ActionStop -> {
                requestGeneration += 1
                connectionRequested = false
                networkRecoveryPending = false
                releaseRecoveryCpu()
                runtimeFailureMessage = null
                requestStop(startId)
                START_NOT_STICKY
            }
            ActionStart, ActionReconfigure -> {
                val sessionId = intent.getStringExtra(ExtraSessionId).orEmpty()
                if (sessionId.isBlank()) {
                    Log.w(Tag, "Ignoring VPN start without session ID")
                    stopSelfResult(startId)
                    return START_NOT_STICKY
                }
                if (intent.action == ActionReconfigure && (!connectionRequested || requestedSessionId != sessionId)) {
                    Log.i(Tag, "Ignoring settings update for an inactive VPN session")
                    if (!connectionRequested) stopSelfResult(startId)
                    return START_NOT_STICKY
                }
                try {
                    requestGeneration += 1
                    if (requestedSessionId != sessionId) {
                        sessionWasReady = false
                        networkRecoveryPending = false
                        reconnectBackoff.reset()
                    }
                    connectionRequested = true
                    requestedSessionId = sessionId
                    enterForeground("Preparing WhiteZia")
                    startVpn(sessionId, settleDelayMillis = if (runtimeReady) 3_000L else 0L)
                    START_NOT_STICKY
                } catch (error: Exception) {
                    logError("Failed to start foreground VPN service", error)
                    connectionRequested = false
                    requestStop(startId)
                    START_NOT_STICKY
                }
            }
            else -> {
                Log.w(Tag, "Ignoring unexpected VPN service action: ${intent?.action ?: "null"}")
                stopSelfResult(startId)
                START_NOT_STICKY
            }
        }
    }

    override fun onDestroy() {
        requestGeneration += 1
        connectionRequested = false
        releaseRecoveryCpu()
        networkCallback?.let { runCatching { connectivityManager.unregisterNetworkCallback(it) } }
        networkCallback = null
        networkChanges.close()
        val previousStart = startJob
        val previousStop = stopJob
        previousStart?.cancel()
        exitForeground()
        serviceScope.launch {
            withContext(NonCancellable) {
                previousStart?.join()
                previousStop?.join()
                VpnRuntimeOwnership.mutex.withLock { stopVpn() }
            }
            serviceScope.cancel()
        }
        super.onDestroy()
    }

    private fun requestStop(startId: Int) {
        stopping = true
        val startJobToStop = startJob
        startJobToStop?.cancel()
        val previousStopJob = stopJob
        stopJob = serviceScope.launch {
            previousStopJob?.join()
            startJobToStop?.cancelAndJoin()
            if (startJob === startJobToStop) {
                startJob = null
            }
            VpnRuntimeOwnership.mutex.withLock { stopVpn() }
            withContext(Dispatchers.Main.immediate) {
                if (!connectionRequested) exitForeground()
                stopSelfResult(startId)
            }
        }
    }

    override fun onRevoke() {
        // Android may revoke off Main. Its default stopSelf bypasses our serialized teardown.
        serviceScope.launch(Dispatchers.Main.immediate) {
            val generation = ++requestGeneration
            connectionRequested = false
            val hadActiveRuntime = runtimeReady || (
                WhiteZiaRuntimeStateStore.read(applicationContext, WhiteZiaRuntimeStateStore.ModeVpn)
                    ?.status in setOf(
                        WhiteZiaRuntimeStateStore.StatusStarting,
                        WhiteZiaRuntimeStateStore.StatusReady,
                        WhiteZiaRuntimeStateStore.StatusStopping,
                    )
                )
            val failureMessage = "VPN permission was revoked by Android"
            if (hadActiveRuntime) runtimeFailureMessage = failureMessage
            val previousStart = startJob
            previousStart?.cancel()
            val previousStop = stopJob
            stopJob = serviceScope.launch {
                previousStart?.join()
                previousStop?.join()
                VpnRuntimeOwnership.mutex.withLock { stopVpn() }
                withContext(Dispatchers.Main.immediate) {
                    if (requestGeneration == generation && !connectionRequested) {
                        if (hadActiveRuntime) reportFailure(failureMessage)
                        exitForeground()
                        stopSelf()
                    }
                }
            }
        }
    }

    private fun enterForeground(statusText: String) {
        createNotificationChannel()
        val notification = buildForegroundNotification(statusText)
        if (foregroundStarted) {
            updateForegroundNotification(statusText)
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NotificationId,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED,
            )
        } else {
            startForeground(NotificationId, notification)
        }
        foregroundStarted = true
    }

    private fun updateForegroundNotification(statusText: String) {
        if (!foregroundStarted) {
            return
        }
        getSystemService(NotificationManager::class.java)
            .notify(NotificationId, buildForegroundNotification(statusText))
    }

    private fun exitForeground() {
        if (!foregroundStarted) {
            return
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        foregroundStarted = false
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NotificationChannelId,
            "WhiteZia VPN",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Shows the active WhiteZia VPN connection"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildForegroundNotification(statusText: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            pendingIntentFlags,
        )
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, WhiteZiaVpnService::class.java).setAction(ActionStop),
            pendingIntentFlags,
        )

        return NotificationCompat.Builder(this, NotificationChannelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("WhiteZia VPN")
            .setContentText(statusText)
            .setContentIntent(openAppPendingIntent)
            .addAction(R.drawable.ic_notification, "Disconnect", stopPendingIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .build()
    }

    @Synchronized
    private fun startVpn(sessionId: String, settleDelayMillis: Long = 0L) {
        if (!connectionRequested || requestedSessionId != sessionId) return
        val previousJob = startJob
        val pendingStopJob = stopJob
        val generation = requestGeneration
        startJob = serviceScope.launch {
            var startupHeartbeat: Job? = null
            pendingStopJob?.join()
            if (stopJob === pendingStopJob) {
                stopJob = null
            }
            previousJob?.cancelAndJoin()
            val reconnectWakeLock = if (settleDelayMillis > 0L) {
                runCatching {
                    getSystemService(PowerManager::class.java)
                        .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:VpnReconnect")
                        .apply {
                            setReferenceCounted(false)
                            acquire(60_000L)
                        }
                }.onFailure { Log.w(Tag, "Could not hold CPU during VPN reconnect", it) }.getOrNull()
            } else null
            try {
                currentCoroutineContext().ensureActive()
                if (!connectionRequested || requestedSessionId != sessionId) return@launch
                currentSessionId = sessionId
                runtimeFailureMessage = null
                runtimeReady = false
                val launchRequest = RuntimeLaunchRequestStore.loadOrRecover(applicationContext, sessionId)
                    ?: throw IllegalStateException("Runtime launch request is missing")
                val settings = launchRequest.settings.runtimeConnectionSettings()
                if (settings.resolve().connectionMode != "vpn") {
                    throw IllegalStateException("VPN mode is not enabled")
                }
                if (settleDelayMillis > 0L) {
                    updateForegroundNotification("Переподключение VPN")
                    sendVpnEvent(BroadcastTypeReconnecting, "Переподключаю VPN")
                }
                activeSettings = settings
                lastTrafficNotificationUpdateMillis = 0L
                WhiteZiaProxyService.stop(applicationContext)
                currentCoroutineContext().ensureActive()
                if (!connectionRequested || requestedSessionId != sessionId) {
                    throw CancellationException("VPN stop requested")
                }
                val initialNetwork = connectivityManager.physicalInternetNetwork()
                networkReconnectPolicy.reset(
                    initialNetwork?.toString(),
                    initialNetwork?.let { connectivityManager.getNetworkCapabilities(it) }
                        ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
                )
                startupHeartbeat = serviceScope.launch(Dispatchers.Main.immediate) {
                    while (isActive && connectionRequested && requestedSessionId == sessionId) {
                        delay(NetworkRecoveryPollMillis)
                        val current = activeSettings ?: settings
                        WhiteZiaRuntimeStateStore.markStarting(applicationContext, current, sessionId,
                            "Подключаю ${transportLabel(current.transportMode)}", recoveryWaiting = networkRecoveryPending,
                            transportMode = runtimeTransport(current))
                    }
                }
                val orchestrator = VpnConnectionOrchestrator(
                    pause = { millis -> withContext(Dispatchers.Main.immediate) { delay(millis) } },
                    runtime = object : VpnTransportRuntime {
                        override suspend fun stop() {
                            stopVpn(restartingSettings = activeSettings ?: settings)
                        }
                        override suspend fun awaitStopped() {
                            check(lastStopComplete) { "Не удалось полностью остановить предыдущий VPN туннель" }
                            waitForLocalPortToClose(settings.resolve().listenPort)
                        }
                        override suspend fun prepare(candidates: List<VpnConnectionCandidate>): List<VpnConnectionCandidate> {
                            if (candidates.none { it.settings.transportMode == WhiteZiaOptions.TransportXray }) return candidates
                            val network = connectivityManager.physicalInternetNetwork()
                                ?: error("Нет доступной сети для проверки маршрутов")
                            logInfo("Проверяю доступность всех Xray маршрутов")
                            val endpoint = XrayEndpointProbe(network)
                            return XrayRoutePreflight(endpoint::probe, report = { candidate, result ->
                                val label = if (candidate.routeKind == XrayRouteKind.Direct) "direct" else "cdn"
                                logInfo("Xray $label ${candidate.nodeId}: " +
                                    (result.latencyMillis?.let { "$it ms (TCP/TLS)" } ?: "недоступен (${result.reason})"))
                            }).prepare(candidates)
                        }
                        override suspend fun start(candidate: VpnConnectionCandidate) {
                            currentCoroutineContext().ensureActive()
                            check(connectionRequested && requestedSessionId == sessionId) { "Подключение отменено" }
                            val selected = candidate.settings
                            stopping = false
                            activeSettings = selected
                            WhiteZiaRuntimeStateStore.markStarting(applicationContext, selected, sessionId, "Starting ${transportLabel(selected.transportMode)} VPN",
                                transportMode = runtimeTransport(selected))
                            when (selected.transportMode) {
                                WhiteZiaOptions.TransportAuto -> error("Автоматический режим требует выбора маршрута")
                                WhiteZiaOptions.TransportXray -> {
                                    check(awaitXrayNetworkReady()) { "Нет подключения к интернету для Xray" }
                                    startXrayAndVpn(sessionId, selected, selected.resolve())
                                }
                                WhiteZiaOptions.TransportDns -> {
                                    awaitDnsNetworkReady(sessionId, selected)
                                    val prepared = serviceResolvers.prepare(selected)
                                    activeSettings = prepared
                                    val profile = selectServerProfile(prepared) ?: launchRequest.serverProfile
                                    check(prepared.resolve().resolverEntries.isNotEmpty()) { "StormDNS resolvers are missing" }
                                    startStormDnsAndVpn(sessionId, requireNotNull(profile) { "StormDNS server profile is missing" }, prepared, prepared.resolve())
                                }
                            }
                        }
                        override suspend fun verify(candidate: VpnConnectionCandidate): Boolean {
                            val selected = activeSettings ?: candidate.settings
                            requireRoutingAlive(selected)
                            logInfo("Проверяю ${transportLabel(selected.transportMode)} через туннель")
                            val healthy = ConnectionProbeClient().isHttpHealthy(::logInfo, selected.resolve().listenPort)
                            currentCoroutineContext().ensureActive()
                            requireRoutingAlive(selected)
                            return healthy
                        }
                    },
                    onFailure = { candidate, error ->
                        logWarning("${transportLabel(candidate.settings.transportMode)} ${candidate.nodeId}: ${error.message}; пробую следующий маршрут")
                    },
                    onState = { state ->
                        if (state.phase != VpnConnectionPhase.Connected) {
                            sendVpnEvent(BroadcastTypeReconnecting, "Подключение ${state.attempt}/${state.totalAttempts}: ${transportLabel(state.transport)}")
                        }
                    },
                )
                val winner = orchestrator.connect(VpnCandidatePlanner.plan(settings), settleFirst = settleDelayMillis > 0L)
                currentCoroutineContext().ensureActive()
                if (!connectionRequested || requestedSessionId != sessionId) throw CancellationException("VPN stop requested")
                val selected = activeSettings ?: winner.settings
                if (selected.transportMode == WhiteZiaOptions.TransportDns) {
                    optimizeServiceResolvers(sessionId, selected)
                }
                startupHeartbeat.cancelAndJoin()
                startupHeartbeat = null
                publishConnected(sessionId, activeSettings ?: selected)
            } catch (error: CancellationException) {
                withContext(NonCancellable) { startupHeartbeat?.cancelAndJoin() }
                withContext(NonCancellable) { VpnRuntimeOwnership.mutex.withLock { stopVpn() } }
                throw error
            } catch (error: Exception) {
                startupHeartbeat?.cancelAndJoin()
                failAndStopVpn(sessionId, generation, "Failed to start WhiteZia VPN", error)
            } finally {
                withContext(NonCancellable) { startupHeartbeat?.cancelAndJoin() }
                reconnectWakeLock?.let { lock ->
                    runCatching { if (lock.isHeld) lock.release() }
                }
            }
        }
    }


    private suspend fun awaitXrayNetworkReady(): Boolean {
        val deadline = SystemClock.elapsedRealtime() + XrayNetworkReadyTimeoutMillis
        var readySinceMillis = 0L
        while (SystemClock.elapsedRealtime() < deadline) {
            val now = SystemClock.elapsedRealtime()
            if (connectivityManager.physicalInternetNetwork() != null) {
                if (readySinceMillis == 0L) {
                    readySinceMillis = now
                }
                if (now - readySinceMillis >= XrayNetworkStableWindowMillis) {
                    return true
                }
            } else {
                readySinceMillis = 0L
            }
            delay(XrayNetworkReadyPollMillis)
        }
        return connectivityManager.physicalInternetNetwork() != null
    }

    private fun hasActiveWifiNetwork(): Boolean {
        val connectivityManager = getSystemService(ConnectivityManager::class.java) ?: return false
        val activeNetwork = connectivityManager.physicalInternetNetwork() ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private suspend fun awaitDnsNetworkReady(sessionId: String, settings: WhiteZiaSettings) {
        var notified = false
        while (hasActiveWifiNetwork() || connectivityManager.physicalInternetNetwork() == null) {
            currentCoroutineContext().ensureActive()
            if (!connectionRequested || requestedSessionId != sessionId) throw CancellationException("VPN stop requested")
            if (!notified) {
                val message = if (hasActiveWifiNetwork()) "Выключите Wi-Fi для StormDNS" else "StormDNS: жду мобильную сеть"
                logInfo(message)
                sendVpnEvent(BroadcastTypeReconnecting, message)
                updateForegroundNotification(message)
                notified = true
            }
            WhiteZiaRuntimeStateStore.markStarting(applicationContext, settings, sessionId,
                "StormDNS: ожидание мобильной сети", recoveryWaiting = true)
            delay(1_000L)
        }
    }


    internal fun newVpnBuilder(): VpnService.Builder = Builder()

    private suspend fun startAmneziaWgVpn(
        sessionId: String,
        settings: WhiteZiaSettings,
    ) {
        logInfo("Starting AmneziaWG primary tunnel")
        amneziaWgBackend.start(
            service = this,
            settings = settings,
            configText = settings.amneziaWgConfig,
            onLog = ::logInfo,
        )
        currentCoroutineContext().ensureActive()
        if (stopping || currentSessionId != sessionId) {
            throw CancellationException("AmneziaWG start was cancelled")
        }
    }

    private suspend fun startXrayAndVpn(
        sessionId: String,
        settings: WhiteZiaSettings,
        resolvedSettings: ResolvedWhiteZiaSettings,
    ) {
        val startupFailure = AtomicReference<String?>(null)
        xrayProcessManager.start(settings, resolvedSettings) { line ->
            logInfo(line)
            detectXrayStartupFailure(line)?.let { failure ->
                startupFailure.compareAndSet(null, failure)
            }
        }
        currentCoroutineContext().ensureActive()
        waitForProxyPort(
            runtimeName = "Xray",
            listenPort = resolvedSettings.listenPort,
            startupFailure = { startupFailure.get() },
            isRunning = { xrayProcessManager.isRunning() },
            exitCode = { xrayProcessManager.exitCodeOrNull() },
        )
        logInfo("Xray SOCKS proxy is ready")
        startVpnRouting(
            sessionId = sessionId,
            settings = settings,
            resolvedSettings = resolvedSettings,
            readyMessage = "Xray VPN routing started",
            notificationText = "Xray VPN is active",
        )
    }

    private suspend fun startStormDnsAndVpn(
        sessionId: String,
        serverProfile: StormDnsServerProfile,
        settings: WhiteZiaSettings,
        resolvedSettings: ResolvedWhiteZiaSettings,
    ) {
        val startupFailure = AtomicReference<String?>(null)
        stormDnsProcessManager.start(serverProfile, settings) { line ->
            logInfo(line)
            detectStormDnsStartupFailure(line)?.let { failure ->
                startupFailure.compareAndSet(null, failure)
            }
        }
        currentCoroutineContext().ensureActive()
        waitForProxyPort(
            runtimeName = "StormDNS",
            listenPort = resolvedSettings.listenPort,
            startupFailure = { startupFailure.get() },
            isRunning = { stormDnsProcessManager.isRunning() },
            exitCode = { stormDnsProcessManager.exitCodeOrNull() },
        )
        logInfo("StormDNS SOCKS proxy is ready")
        startVpnRouting(
            sessionId = sessionId,
            settings = settings,
            resolvedSettings = resolvedSettings,
            readyMessage = "StormDNS VPN routing started",
            notificationText = "Full-device VPN is active",
        )
    }

    private suspend fun optimizeServiceResolvers(sessionId: String, settings: WhiteZiaSettings) {
        VpnRuntimeOwnership.mutex.withLock {
            activeSettings = serviceResolvers.optimize(
                settings = settings,
                start = { next ->
                    currentCoroutineContext().ensureActive()
                    check(connectionRequested && requestedSessionId == sessionId) { "Подключение отменено" }
                    stopping = false
                    activeSettings = next
                    WhiteZiaRuntimeStateStore.markStarting(applicationContext, next, sessionId, "Сравнение DNS резолверов")
                    val profile = requireNotNull(selectServerProfile(next))
                    startStormDnsAndVpn(sessionId, profile, next, next.resolve())
                    requireRoutingAlive(next)
                    check(ConnectionProbeClient().isHttpHealthy(::logInfo, next.resolve().listenPort)) { "DNS health-check не пройден" }
                    requireRoutingAlive(next)
                },
                stop = {
                    withContext(NonCancellable) {
                        stopVpn(restartingSettings = activeSettings ?: settings)
                        check(lastStopComplete) { "Не удалось полностью остановить предыдущий VPN туннель" }
                        waitForLocalPortToClose(settings.resolve().listenPort)
                    }
                },
                log = ::logInfo,
            )
        }
    }

    private fun publishConnected(sessionId: String, settings: WhiteZiaSettings) {
        if (settings.transportMode != WhiteZiaOptions.TransportAuto) requireRoutingAlive(settings)
        activeSettings = settings
        stopping = false
        runtimeReady = true
        val message = "${transportLabel(settings.transportMode)} VPN routing started"
        WhiteZiaRuntimeStateStore.markReady(applicationContext, settings, sessionId, message, transportMode = runtimeTransport(settings))
        updateForegroundNotification("Успешное подключение: ${transportLabel(settings.transportMode)}")
        reportReady(message)
        if (settings.transportMode != WhiteZiaOptions.TransportAuto) {
            startTrafficKeepalive(settings.resolve())
            startRuntimeWatchdog(sessionId, settings)
            if (!tun2SocksProcessManager.isRunning()) requestRuntimeRecovery("tun2proxy exited during readiness", sessionId)
        }
    }

    private fun requireRoutingAlive(settings: WhiteZiaSettings) {
        routingStartupFailure.get()?.let { throw IllegalStateException(it) }
        check(vpnInterface != null && tun2SocksProcessManager.isRunning()) { "VPN routing runner stopped before readiness" }
        val proxyAlive = if (settings.transportMode == WhiteZiaOptions.TransportXray) xrayProcessManager.isRunning()
            else stormDnsProcessManager.isRunning()
        check(proxyAlive) { "${transportLabel(settings.transportMode)} process stopped before readiness" }
    }

    private fun startRuntimeWatchdog(sessionId: String, settings: WhiteZiaSettings) {
        xrayMonitorJob?.cancel()
        xrayMonitorJob = serviceScope.launch {
            while (isActive && connectionRequested && !stopping && currentSessionId == sessionId) {
                val alive = if (settings.transportMode == WhiteZiaOptions.TransportXray) {
                    xrayProcessManager.isRunning()
                } else stormDnsProcessManager.isRunning()
                if (!alive || !tun2SocksProcessManager.isRunning()) {
                    requestRuntimeRecovery("${transportLabel(settings.transportMode)} process exited", sessionId)
                    break
                }
                delay(1_000L)
            }
        }
        livenessJob?.cancel()
        livenessJob = serviceScope.launch {
            val policy = TunnelLivenessPolicy()
            while (isActive && connectionRequested && !stopping && currentSessionId == sessionId) {
                delay(TunnelLivenessIntervalMillis)
                if (!runtimeReady || networkRecoveryPending || connectivityManager.physicalInternetNetwork() == null) continue
                val healthy = ConnectionProbeClient().isHttpHealthy({}, settings.resolve().listenPort)
                currentCoroutineContext().ensureActive()
                if (policy.observe(healthy)) {
                    requestRuntimeRecovery("${transportLabel(settings.transportMode)}: три сетевые проверки подряд не прошли", sessionId)
                    break
                }
            }
        }
    }

    @Synchronized
    private fun requestRuntimeRecovery(message: String, sessionId: String) {
        if (!connectionRequested || stopping || !runtimeReady || currentSessionId != sessionId || requestedSessionId != sessionId || networkRecoveryPending) return
        logWarning("$message; восстанавливаю подключение")
        networkRecoveryPending = true
        nextRecoveryAtMillis = SystemClock.elapsedRealtime() + reconnectBackoff.afterFailureMillis()
        activeSettings?.let { markNetworkRecoveryWaiting(it, sessionId) }
        sendVpnEvent(BroadcastTypeReconnecting, "Восстанавливаю VPN")
        networkChanges.trySend(Unit)
    }

    private fun transportLabel(transport: String): String = when (transport) {
        WhiteZiaOptions.TransportAuto -> "автоматический режим"
        WhiteZiaOptions.TransportXray -> "Xray"
        else -> "StormDNS"
    }

    private fun runtimeTransport(settings: WhiteZiaSettings): String = settings.transportMode

    private suspend fun waitForProxyPort(
        runtimeName: String,
        listenPort: Int,
        startupFailure: () -> String?,
        isRunning: () -> Boolean,
        exitCode: () -> Int?,
    ) {
        val deadline = SystemClock.elapsedRealtime() + ProxyStartupTimeoutMillis
        while (true) {
            startupFailure()?.let { failure ->
                throw IllegalStateException("$runtimeName startup failed: $failure")
            }
            if (!isRunning()) {
                val processExitCode = exitCode()
                throw IllegalStateException(
                    "$runtimeName process exited before SOCKS was ready${processExitCode?.let { " (exit code $it)" }.orEmpty()}",
                )
            }
            if (canConnectToLocalPort(listenPort)) {
                return
            }
            if (SystemClock.elapsedRealtime() >= deadline) {
                throw IllegalStateException("$runtimeName SOCKS startup timed out")
            }
            delay(500)
        }
    }

    private suspend fun waitForLocalPortToClose(port: Int) {
        val deadline = SystemClock.elapsedRealtime() + PreviousRuntimeStopTimeoutMillis
        while (canConnectToLocalPort(port)) {
            if (SystemClock.elapsedRealtime() >= deadline) {
                throw IllegalStateException("Previous local proxy listener is still active on port $port")
            }
            delay(PreviousRuntimeStopPollMillis)
        }
    }

    private fun canConnectToLocalPort(port: Int): Boolean {
        return runCatching {
            Socket().use { socket ->
                socket.connect(InetSocketAddress("127.0.0.1", port), 300)
            }
            true
        }.getOrDefault(false)
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

    private fun detectXrayStartupFailure(line: String): String? {
        val normalized = line.lowercase()
        return when {
            "failed to" in normalized -> line.trim()
            "cannot" in normalized && "start" in normalized -> line.trim()
            "error" in normalized && "started" !in normalized -> line.trim()
            else -> null
        }
    }

    private suspend fun startVpnRouting(
        sessionId: String,
        settings: WhiteZiaSettings,
        resolvedSettings: ResolvedWhiteZiaSettings,
        readyMessage: String,
        notificationText: String,
    ) {
        try {
            currentCoroutineContext().ensureActive()
            val socksHost = selectVpnSocksHost(resolvedSettings.listenIp)
            val socksPort = resolvedSettings.listenPort
            val socksUsername = if (resolvedSettings.socks5Authentication) {
                resolvedSettings.socksUsername
            } else {
                null
            }
            val socksPassword = if (resolvedSettings.socks5Authentication) {
                resolvedSettings.socksPassword
            } else {
                null
            }
            val vpnMtu = if (android.os.Process.is64Bit()) VpnMtu else VpnMtu32Bit
            logInfo(
                "Preparing Android VPN interface with virtual DNS " +
                    "(process=${if (android.os.Process.is64Bit()) "64-bit" else "32-bit"}, mtu=$vpnMtu)",
            )
            tun2SocksProcessManager.requireBinary()
            logInfo("tun2proxy native library is ready")
            val vpnBuilder = Builder()
                .setSession("WhiteZia")
                .setMtu(vpnMtu)
                .addAddress(TunIpv4Address, TunIpv4PrefixLength)
                .addDnsServer(TunDnsServer)
                .addRoute(TunDnsServer, 32)
                .addRoute("0.0.0.0", 0)
                .apply {
                    configureSplitTunnelApplications(
                        splitTunnelMode = resolvedSettings.splitTunnelMode,
                        splitTunnelPackages = resolvedSettings.splitTunnelPackages,
                    )
                }
            logInfo("Establishing Android VPN interface")
            val tun = vpnBuilder.establish()
                ?: throw IllegalStateException("Failed to establish WhiteZia VPN interface")

            try {
                currentCoroutineContext().ensureActive()
                if (stopping || currentSessionId != sessionId) {
                    throw CancellationException("VPN routing start was cancelled")
                }
            } catch (error: CancellationException) {
                runCatching { tun.close() }
                throw error
            }
            vpnInterface = tun
            val attemptFailure = AtomicReference<String?>(null)
            routingStartupFailure = attemptFailure
            logInfo("Android VPN interface established")
            val tunFd = tun.fd
            logInfo("Routing device traffic to SOCKS $socksHost:$socksPort")
            tun2SocksProcessManager.start(
                tunFileDescriptor = tunFd,
                closeTunFileDescriptorOnDrop = false,
                tunMtu = vpnMtu,
                socksHost = socksHost,
                socksPort = socksPort,
                socksUsername = socksUsername,
                socksPassword = socksPassword,
                onOutput = { line ->
                    logInfo("tun2proxy: $line")
                },
                onExit = { exitCode ->
                    if (stopping) {
                        Log.i(Tag, "tun2proxy stopped with code $exitCode")
                    } else {
                        val message = "tun2proxy exited with code $exitCode"
                        if (currentSessionId == sessionId && vpnInterface === tun) {
                            attemptFailure.compareAndSet(null, message)
                        }
                        serviceScope.launch {
                            if (!stopping && currentSessionId == sessionId && vpnInterface === tun) {
                                requestRuntimeRecovery(message, sessionId)
                            }
                        }
                    }
                },
            )
            currentCoroutineContext().ensureActive()
            if (stopping || currentSessionId != sessionId) {
                throw CancellationException("VPN routing start was cancelled")
            }
            requireRoutingAlive(settings)
            logInfo(readyMessage)
        } catch (error: CancellationException) {
            stopVpn()
            throw error
        } catch (error: Exception) {
            stopVpn()
            throw IllegalStateException("Failed to start WhiteZia VPN routing", error)
        }
    }

    private fun stopVpn(restartingSettings: WhiteZiaSettings? = null) = synchronized(stopLock) {
        stopping = true
        runtimeReady = false
        lastTrafficNotificationUpdateMillis = 0L
        if (restartingSettings != null) {
            WhiteZiaRuntimeStateStore.markStarting(
                applicationContext, restartingSettings, currentSessionId,
                "Reconnecting ${transportLabel(restartingSettings.transportMode)} after network change",
                transportMode = runtimeTransport(restartingSettings),
            )
        } else WhiteZiaRuntimeStateStore.markStopping(
            context = applicationContext,
            mode = WhiteZiaRuntimeStateStore.ModeVpn,
            sessionId = currentSessionId,
            message = "VPN service stopping",
        )
        xrayMonitorJob?.cancel()
        xrayMonitorJob = null
        livenessJob?.cancel()
        livenessJob = null
        stopTrafficKeepalive()
        val interfaceToClose = vpnInterface
        vpnInterface = null
        lastStopComplete = true
        // Cancel native reads before closing their borrowed fd. TUN close remains the fallback.
        val stoppedGracefully = runCatching {
            tun2SocksProcessManager.stop(
                gracePeriodMillis = Tun2proxyPassiveStopGracePeriodMillis,
                signalNative = true,
            )
        }.onFailure { error ->
            lastStopComplete = false
            Log.w(Tag, "Failed to request tun2proxy shutdown", error)
        }.getOrDefault(false)
        runCatching {
            interfaceToClose?.close()
        }.onFailure { error ->
            lastStopComplete = false
            vpnInterface = interfaceToClose
            Log.w(Tag, "Failed to close VPN interface", error)
        }
        runCatching {
            val stopped = stoppedGracefully || tun2SocksProcessManager.stop(
                gracePeriodMillis = Tun2proxyForcedStopGracePeriodMillis,
                signalNative = true,
            )
            if (!stopped) {
                lastStopComplete = false
                Log.w(Tag, "tun2proxy did not stop after VPN interface close")
            }
        }.onFailure { error ->
            lastStopComplete = false
            Log.w(Tag, "Failed to stop tun2proxy", error)
        }
        runCatching {
            xrayProcessManager.stop()
        }.onFailure { error ->
            lastStopComplete = false
            Log.w(Tag, "Failed to stop Xray", error)
        }
        runCatching {
            stormDnsProcessManager.stop()
        }.onFailure { error ->
            lastStopComplete = false
            Log.w(Tag, "Failed to stop StormDNS", error)
        }
        runCatching {
            amneziaWgBackend.stop()
        }.onFailure { error ->
            lastStopComplete = false
            Log.w(Tag, "Failed to stop AmneziaWG", error)
        }
        if (!lastStopComplete) {
            WhiteZiaRuntimeStateStore.markFailed(applicationContext, WhiteZiaRuntimeStateStore.ModeVpn,
                "Не удалось полностью остановить предыдущий VPN туннель", currentSessionId)
            return@synchronized
        }
        val failureMessage = runtimeFailureMessage
        if (restartingSettings != null && failureMessage == null && connectionRequested) {
            // Preserve Starting while the old TUN closes and the network settles.
        } else if (failureMessage == null) {
            WhiteZiaRuntimeStateStore.markStopped(
                context = applicationContext,
                mode = WhiteZiaRuntimeStateStore.ModeVpn,
                sessionId = currentSessionId,
                message = "VPN service stopped",
            )
        } else {
            WhiteZiaRuntimeStateStore.markFailed(
                context = applicationContext,
                mode = WhiteZiaRuntimeStateStore.ModeVpn,
                sessionId = currentSessionId,
                message = failureMessage,
            )
        }
    }

    private fun startTrafficKeepalive(resolvedSettings: ResolvedWhiteZiaSettings) {
        stopTrafficKeepalive()
        if (!resolvedSettings.trafficWarmupEnabled) {
            return
        }
        keepaliveJob = serviceScope.launch {
            var successfulWarmupProbes = 0
            repeat(resolvedSettings.trafficWarmupProbeCount) { index ->
                if (!isActive || stopping) {
                    return@launch
                }
                if (WhiteZiaTrafficWarmup.runProbe(resolvedSettings)) {
                    successfulWarmupProbes += 1
                }
                if (index < resolvedSettings.trafficWarmupProbeCount - 1) {
                    delay(TrafficWarmupProbeSpacingMillis)
                }
            }
            if (successfulWarmupProbes > 0) {
                logInfo("Traffic warmup completed")
            }
            while (isActive && !stopping) {
                delay(resolvedSettings.trafficKeepaliveIntervalSeconds * 1_000L)
                WhiteZiaTrafficWarmup.runProbe(resolvedSettings)
            }
        }
    }

    private fun stopTrafficKeepalive() {
        keepaliveJob?.cancel()
        keepaliveJob = null
    }

    private fun selectVpnSocksHost(listenIp: String): String {
        val host = listenIp.trim().removeSurrounding("[", "]")
        return when (host) {
            "", "0.0.0.0" -> "127.0.0.1"
            "::" -> "::1"
            else -> host
        }
    }

    private fun Builder.configureSplitTunnelApplications(
        splitTunnelMode: String,
        splitTunnelPackages: List<String>,
    ) {
        val selectedPackages = splitTunnelPackages
            .asSequence()
            .map(String::trim)
            .filter { it.isNotEmpty() && it != packageName }
            .distinct()
            .toList()

        when (splitTunnelMode) {
            WhiteZiaOptions.SplitTunnelModeInclude -> {
                if (selectedPackages.isEmpty()) {
                    excludeWhiteZiaApp()
                    logWarning("No split tunnel apps selected; using full-device VPN routing")
                    return
                }

                val allowedCount = selectedPackages.count { appPackage ->
                    tryAddAllowedApplication(appPackage)
                }
                if (allowedCount == 0) {
                    throw IllegalStateException("No selected split tunnel apps could be routed through the VPN")
                }
                logInfo("Split tunnel routes $allowedCount selected app(s) through the VPN")
            }
            WhiteZiaOptions.SplitTunnelModeExclude -> {
                excludeWhiteZiaApp()
                val excludedCount = selectedPackages.count { appPackage ->
                    tryAddDisallowedApplication(appPackage, "Unable to bypass $appPackage")
                }
                logInfo("Split tunnel bypasses $excludedCount selected app(s)")
            }
            else -> {
                excludeWhiteZiaApp()
            }
        }
    }

    private fun Builder.excludeWhiteZiaApp() {
        tryAddDisallowedApplication(packageName, "Unable to exclude WhiteZia app from VPN")
    }

    private fun Builder.tryAddAllowedApplication(appPackage: String): Boolean {
        return runCatching {
            addAllowedApplication(appPackage)
            true
        }.getOrElse { error ->
            logWarning("Unable to route $appPackage through VPN: ${error.message ?: error::class.java.simpleName}")
            false
        }
    }

    private fun Builder.tryAddDisallowedApplication(appPackage: String, message: String): Boolean {
        return runCatching {
            addDisallowedApplication(appPackage)
            true
        }.getOrElse { error ->
            logWarning("$message: ${error.message ?: error::class.java.simpleName}")
            false
        }
    }

    private fun logInfo(message: String) {
        Log.i(Tag, message)
        updateTrafficNotification(message)
        WhiteZiaVpnEvents.log(currentSessionId, message)
        sendVpnEvent(BroadcastTypeLog, message)
    }

    private fun logWarning(message: String) {
        Log.w(Tag, message)
        updateTrafficNotification(message)
        WhiteZiaVpnEvents.log(currentSessionId, message)
        sendVpnEvent(BroadcastTypeLog, message)
    }

    private fun updateTrafficNotification(message: String) {
        if (!runtimeReady) {
            return
        }
        val stats = parseStormDnsTrafficStatsLine(message) ?: return
        val now = System.currentTimeMillis()
        if (now - lastTrafficNotificationUpdateMillis < TrafficNotificationUpdateIntervalMillis) {
            return
        }
        lastTrafficNotificationUpdateMillis = now
        updateForegroundNotification(formatTrafficNotificationText(stats))
    }

    private fun logError(message: String, error: Throwable) {
        Log.e(Tag, message, error)
        reportFailure("$message: ${error.message ?: error::class.java.simpleName}")
    }

    private suspend fun failAndStopVpn(failedSessionId: String, generation: Long, message: String, error: Throwable? = null) {
        currentCoroutineContext().ensureActive()
        if (requestGeneration != generation || requestedSessionId != failedSessionId) return
        val recoveryRequest = if (connectionRequested && sessionWasReady && currentSessionId == requestedSessionId) {
            RuntimeLaunchRequestStore.load(applicationContext, currentSessionId)
        } else null
        if (recoveryRequest != null) {
            val retryDelayMillis = reconnectBackoff.afterFailureMillis()
            networkRecoveryPending = true
            nextRecoveryAtMillis = SystemClock.elapsedRealtime() + retryDelayMillis
            logWarning("VPN временно недоступен: ${error?.message ?: message}; повтор через ${retryDelayMillis / 1_000} с")
            runtimeFailureMessage = null
            withContext(NonCancellable) {
                VpnRuntimeOwnership.mutex.withLock { stopVpn(restartingSettings = activeSettings ?: recoveryRequest.settings) }
            }
            if (connectionRequested) {
                stopping = false
                markNetworkRecoveryWaiting(recoveryRequest.settings, currentSessionId)
                sendVpnEvent(BroadcastTypeReconnecting, "Восстанавливаю VPN после потери связи")
                updateForegroundNotification("Восстановление подключения")
            }
            return
        }
        if (error == null) {
            Log.w(Tag, message)
        } else {
            Log.e(Tag, message, error)
        }
        val failureMessage = if (error == null) {
            message
        } else {
            "$message: ${error.message ?: error::class.java.simpleName}"
        }
        val claimed = withContext(Dispatchers.Main.immediate) {
            if (requestGeneration != generation || requestedSessionId != failedSessionId) false else {
                connectionRequested = false
                runtimeReady = false
                lastTrafficNotificationUpdateMillis = 0L
                runtimeFailureMessage = failureMessage
                updateForegroundNotification("VPN disconnected")
                true
            }
        }
        if (!claimed) return
        withContext(NonCancellable) { VpnRuntimeOwnership.mutex.withLock { stopVpn() } }
        withContext(Dispatchers.Main.immediate) {
            if (!connectionRequested && requestedSessionId == failedSessionId && requestGeneration == generation) {
                reportFailure(failureMessage)
                exitForeground()
                stopSelf()
            }
        }
    }

    private fun reportFailure(message: String) {
        WhiteZiaVpnEvents.failed(currentSessionId, message)
        sendVpnEvent(BroadcastTypeFailed, message)
    }

    private fun reportReady(message: String) {
        sessionWasReady = true
        networkRecoveryPending = false
        releaseRecoveryCpu()
        nextRecoveryAtMillis = 0L
        reconnectBackoff.reset()
        networkChanges.trySend(Unit)
        Log.i(Tag, message)
        WhiteZiaVpnEvents.ready(currentSessionId, message)
        sendVpnEvent(BroadcastTypeReady, message)
    }

    private fun markNetworkRecoveryWaiting(settings: WhiteZiaSettings, sessionId: String) {
        if (!connectionRequested || stopping || sessionId != requestedSessionId || sessionId != currentSessionId) return
        val selected = activeSettings ?: settings
        val transport = transportLabel(selected.transportMode)
        WhiteZiaRuntimeStateStore.markStarting(
            applicationContext, selected, sessionId, "Reconnecting $transport VPN: waiting for network recovery", recoveryWaiting = true,
            transportMode = runtimeTransport(selected),
        )
    }

    @Synchronized
    private fun holdRecoveryCpu() {
        if (!connectionRequested || stopping) return
        runCatching {
            val lock = recoveryWakeLock ?: getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:NetworkRecovery")
                .apply { setReferenceCounted(false) }.also { recoveryWakeLock = it }
            lock.acquire(60_000L)
        }.onFailure { Log.w(Tag, "Could not hold CPU during network recovery", it) }
    }

    @Synchronized
    private fun releaseRecoveryCpu() {
        recoveryWakeLock?.let { lock -> runCatching { if (lock.isHeld) lock.release() } }
        recoveryWakeLock = null
    }

    private fun sendVpnEvent(type: String, message: String) {
        sendBroadcast(
            Intent(BroadcastAction)
                .setPackage(packageName)
                .putExtra(BroadcastExtraType, type)
                .putExtra(BroadcastExtraSessionId, currentSessionId)
                .putExtra(BroadcastExtraMessage, message),
        )
    }

    companion object {
        private const val Tag = "WhiteZiaVpnService"
        const val BroadcastAction = "shop.whitezia.client.vpn.EVENT"
        const val BroadcastExtraType = "shop.whitezia.client.vpn.extra.TYPE"
        const val BroadcastExtraSessionId = "shop.whitezia.client.vpn.extra.SESSION_ID"
        const val BroadcastExtraMessage = "shop.whitezia.client.vpn.extra.MESSAGE"
        const val BroadcastTypeLog = "log"
        const val BroadcastTypeReady = "ready"
        const val BroadcastTypeReconnecting = "reconnecting"
        const val BroadcastTypeFailed = "failed"
        private const val ActionStart = "shop.whitezia.client.vpn.START"
        private const val ActionReconfigure = "shop.whitezia.client.vpn.RECONFIGURE"
        private const val ActionStop = "shop.whitezia.client.vpn.STOP"
        private const val ExtraSessionId = "shop.whitezia.client.vpn.extra.SESSION_ID"
        const val TunIpv4Address = "172.19.0.1"
        private const val TunIpv4PrefixLength = 30
        private const val TunDnsServer = "172.19.0.2"
        private const val VpnMtu = 1500
        private const val VpnMtu32Bit = 1280
        private const val Tun2proxyPassiveStopGracePeriodMillis = 1_000L
        private const val Tun2proxyForcedStopGracePeriodMillis = 4_000L
        private const val PreviousRuntimeStopTimeoutMillis = 3_000L
        private const val PreviousRuntimeStopPollMillis = 100L
        private const val ProxyStartupTimeoutMillis = 15_000L
        private const val XrayNetworkReadyTimeoutMillis = 4_000L
        private const val NetworkRecoveryPollMillis = 5_000L
        private const val TunnelLivenessIntervalMillis = 30_000L
        private const val XrayNetworkReadyPollMillis = 200L
        private const val XrayNetworkStableWindowMillis = 400L
        private const val TrafficNotificationUpdateIntervalMillis = 1_000L
        private const val TrafficWarmupProbeSpacingMillis = 300L
        private const val NotificationId = 3101
        private const val NotificationChannelId = "whitezia_vpn"

        fun start(
            context: Context,
            sessionId: String,
            serverProfile: StormDnsServerProfile? = null,
            settings: WhiteZiaSettings? = null,
            reconfigureOnly: Boolean = false,
        ) {
            val launchSettings = settings ?: WhiteZiaSettingsStore(context).load()
            val launchServerProfile = serverProfile ?: selectServerProfile(launchSettings)
            val stormDnsProfileRequired = launchSettings.resolve().connectionMode != "vpn" ||
                launchSettings.transportMode == WhiteZiaOptions.TransportDns
            if (stormDnsProfileRequired && launchServerProfile == null) {
                throw IllegalStateException("No StormDNS server profile configured")
            }
            RuntimeLaunchRequestStore.save(
                context = context,
                requestId = sessionId,
                serverProfile = launchServerProfile,
                settings = launchSettings,
            )
            val intent = Intent(context, WhiteZiaVpnService::class.java)
                .setAction(if (reconfigureOnly) ActionReconfigure else ActionStart)
                .putExtra(ExtraSessionId, sessionId)
            ContextCompat.startForegroundService(context, intent)
        }

        private fun selectServerProfile(settings: WhiteZiaSettings): StormDnsServerProfile? {
            val connectionProfile = settings.selectedConnectionProfile()
            val domain = connectionProfile.customServerDomain
                .trim()
                .trimEnd('.')
            val encryptionKey = connectionProfile.customServerEncryptionKey.trim()
            if (domain.isBlank() || encryptionKey.isBlank()) {
                return null
            }
            return StormDnsServerProfile(
                id = "custom",
                label = "Custom StormDNS Server",
                domain = domain,
                encryptionKey = encryptionKey,
                encryptionMethod = connectionProfile.customServerEncryptionMethod.coerceIn(0, 5),
            )
        }

        fun stop(context: Context) {
            runCatching {
                context.startService(
                    Intent(context, WhiteZiaVpnService::class.java)
                        .setAction(ActionStop),
                )
            }.onFailure { error ->
                Log.w(Tag, "Failed to request VPN service stop", error)
                runCatching {
                    context.stopService(Intent(context, WhiteZiaVpnService::class.java))
                }.onFailure { stopError ->
                    Log.w(Tag, "Failed to stop VPN service", stopError)
                }
            }
        }

    }
}
