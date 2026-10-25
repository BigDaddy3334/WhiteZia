package shop.whitezia.client.vpn

import shop.whitezia.client.diagnostics.DiagnosticPrivacy
import shop.whitezia.client.diagnostics.redactDiagnostics

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
import shop.whitezia.client.model.CottenDnsMode
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
import shop.whitezia.client.runtime.awaitProxyStartup
import shop.whitezia.client.runtime.formatTrafficNotificationText
import shop.whitezia.client.runtime.parseStormDnsTrafficStatsLine
import shop.whitezia.client.runtime.VpnDesiredSessionStore
import shop.whitezia.client.runtime.acceptsVpnStop
import shop.whitezia.client.runtime.acceptsVpnRevocation
import shop.whitezia.client.storm.CottenDnsEngineContract
import shop.whitezia.client.storm.CottenDnsStartupState
import shop.whitezia.client.storm.StormDnsProcessManager
import shop.whitezia.client.xray.XrayProcessManager

class WhiteZiaVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private data class VpnInterfaceConfiguration(
        val mtu: Int,
        val splitTunnelMode: String,
        val splitTunnelPackages: List<String>,
    )
    private data class VpnReaderConfiguration(
        val vpn: VpnInterfaceConfiguration,
        val socksHost: String,
        val socksPort: Int,
        val socksUsername: String?,
        val socksPassword: String?,
        val udpEnabled: Boolean,
    )
    private class RoutingAttempt(val sessionId: String) {
        val failure = AtomicReference<String?>(null)
    }
    private class VpnReaderBinding(
        val tun: ParcelFileDescriptor,
        val configuration: VpnReaderConfiguration,
        attempt: RoutingAttempt,
    ) {
        val attempt = AtomicReference(attempt)
    }
    @Volatile
    private var vpnReaderBinding: VpnReaderBinding? = null
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
    private var restoredSession = false
    @Volatile
    private var networkRecoveryPending = false
    @Volatile
    private var nextRecoveryAtMillis = 0L
    private var recoveryWakeLock: PowerManager.WakeLock? = null
    private val connectivityManager by lazy { getSystemService(ConnectivityManager::class.java) }
    private val desiredSession by lazy { VpnDesiredSessionStore(applicationContext.noBackupFilesDir) }
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
        val binder = super.onBind(intent)
        if (binder != null && intent.action == SERVICE_INTERFACE) resumePreviouslyReadyBoundSession()
        return binder
    }

    private fun resumePreviouslyReadyBoundSession() {
        // Some Android builds recreate only the BIND_AUTO_CREATE connection after
        // a dead TUN loses its started-service record. No sticky Intent follows.
        // A bare binding is not a new Connect request: only resume a session that
        // previously reached Ready, using its exact persisted launch snapshot.
        if (connectionRequested) return
        val desired = desiredSession.read()?.takeIf { it.wasReady } ?: return
        if (requestedSessionId.isNotBlank() && requestedSessionId != desired.sessionId) return
        val request = RuntimeLaunchRequestStore.load(applicationContext, desired.sessionId)
        if (request == null) {
            markBoundRestoreDispatchFailure(desired.sessionId, "Сохранённый профиль для восстановления VPN недоступен")
            return
        }
        if (!desiredSession.withCurrent(desired) {
            WhiteZiaRuntimeStateStore.markStarting(applicationContext, request.settings, desired.sessionId,
                "Восстановление VPN после перезапуска процесса", recoveryWaiting = true)
        }) return
        try {
            ContextCompat.startForegroundService(this, Intent(this, WhiteZiaVpnService::class.java)
                .setAction(ActionRestore).putExtra(ExtraSessionId, desired.sessionId))
            Log.i(Tag, "Restoring requested VPN after bound service recreation")
        } catch (error: Exception) {
            // Preserve user intent for a later permitted start, but never keep a
            // stale Ready status when Android rejects a background FGS launch.
            Log.w(Tag, "Could not dispatch bound VPN restoration", error)
            markBoundRestoreDispatchFailure(desired.sessionId,
                "Android не разрешил восстановить VPN в фоне. Откройте приложение для подключения")
        }
    }

    private fun markBoundRestoreDispatchFailure(sessionId: String, message: String) {
        val expected = desiredSession.read()
        when (expected?.sessionId) {
            sessionId -> desiredSession.withCurrent(expected) {
                WhiteZiaRuntimeStateStore.markFailed(applicationContext,
                    WhiteZiaRuntimeStateStore.ModeVpn, message, sessionId)
            }
            null -> desiredSession.withCurrent(null) {
                WhiteZiaRuntimeStateStore.markStopped(applicationContext,
                    WhiteZiaRuntimeStateStore.ModeVpn, sessionId)
            }
            else -> Unit // A newer request owns its status and snapshot.
        }
    }

    override fun onCreate() {
        super.onCreate()
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                if (connectionRequested && (runtimeReady || networkRecoveryPending)) holdRecoveryCpu()
                networkChanges.trySend(Unit)
            }
            override fun onLost(network: Network) {
                if (networkReconnectPolicy.networkLost(network.toString())) holdRecoveryCpu()
                networkChanges.trySend(Unit)
            }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                val recoveryEvent = networkReconnectPolicy.capabilitiesChanged(
                    network.toString(),
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
                )
                if (recoveryEvent || networkRecoveryPending) holdRecoveryCpu()
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
                sendVpnEvent(BroadcastTypeProgress, "Сеть потеряна, жду восстановления", "waiting-network")
                updateForegroundNotification("Ожидание сети")
            }
            markNetworkRecoveryWaiting(request.settings, sessionId)
            return
        }
        if (request.settings.manualMode && request.settings.forceDnsTunnel && hasActiveWifiNetwork()) {
            if (!networkRecoveryPending) {
                logInfo("CottenDNS: выключите Wi-Fi для восстановления подключения")
                sendVpnEvent(BroadcastTypeProgress, "Для CottenDNS отключите Wi-Fi", "waiting-action")
                networkRecoveryPending = true
            }
            markNetworkRecoveryWaiting(request.settings, sessionId)
            return
        }
        // An offline attempt may be waiting indefinitely for the DNS fallback's
        // mobile network. A restored physical network must cancel that attempt
        // and retry Xray, even though the startup job has not finished yet.
        if (startJob?.isActive == true && !networkRecovered) {
            markNetworkRecoveryWaiting(request.settings, sessionId)
            return
        }
        if (!networkRecovered && (!networkRecoveryPending || SystemClock.elapsedRealtime() < nextRecoveryAtMillis)) {
            if (networkRecoveryPending) markNetworkRecoveryWaiting(request.settings, sessionId)
            return
        }
        if (!isConnectionRequested(sessionId) || currentSessionId != sessionId) return
        networkRecoveryPending = true
        logInfo("Восстановление сети: заново проверяю доступные VPN маршруты")
        startVpn(sessionId, settleDelayMillis = 3_000L)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Sticky restarts carry no Intent. Resume only an explicitly requested session.
        val systemRestore = intent == null || intent.action == SERVICE_INTERFACE
        val command = if (systemRestore) desiredSession.read()?.let { desired ->
            Intent(this, WhiteZiaVpnService::class.java).setAction(ActionStart)
                .putExtra(ExtraSessionId, desired.sessionId)
        } else intent
        return when (command?.action) {
            ActionStop -> {
                val stopSessionId = command.getStringExtra(ExtraSessionId).orEmpty()
                if (!acceptsVpnStop(requestedSessionId, stopSessionId, desiredSession.read()?.sessionId)) {
                    Log.i(Tag, "Ignoring STOP for a superseded VPN session")
                    return continuationMode()
                }
                // An empty old STOP must not clear a START written after the policy check.
                desiredSession.clear(stopSessionId.ifBlank { requestedSessionId })
                VpnRuntimeBinding.notifyCancellation(applicationContext)
                requestGeneration += 1
                connectionRequested = false
                networkRecoveryPending = false
                restoredSession = false
                releaseRecoveryCpu()
                runtimeFailureMessage = null
                requestStop(startId)
                START_NOT_STICKY
            }
            ActionStart, ActionReconfigure, ActionRestore -> {
                val sessionId = command.getStringExtra(ExtraSessionId).orEmpty()
                if (sessionId.isBlank()) {
                    Log.w(Tag, "Ignoring VPN start without session ID")
                    if (!connectionRequested) stopSelfResult(startId)
                    return continuationMode()
                }
                val desired = desiredSession.read()
                if (desired?.sessionId != sessionId) {
                    Log.i(Tag, "Ignoring VPN start cancelled or superseded before delivery")
                    if (command.action == ActionRestore && desired == null && !connectionRequested) {
                        desiredSession.withCurrent(null) {
                            WhiteZiaRuntimeStateStore.markStopped(applicationContext,
                                WhiteZiaRuntimeStateStore.ModeVpn, sessionId)
                        }
                    }
                    if (!connectionRequested) stopSelfResult(startId)
                    return continuationMode()
                }
                val redelivered = systemRestore || command.action == ActionRestore || flags and START_FLAG_REDELIVERY != 0
                if (redelivered && connectionRequested && requestedSessionId == sessionId) {
                    // A binding restore and Android's sticky delivery may both
                    // arrive. Do not cancel/restart a live or still-starting reader.
                    return continuationMode()
                }
                if (command.action == ActionReconfigure && !redelivered && (!connectionRequested || requestedSessionId != sessionId)) {
                    Log.i(Tag, "Ignoring settings update for an inactive VPN session")
                    if (!connectionRequested) stopSelfResult(startId)
                    return continuationMode()
                }
                try {
                    requestGeneration += 1
                    if (requestedSessionId != sessionId) {
                        sessionWasReady = desired.wasReady
                        restoredSession = redelivered
                        networkRecoveryPending = redelivered
                        reconnectBackoff.reset()
                    }
                    connectionRequested = true
                    requestedSessionId = sessionId
                    enterForeground("Preparing WhiteZia")
                    startVpn(sessionId, settleDelayMillis = if (runtimeReady) 3_000L else 0L)
                    START_STICKY
                } catch (error: Exception) {
                    logError("Failed to start foreground VPN service", error)
                    connectionRequested = false
                    desiredSession.clear(sessionId)
                    VpnRuntimeBinding.notifyCancellation(applicationContext)
                    requestStop(startId)
                    START_NOT_STICKY
                }
            }
            else -> {
                Log.w(Tag, "Ignoring unexpected VPN service action: ${command?.action ?: "null"}")
                if (!connectionRequested) stopSelfResult(startId)
                continuationMode()
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
        // A recreated bound service can be revoked before its RESTORE reaches
        // onStartCommand, when requestedSessionId is still empty. Capture the
        // durable owner now so a queued restore cannot undo the system's Stop.
        val revokedSessionId = requestedSessionId.ifBlank { desiredSession.read()?.sessionId.orEmpty() }
        serviceScope.launch(Dispatchers.Main.immediate) {
            val desired = desiredSession.read()
            if (!acceptsVpnRevocation(requestedSessionId, revokedSessionId, desired?.sessionId)) {
                Log.i(Tag, "Ignoring VPN revocation for a superseded session")
                return@launch
            }
            val generation = ++requestGeneration
            connectionRequested = false
            desiredSession.clear(revokedSessionId)
            VpnRuntimeBinding.notifyCancellation(applicationContext)
            releaseRecoveryCpu()
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
            Intent(this, WhiteZiaVpnService::class.java).setAction(ActionStop)
                .putExtra(ExtraSessionId, requestedSessionId),
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
        if (!isConnectionRequested(sessionId)) return
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
                if (!isConnectionRequested(sessionId)) return@launch
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
                                    (result.latencyMillis?.let { "$it ms (TCP/TLS)" } ?: "TCP/TLS не подтверждён (${result.reason}); сохранён для резервной попытки"))
                            }).prepare(candidates)
                        }
                        override suspend fun expand(candidate: VpnConnectionCandidate): List<VpnConnectionCandidate> {
                            if (candidate.settings.transportMode != WhiteZiaOptions.TransportDns) return listOf(candidate)
                            currentCoroutineContext().ensureActive()
                            if (!isConnectionRequested(sessionId)) throw CancellationException("VPN stop requested")
                            activeSettings = candidate.settings
                            awaitDnsNetworkReady(sessionId, candidate.settings)
                            return serviceResolvers.preparePools(candidate.settings).map { candidate.copy(settings = it) }
                        }
                        override suspend fun start(candidate: VpnConnectionCandidate) {
                            currentCoroutineContext().ensureActive()
                            if (!isConnectionRequested(sessionId)) throw CancellationException("VPN stop requested")
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
                                    // expand() captured the current physical network after awaited cleanup.
                                    // Do not prepare again here: that would reinsert physical DNS in a public fallback.
                                    val profile = selectServerProfile(selected) ?: launchRequest.serverProfile
                                    check(selected.resolve().resolverEntries.isNotEmpty()) { "CottenDNS resolvers are missing" }
                                    startStormDnsAndVpn(sessionId, requireNotNull(profile) { "CottenDNS server profile is missing" }, selected, selected.resolve())
                                }
                            }
                        }
                        override suspend fun verify(candidate: VpnConnectionCandidate): Boolean {
                            val selected = activeSettings ?: candidate.settings
                            requireRoutingAlive(selected)
                            logInfo("Проверяю ${transportLabel(selected.transportMode)} через туннель")
                            val healthy = ConnectionProbeClient().isHttpHealthy(
                                ::logInfo, selected.resolve().listenPort,
                                requireRelayEgress = selected.transportMode == WhiteZiaOptions.TransportXray,
                                requireDnsContent = selected.transportMode == WhiteZiaOptions.TransportDns && CottenDnsMode.requiresStrongDnsHealth(selected.cottenDnsMode, selected.customResolversEnabled),
                                useCottenMaxHealthReserve = selected.transportMode == WhiteZiaOptions.TransportDns && CottenDnsMode.normalize(selected.cottenDnsMode) == CottenDnsMode.Max,
                            )
                            currentCoroutineContext().ensureActive()
                            requireRoutingAlive(selected)
                            return healthy
                        }
                    },
                    onFailure = { candidate, error ->
                        logWarning("${transportLabel(candidate.settings.transportMode)} ${candidate.nodeId}: ${error.message}; пробую следующий маршрут")
                    },
                    onState = { state ->
                        if (state.phase in setOf(VpnConnectionPhase.Preparing, VpnConnectionPhase.Connecting,
                                VpnConnectionPhase.Checking, VpnConnectionPhase.Stopping)) {
                            val phase = when (state.phase) {
                                VpnConnectionPhase.Preparing -> "preparing"
                                VpnConnectionPhase.Checking -> "checking"
                                VpnConnectionPhase.Stopping -> "stopping"
                                else -> "starting"
                            }
                            sendVpnEvent(BroadcastTypeProgress,
                                "Маршрут ${state.attempt}/${state.totalAttempts}: ${transportLabel(state.transport)}", phase)
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
                withContext(NonCancellable) { VpnRuntimeOwnership.mutex.withLock { stopVpnForRetry() } }
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
                val message = if (hasActiveWifiNetwork()) "Выключите Wi-Fi для CottenDNS" else "CottenDNS: жду мобильную сеть"
                logInfo(message)
                sendVpnEvent(BroadcastTypeProgress, message, if (hasActiveWifiNetwork()) "waiting-action" else "waiting-network")
                updateForegroundNotification(message)
                notified = true
            }
            WhiteZiaRuntimeStateStore.markStarting(applicationContext, settings, sessionId,
                "CottenDNS: ожидание мобильной сети", recoveryWaiting = true)
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
        xrayProcessManager.start(
            settings,
            resolvedSettings,
            bootstrapNetwork = connectivityManager.physicalInternetNetwork()
                ?: error("No physical network for Xray bootstrap"),
        ) { line ->
            logInfo(line)
            detectXrayStartupFailure(line)?.let { failure ->
                startupFailure.compareAndSet(null, failure)
            }
        }
        currentCoroutineContext().ensureActive()
        waitForProxyPort(
            runtimeName = "Xray",
            sessionId = sessionId,
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
        val startupState = CottenDnsStartupState(resolvedSettings.cottenDnsMode)
        stormDnsProcessManager.start(serverProfile, settings) { line ->
            logInfo(line)
            startupState.observe(line)
            detectStormDnsStartupFailure(line)?.let { failure ->
                startupFailure.compareAndSet(null, failure)
            }
        }
        currentCoroutineContext().ensureActive()
        waitForProxyPort(
            runtimeName = "CottenDNS",
            sessionId = sessionId,
            timeoutMillis = CottenDnsEngineContract.SocksStartupTimeoutMillis,
            listenPort = resolvedSettings.listenPort,
            startupFailure = { startupState.failureOrNull() ?: startupFailure.get() },
            isRunning = { stormDnsProcessManager.isRunning() },
            exitCode = { stormDnsProcessManager.exitCodeOrNull() },
            isProtocolReady = startupState::isProtocolReady,
        )
        logInfo("CottenDNS SOCKS proxy is ready")
        startVpnRouting(
            sessionId = sessionId,
            settings = settings,
            resolvedSettings = resolvedSettings,
            readyMessage = "CottenDNS VPN routing started",
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
                    check(ConnectionProbeClient().isHttpHealthy(::logInfo, next.resolve().listenPort,
                        requireDnsContent = CottenDnsMode.requiresStrongDnsHealth(next.cottenDnsMode, next.customResolversEnabled),
                        useCottenMaxHealthReserve = next.transportMode == WhiteZiaOptions.TransportDns && CottenDnsMode.normalize(next.cottenDnsMode) == CottenDnsMode.Max)) { "DNS health-check не пройден" }
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
                val healthy = ConnectionProbeClient().isHttpHealthy(
                    {}, settings.resolve().listenPort,
                    requireRelayEgress = settings.transportMode == WhiteZiaOptions.TransportXray,
                    requireDnsContent = settings.transportMode == WhiteZiaOptions.TransportDns && CottenDnsMode.requiresStrongDnsHealth(settings.cottenDnsMode, settings.customResolversEnabled),
                    useCottenMaxHealthReserve = settings.transportMode == WhiteZiaOptions.TransportDns && CottenDnsMode.normalize(settings.cottenDnsMode) == CottenDnsMode.Max,
                )
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
        else -> "CottenDNS"
    }

    private fun runtimeTransport(settings: WhiteZiaSettings): String = settings.transportMode

    private suspend fun waitForProxyPort(
        runtimeName: String,
        sessionId: String,
        listenPort: Int,
        startupFailure: () -> String?,
        isRunning: () -> Boolean,
        exitCode: () -> Int?,
        timeoutMillis: Long = ProxyStartupTimeoutMillis,
        isProtocolReady: () -> Boolean = { true },
    ) = awaitProxyStartup(
        runtimeName = runtimeName,
        timeoutMillis = timeoutMillis,
        startupFailure = startupFailure,
        isRunning = isRunning,
        exitCode = exitCode,
        isPortReady = { isProtocolReady() && canConnectToLocalPort(listenPort) },
        keepWaiting = { isConnectionRequested(sessionId) && currentSessionId == sessionId },
        monotonicMillis = SystemClock::elapsedRealtime,
    )

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
            val interfaceConfiguration = VpnInterfaceConfiguration(
                vpnMtu, resolvedSettings.splitTunnelMode, resolvedSettings.splitTunnelPackages.sorted(),
            )
            val udpEnabled = settings.transportMode != WhiteZiaOptions.TransportDns ||
                CottenDnsMode.normalize(resolvedSettings.cottenDnsMode) != CottenDnsMode.Max
            val readerConfiguration = VpnReaderConfiguration(
                interfaceConfiguration, socksHost, socksPort, socksUsername, socksPassword, udpEnabled,
            )
            val attempt = RoutingAttempt(sessionId)
            synchronized(stopLock) {
                if (stopping || currentSessionId != sessionId) {
                    throw CancellationException("VPN routing start was cancelled")
                }
                val previous = vpnInterface
                val reader = vpnReaderBinding
                if (previous != null && reader != null && reader.tun === previous &&
                    reader.configuration == readerConfiguration && tun2SocksProcessManager.isRunning()
                ) {
                    // The virtual DNS map belongs to this reader. Keep it with the TUN
                    // while only the upstream proxy/physical network is replaced.
                    reader.attempt.set(attempt)
                    routingStartupFailure = attempt.failure
                    logInfo("Reusing Android VPN interface")
                    logInfo("Reusing tun2proxy reader and virtual DNS mappings")
                } else {
                    // A new reader has an empty virtual DNS map, so it must also get a
                    // new Android network/DNS session even when routes are unchanged.
                    // Invalidate callbacks before stopping the old borrowed-fd reader.
                    vpnReaderBinding = null
                    check(stopTunReader()) { "Previous tun2proxy reader is still stopping" }
                    logInfo("Establishing Android VPN interface")
                    val replacement = vpnBuilder.establish()
                        ?: throw IllegalStateException("Failed to establish WhiteZia VPN interface")
                    vpnInterface = replacement
                    runCatching { previous?.close() }
                        .onFailure { Log.w(Tag, "Failed to close replaced VPN interface", it) }
                    val binding = VpnReaderBinding(replacement, readerConfiguration, attempt)
                    vpnReaderBinding = binding
                    routingStartupFailure = attempt.failure
                    logInfo("Android VPN interface established")
                    logInfo("Routing device traffic to SOCKS $socksHost:$socksPort")
                    tun2SocksProcessManager.start(
                        tunFileDescriptor = replacement.fd,
                        closeTunFileDescriptorOnDrop = false,
                        tunMtu = vpnMtu,
                        socksHost = socksHost,
                        socksPort = socksPort,
                        socksUsername = socksUsername,
                        socksPassword = socksPassword,
                        udpEnabled = udpEnabled,
                        onOutput = { line -> logInfo("tun2proxy: $line") },
                        onExit = { exitCode -> onTunReaderExit(binding, exitCode) },
                    )
                }
            }
            currentCoroutineContext().ensureActive()
            if (stopping || currentSessionId != sessionId) {
                throw CancellationException("VPN routing start was cancelled")
            }
            requireRoutingAlive(settings)
            logInfo(readyMessage)
        } catch (error: CancellationException) {
            stopVpnForRetry()
            throw error
        } catch (error: Exception) {
            stopVpnForRetry()
            throw IllegalStateException("Failed to start WhiteZia VPN routing", error)
        }
    }

    private fun onTunReaderExit(binding: VpnReaderBinding, exitCode: Int) {
        if (stopping) {
            Log.i(Tag, "tun2proxy stopped with code $exitCode")
            return
        }
        val message = "tun2proxy exited with code $exitCode"
        val attempt = binding.attempt.get()
        if (vpnReaderBinding === binding && vpnInterface === binding.tun &&
            currentSessionId == attempt.sessionId && routingStartupFailure === attempt.failure
        ) attempt.failure.compareAndSet(null, message)
        serviceScope.launch {
            // A retained reader can serve a newer startup/session by the time its
            // callback is dispatched. Recover only its current binding and attempt.
            val current = binding.attempt.get()
            if (!stopping && vpnReaderBinding === binding && vpnInterface === binding.tun &&
                currentSessionId == current.sessionId && routingStartupFailure === current.failure
            ) requestRuntimeRecovery(message, current.sessionId)
        }
    }

    private fun stopTunReader(): Boolean = tun2SocksProcessManager.stop(
        gracePeriodMillis = Tun2proxyPassiveStopGracePeriodMillis,
        signalNative = true,
    ) || tun2SocksProcessManager.stop(
        gracePeriodMillis = Tun2proxyForcedStopGracePeriodMillis,
        signalNative = true,
    )

    private fun stopVpnForRetry() {
        stopVpn(restartingSettings = activeSettings?.takeIf { connectionRequested })
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
        // Keep the reader's virtual DNS map with a retained TUN. Proxy connections
        // fail closed while the upstream is stopped; DNS identities remain stable.
        val keepInterface = restartingSettings != null && connectionRequested
        val keepReader = keepInterface && vpnReaderBinding?.tun === vpnInterface &&
            vpnReaderBinding != null && tun2SocksProcessManager.isRunning()
        val interfaceToClose = if (keepInterface) null else vpnInterface.also {
            vpnInterface = null
        }
        if (!keepReader) vpnReaderBinding = null
        lastStopComplete = true
        // Cancel native reads before closing their borrowed fd. During retries a failed
        // native stop must keep the TUN and prevent a second runner from starting.
        val stoppedGracefully = keepReader || runCatching {
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
                Log.w(Tag, if (keepInterface) "tun2proxy did not stop; retaining VPN interface" else "tun2proxy did not stop after VPN interface close")
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
            Log.w(Tag, "Failed to stop CottenDNS", error)
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
                message = diagnosticMessage(failureMessage),
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

    private fun diagnosticMessage(message: String): String =
        activeSettings?.redactDiagnostics(message) ?: DiagnosticPrivacy.redact(message)

    private fun logInfo(message: String) {
        val safeMessage = diagnosticMessage(message)
        Log.i(Tag, safeMessage)
        updateTrafficNotification(safeMessage)
        WhiteZiaVpnEvents.log(currentSessionId, safeMessage)
        sendVpnEvent(BroadcastTypeLog, safeMessage)
    }

    private fun logWarning(message: String) {
        val safeMessage = diagnosticMessage(message)
        Log.w(Tag, safeMessage)
        updateTrafficNotification(safeMessage)
        WhiteZiaVpnEvents.log(currentSessionId, safeMessage)
        sendVpnEvent(BroadcastTypeLog, safeMessage)
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
        Log.e(Tag, diagnosticMessage("$message: ${error.stackTraceToString()}"))
        reportFailure("$message: ${error.message ?: error::class.java.simpleName}")
    }

    private suspend fun failAndStopVpn(failedSessionId: String, generation: Long, message: String, error: Throwable? = null) {
        currentCoroutineContext().ensureActive()
        if (requestGeneration != generation || requestedSessionId != failedSessionId) return
        val recoveryRequest = if (isConnectionRequested(failedSessionId) && (sessionWasReady || restoredSession) && currentSessionId == requestedSessionId) {
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
            Log.e(Tag, diagnosticMessage("$message: ${error.stackTraceToString()}"))
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
                desiredSession.clear(failedSessionId)
                VpnRuntimeBinding.notifyCancellation(applicationContext)
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
        val safeMessage = diagnosticMessage(message)
        WhiteZiaVpnEvents.failed(currentSessionId, safeMessage)
        sendVpnEvent(BroadcastTypeFailed, safeMessage)
    }

    private fun reportReady(message: String) {
        desiredSession.markReady(currentSessionId)
        sessionWasReady = true
        restoredSession = false
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

    private fun isConnectionRequested(sessionId: String): Boolean =
        connectionRequested && requestedSessionId == sessionId && desiredSession.read()?.sessionId == sessionId

    private fun continuationMode(): Int = if (isConnectionRequested(requestedSessionId)) START_STICKY else START_NOT_STICKY

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

    private fun sendVpnEvent(type: String, message: String, phase: String = "") {
        sendBroadcast(
            Intent(BroadcastAction)
                .setPackage(packageName)
                .putExtra(BroadcastExtraType, type)
                .putExtra(BroadcastExtraSessionId, currentSessionId)
                .putExtra(BroadcastExtraMessage, diagnosticMessage(message))
                .putExtra(BroadcastExtraPhase, phase),
        )
    }

    companion object {
        private const val Tag = "WhiteZiaVpnService"
        const val BroadcastAction = "shop.whitezia.client.vpn.EVENT"
        const val BroadcastExtraType = "shop.whitezia.client.vpn.extra.TYPE"
        const val BroadcastExtraSessionId = "shop.whitezia.client.vpn.extra.SESSION_ID"
        const val BroadcastExtraMessage = "shop.whitezia.client.vpn.extra.MESSAGE"
        const val BroadcastExtraPhase = "shop.whitezia.client.vpn.extra.PHASE"
        const val BroadcastTypeProgress = "progress"
        const val BroadcastTypeLog = "log"
        const val BroadcastTypeReady = "ready"
        const val BroadcastTypeReconnecting = "reconnecting"
        const val BroadcastTypeFailed = "failed"
        private const val ActionStart = "shop.whitezia.client.vpn.START"
        private const val ActionReconfigure = "shop.whitezia.client.vpn.RECONFIGURE"
        internal const val ActionRestore = "shop.whitezia.client.vpn.RESTORE"
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
                throw IllegalStateException("No CottenDNS server profile configured")
            }
            val desiredStore = VpnDesiredSessionStore(context.applicationContext.noBackupFilesDir)
            if (reconfigureOnly && desiredStore.read()?.sessionId != sessionId) {
                Log.i(Tag, "Ignoring configuration for a superseded VPN session")
                return
            }
            RuntimeLaunchRequestStore.save(
                context = context,
                requestId = sessionId,
                serverProfile = launchServerProfile,
                settings = launchSettings,
            )
            // Reconfiguration updates the snapshot, never re-authorizes an old session.
            if (!reconfigureOnly) desiredStore.request(sessionId)
            VpnRuntimeBinding.hold(context, sessionId)
            val intent = Intent(context, WhiteZiaVpnService::class.java)
                .setAction(if (reconfigureOnly) ActionReconfigure else ActionStart)
                .putExtra(ExtraSessionId, sessionId)
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (error: Exception) {
                if (!reconfigureOnly) desiredStore.clear(sessionId)
                VpnRuntimeBinding.notifyCancellation(context)
                throw error
            }
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
                label = "Custom CottenDNS Server",
                domain = domain,
                encryptionKey = encryptionKey,
                encryptionMethod = connectionProfile.customServerEncryptionMethod.coerceIn(0, 5),
            )
        }

        fun stop(context: Context) {
            val stoppedSession = VpnDesiredSessionStore(context.applicationContext.noBackupFilesDir).clear()
            VpnRuntimeBinding.notifyCancellation(context)
            runCatching {
                context.startService(
                    Intent(context, WhiteZiaVpnService::class.java)
                        .setAction(ActionStop)
                        .putExtra(ExtraSessionId, stoppedSession?.sessionId.orEmpty()),
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
