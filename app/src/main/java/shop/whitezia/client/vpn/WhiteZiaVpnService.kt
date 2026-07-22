package shop.whitezia.client.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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
    private var runtimeReady = false
    private var lastTrafficNotificationUpdateMillis = 0L
    @Volatile
    private var currentSessionId = ""
    @Volatile
    private var runtimeFailureMessage: String? = null
    private val stopLock = Any()
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

    override fun onBind(intent: Intent): IBinder? {
        return super.onBind(intent)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return when (intent?.action) {
            ActionStop -> {
                runtimeFailureMessage = null
                requestStop(startId)
                START_NOT_STICKY
            }
            ActionStart -> {
                val sessionId = intent.getStringExtra(ExtraSessionId).orEmpty()
                if (sessionId.isBlank()) {
                    Log.w(Tag, "Ignoring VPN start without session ID")
                    stopSelfResult(startId)
                    return START_NOT_STICKY
                }
                try {
                    enterForeground("Preparing WhiteZia")
                    startVpn(sessionId)
                    START_NOT_STICKY
                } catch (error: Exception) {
                    logError("Failed to start foreground VPN service", error)
                    stopVpn()
                    exitForeground()
                    stopSelf()
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
        startJob?.cancel()
        stopVpn()
        exitForeground()
        serviceScope.cancel()
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
            stopVpn()
            exitForeground()
            stopSelfResult(startId)
        }
    }

    override fun onRevoke() {
        val hadActiveRuntime = runtimeReady || (
            WhiteZiaRuntimeStateStore.read(
                context = applicationContext,
                mode = WhiteZiaRuntimeStateStore.ModeVpn,
            )?.status in setOf(
                WhiteZiaRuntimeStateStore.StatusStarting,
                WhiteZiaRuntimeStateStore.StatusReady,
                WhiteZiaRuntimeStateStore.StatusStopping,
            )
        )
        val failureMessage = "VPN permission was revoked by Android"
        if (hadActiveRuntime) {
            runtimeFailureMessage = failureMessage
        }
        startJob?.cancel()
        stopVpn()
        if (hadActiveRuntime) {
            reportFailure(failureMessage)
        }
        exitForeground()
        stopSelf()
        super.onRevoke()
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

    private fun startVpn(sessionId: String) {
        val previousJob = startJob
        val pendingStopJob = stopJob
        startJob = serviceScope.launch {
            pendingStopJob?.join()
            if (stopJob === pendingStopJob) {
                stopJob = null
            }
            previousJob?.cancelAndJoin()
            try {
                val launchRequest = RuntimeLaunchRequestStore.loadOrRecover(applicationContext, sessionId)
                    ?: throw IllegalStateException("Runtime launch request is missing")
                val settings = launchRequest.settings.runtimeConnectionSettings()
                val resolvedSettings = settings.resolve()
                if (resolvedSettings.connectionMode != "vpn") {
                    throw IllegalStateException("VPN mode is not enabled")
                }
                stopVpn()
                currentSessionId = sessionId
                runtimeFailureMessage = null
                stopping = false
                runtimeReady = false
                lastTrafficNotificationUpdateMillis = 0L
                WhiteZiaProxyService.stop(applicationContext)
                waitForLocalPortToClose(resolvedSettings.listenPort)
                val serverProfile = launchRequest.serverProfile
                when (settings.transportMode) {
                    WhiteZiaOptions.TransportAuto -> {
                        if (settings.amneziaWgConfig.isBlank()) {
                            throw IllegalStateException("AmneziaWG config is missing")
                        }
                        WhiteZiaRuntimeStateStore.markStarting(
                            context = applicationContext,
                            settings = settings,
                            sessionId = sessionId,
                            message = "Starting AmneziaWG VPN",
                        )
                        if (!tryStartAmneziaWgVpn(sessionId, settings)) {
                            throw IllegalStateException("AmneziaWG unavailable")
                        }
                    }
                    WhiteZiaOptions.TransportXray -> {
                        if (settings.xrayUri.isBlank()) {
                            throw IllegalStateException("Xray URI is missing")
                        }
                        if (!awaitXrayMobileNetworkReady()) {
                            throw IllegalStateException("Xray requires mobile network without Wi-Fi")
                        }
                        WhiteZiaRuntimeStateStore.markStarting(
                            context = applicationContext,
                            settings = settings,
                            sessionId = sessionId,
                            message = "Starting Xray VPN",
                        )
                        if (!tryStartXrayVpn(sessionId, settings, resolvedSettings)) {
                            throw IllegalStateException("Xray unavailable")
                        }
                    }
                    WhiteZiaOptions.TransportDns -> {
                        if (resolvedSettings.resolverEntries.isEmpty()) {
                            throw IllegalStateException("StormDNS resolvers are missing")
                        }
                        val requiredServerProfile = requireNotNull(serverProfile) {
                            "StormDNS server profile is missing"
                        }
                        WhiteZiaRuntimeStateStore.markStarting(
                            context = applicationContext,
                            settings = settings,
                            sessionId = sessionId,
                            message = "Starting StormDNS VPN",
                        )
                        logInfo("Using custom StormDNS server")
                        logInfo("Starting internal SOCKS bridge")
                        startStormDnsAndVpn(sessionId, requiredServerProfile, settings, resolvedSettings)
                    }
                    else -> throw IllegalStateException("Unsupported transport mode: ${settings.transportMode}")
                }
            } catch (error: CancellationException) {
                stopVpn()
                throw error
            } catch (error: Exception) {
                failAndStopVpn("Failed to start WhiteZia VPN", error)
            }
        }
    }


    private suspend fun awaitXrayMobileNetworkReady(): Boolean {
        val deadline = System.currentTimeMillis() + XrayNetworkReadyTimeoutMillis
        var readySinceMillis = 0L
        while (System.currentTimeMillis() < deadline) {
            val now = System.currentTimeMillis()
            if (isXrayMobileNetworkReady()) {
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
        return isXrayMobileNetworkReady()
    }

    private fun isXrayMobileNetworkReady(): Boolean {
        return !hasActiveWifiNetwork() && isMobileNetworkAvailable()
    }

    private fun hasActiveWifiNetwork(): Boolean {
        val connectivityManager = getSystemService(ConnectivityManager::class.java) ?: return false
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun isMobileNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(ConnectivityManager::class.java) ?: return false
        return connectivityManager.allNetworks.any { network ->
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return@any false
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        }
    }

    private suspend fun tryStartAmneziaWgVpn(
        sessionId: String,
        settings: WhiteZiaSettings,
    ): Boolean {
        return try {
            startAmneziaWgVpn(sessionId, settings)
            true
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            logWarning("AmneziaWG unavailable: ${error.message ?: error::class.java.simpleName}")
            false
        }
    }

    private suspend fun tryStartXrayVpn(
        sessionId: String,
        settings: WhiteZiaSettings,
        resolvedSettings: ResolvedWhiteZiaSettings,
    ): Boolean {
        return try {
            startXrayAndVpn(sessionId, settings, resolvedSettings)
            xrayMonitorJob?.cancel()
            xrayMonitorJob = serviceScope.launch {
                try {
                    monitorXrayProcess(sessionId)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    if (!stopping && currentSessionId == sessionId) {
                        failAndStopVpn("Xray disconnected", error)
                    }
                }
            }
            true
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            logWarning("Xray unavailable: ${error.message ?: error::class.java.simpleName}")
            runCatching {
                xrayProcessManager.stop()
            }.onFailure { stopError ->
                Log.w(Tag, "Failed to stop unavailable Xray process", stopError)
            }
            false
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
        updateForegroundNotification("AmneziaWG VPN is active")
        runtimeReady = true
        WhiteZiaRuntimeStateStore.markReady(
            context = applicationContext,
            settings = settings,
            sessionId = sessionId,
            message = "AmneziaWG VPN routing started",
        )
        reportReady("AmneziaWG VPN routing started")
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
        monitorStormDnsProcess()
    }

    private suspend fun waitForProxyPort(
        runtimeName: String,
        listenPort: Int,
        startupFailure: () -> String?,
        isRunning: () -> Boolean,
        exitCode: () -> Int?,
    ) {
        val deadline = System.currentTimeMillis() + ProxyStartupTimeoutMillis
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
            if (System.currentTimeMillis() >= deadline) {
                throw IllegalStateException("$runtimeName SOCKS startup timed out")
            }
            delay(500)
        }
    }

    private suspend fun waitForLocalPortToClose(port: Int) {
        val deadline = System.currentTimeMillis() + PreviousRuntimeStopTimeoutMillis
        while (canConnectToLocalPort(port)) {
            if (System.currentTimeMillis() >= deadline) {
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

    private suspend fun monitorXrayProcess(sessionId: String) {
        while (!stopping && currentSessionId == sessionId) {
            if (!xrayProcessManager.isRunning()) {
                val exitCode = xrayProcessManager.exitCodeOrNull()
                throw IllegalStateException(
                    "Xray process exited while VPN was active${exitCode?.let { " (exit code $it)" }.orEmpty()}",
                )
            }
            delay(1_000)
        }
    }

    private suspend fun monitorStormDnsProcess() {
        while (true) {
            if (!stormDnsProcessManager.isRunning()) {
                val exitCode = stormDnsProcessManager.exitCodeOrNull()
                throw IllegalStateException(
                    "StormDNS process exited while VPN was active${exitCode?.let { " (exit code $it)" }.orEmpty()}",
                )
            }
            delay(1_000)
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
                        serviceScope.launch {
                            failAndStopVpn(message)
                        }
                    }
                },
            )
            currentCoroutineContext().ensureActive()
            if (stopping || currentSessionId != sessionId) {
                throw CancellationException("VPN routing start was cancelled")
            }
            updateForegroundNotification(notificationText)
            runtimeReady = true
            WhiteZiaRuntimeStateStore.markReady(
                context = applicationContext,
                settings = settings,
                sessionId = sessionId,
                message = readyMessage,
            )
            reportReady(readyMessage)
            startTrafficKeepalive(resolvedSettings)
        } catch (error: CancellationException) {
            stopVpn()
            throw error
        } catch (error: Exception) {
            stopVpn()
            throw IllegalStateException("Failed to start WhiteZia VPN routing", error)
        }
    }

    private fun stopVpn() = synchronized(stopLock) {
        stopping = true
        runtimeReady = false
        lastTrafficNotificationUpdateMillis = 0L
        WhiteZiaRuntimeStateStore.markStopping(
            context = applicationContext,
            mode = WhiteZiaRuntimeStateStore.ModeVpn,
            sessionId = currentSessionId,
            message = "VPN service stopping",
        )
        xrayMonitorJob?.cancel()
        xrayMonitorJob = null
        stopTrafficKeepalive()
        val interfaceToClose = vpnInterface
        vpnInterface = null
        runCatching {
            interfaceToClose?.close()
        }.onFailure { error ->
            Log.w(Tag, "Failed to close VPN interface", error)
        }
        runCatching {
            val stoppedAfterTunClose = tun2SocksProcessManager.stop(
                gracePeriodMillis = Tun2proxyPassiveStopGracePeriodMillis,
                signalNative = false,
            )
            val stopped = stoppedAfterTunClose || tun2SocksProcessManager.stop(
                gracePeriodMillis = Tun2proxyForcedStopGracePeriodMillis,
                signalNative = true,
            )
            if (!stopped) {
                Log.w(Tag, "tun2proxy did not stop after VPN interface close")
            }
        }.onFailure { error ->
            Log.w(Tag, "Failed to stop tun2proxy", error)
        }
        runCatching {
            xrayProcessManager.stop()
        }.onFailure { error ->
            Log.w(Tag, "Failed to stop Xray", error)
        }
        runCatching {
            stormDnsProcessManager.stop()
        }.onFailure { error ->
            Log.w(Tag, "Failed to stop StormDNS", error)
        }
        runCatching {
            amneziaWgBackend.stop()
        }.onFailure { error ->
            Log.w(Tag, "Failed to stop AmneziaWG", error)
        }
        val failureMessage = runtimeFailureMessage
        if (failureMessage == null) {
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

    private fun failAndStopVpn(message: String, error: Throwable? = null) {
        if (error == null) {
            Log.w(Tag, message)
        } else {
            Log.e(Tag, message, error)
        }
        runtimeReady = false
        lastTrafficNotificationUpdateMillis = 0L
        val failureMessage = if (error == null) {
            message
        } else {
            "$message: ${error.message ?: error::class.java.simpleName}"
        }
        runtimeFailureMessage = failureMessage
        updateForegroundNotification("VPN disconnected")
        stopVpn()
        reportFailure(failureMessage)
        exitForeground()
        stopSelf()
    }

    private fun reportFailure(message: String) {
        WhiteZiaVpnEvents.failed(currentSessionId, message)
        sendVpnEvent(BroadcastTypeFailed, message)
    }

    private fun reportReady(message: String) {
        Log.i(Tag, message)
        WhiteZiaVpnEvents.ready(currentSessionId, message)
        sendVpnEvent(BroadcastTypeReady, message)
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
        const val BroadcastTypeFailed = "failed"
        private const val ActionStart = "shop.whitezia.client.vpn.START"
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
                .setAction(ActionStart)
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
