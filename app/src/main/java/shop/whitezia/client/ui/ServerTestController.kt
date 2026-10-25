package shop.whitezia.client.ui

import android.content.Context
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicReference
import shop.whitezia.client.model.ConnectionStatus
import shop.whitezia.client.model.ServerTestResult
import shop.whitezia.client.model.ServerTestState
import shop.whitezia.client.model.ServerTestStatus
import shop.whitezia.client.model.StormDnsServerProfile
import shop.whitezia.client.model.WhiteZiaRuntimeProxy
import shop.whitezia.client.model.WhiteZiaSettings
import shop.whitezia.client.model.serverProfileFromConnectionProfile
import shop.whitezia.client.model.WhiteZiaUiState
import shop.whitezia.client.model.normalizedConnectionProfiles
import shop.whitezia.client.model.resolve
import shop.whitezia.client.model.runtimeConnectionSettings
import shop.whitezia.client.model.syncSelectedConnectionProfileFields
import shop.whitezia.client.runtime.WhiteZiaRuntimeStateStore
import shop.whitezia.client.runtime.awaitProxyStartup
import shop.whitezia.client.runtime.WhiteZiaTrafficWarmup
import shop.whitezia.client.runtime.formatTrafficSpeed
import shop.whitezia.client.storm.CottenDnsEngineContract
import shop.whitezia.client.storm.CottenDnsStartupState
import shop.whitezia.client.storm.StormDnsProcessManager

internal class ServerTestController(
    private val appContext: Context,
    private val scope: kotlinx.coroutines.CoroutineScope,
    private val readState: () -> WhiteZiaUiState,
    private val writeState: (WhiteZiaUiState) -> Unit,
    private val appendLog: (String) -> Unit,
) {
    private var uiState: WhiteZiaUiState
        get() = readState()
        set(value) = writeState(value)
    private var serverTestJob: Job? = null
    private val serverTestManagersLock = Any()
    private var serverTestManagers: List<StormDnsProcessManager> = emptyList()

    fun cancel() { serverTestJob?.cancel() }

    private fun canConnectToLocalPort(port: Int): Boolean = runCatching {
        Socket().use { it.connect(InetSocketAddress("127.0.0.1", port), 300) }
        true
    }.getOrDefault(false)

    private suspend fun waitForAutoTuneTrialReady(
        manager: StormDnsProcessManager,
        listenPort: Int,
        startupFailure: AtomicReference<String?>,
        startupState: CottenDnsStartupState,
    ): Boolean = try {
        awaitProxyStartup(
            runtimeName = CottenDnsEngineContract.DisplayName,
            timeoutMillis = CottenDnsEngineContract.SocksStartupTimeoutMillis,
            startupFailure = { startupState.failureOrNull() ?: startupFailure.get() },
            isRunning = manager::isRunning,
            exitCode = manager::exitCodeOrNull,
            isPortReady = { startupState.isProtocolReady() && canConnectToLocalPort(listenPort) },
            pollMillis = AutoTuneSignalPollMillis,
            monotonicMillis = SystemClock::elapsedRealtime,
        )
        true
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: IllegalStateException) {
        false
    }

    private fun allocateRandomLocalPorts(
        count: Int,
        additionalBlockedPorts: Set<Int> = emptySet(),
    ): List<Int> {
        val ports = linkedSetOf<Int>()
        val blockedPorts = setOf(
            WhiteZiaRuntimeProxy.ListenPortInt,
            WhiteZiaRuntimeProxy.HttpProxyPortInt,
            WhiteZiaRuntimeProxy.LocalDnsPortInt,
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

        serverTestJob = scope.launch {
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
                        connectionMode = WhiteZiaRuntimeStateStore.ModeProxy,
                        listenIp = WhiteZiaRuntimeProxy.ListenIp,
                        listenPort = listenPort.toString(),
                        httpProxyEnabled = false,
                        httpProxyPort = WhiteZiaRuntimeProxy.HttpProxyPort,
                        socks5Authentication = false,
                        socksUsername = "",
                        socksPassword = "",
                        localDnsEnabled = false,
                        localDnsPort = WhiteZiaRuntimeProxy.LocalDnsPort,
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
                        stopManagers()
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
        val startupState = CottenDnsStartupState(plan.settings.cottenDnsMode)
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
                startupState.observe(line)
                detectStormDnsStartupFailure(line)?.let { failure ->
                    startupFailure.compareAndSet(null, failure)
                }
            }
            val ready = waitForAutoTuneTrialReady(
                manager = manager,
                listenPort = plan.settings.resolve().listenPort,
                startupFailure = startupFailure,
                startupState = startupState,
            )
            if (!ready) {
                val failedResult = plan.result.copy(
                    status = ServerTestStatus.Failed,
                    message = startupState.failureOrNull() ?: startupFailure.get() ?: "Server did not become ready",
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
        val probeResult = WhiteZiaTrafficWarmup.measureDownloadThroughput(plan.settings.resolve())
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

    private fun connectedServerTestResolvers(settings: WhiteZiaSettings): List<String> {
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
        settings: WhiteZiaSettings,
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

    fun stopManagers() {
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

    private data class ServerTestPlan(
        val serverProfile: StormDnsServerProfile,
        val settings: WhiteZiaSettings,
        val result: ServerTestResult,
    )

    private data class ServerTestStartup(
        val plan: ServerTestPlan,
        val manager: StormDnsProcessManager,
        val ready: Boolean,
    )

    private companion object {
        const val AutoTuneSignalPollMillis = 100L
        const val AutoTuneMeasurementSettleMillis = 1_500L
    }
}
