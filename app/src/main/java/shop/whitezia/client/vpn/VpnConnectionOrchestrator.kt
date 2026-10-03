package shop.whitezia.client.vpn

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import shop.whitezia.client.model.WhiteZiaOptions
import shop.whitezia.client.model.WhiteZiaSettings
import shop.whitezia.client.model.normalizedConnectionProfiles
import shop.whitezia.client.model.selectedConnectionProfile
import shop.whitezia.client.model.selectedTransportMode
import shop.whitezia.client.model.syncSelectedConnectionProfileFields

internal enum class VpnConnectionPhase { Idle, Preparing, Connecting, Checking, Connected, WaitingForNetwork, Stopping, Failed }

internal data class VpnConnectionState(
    val phase: VpnConnectionPhase = VpnConnectionPhase.Idle,
    val transport: String = "",
    val nodeId: String = "",
    val attempt: Int = 0,
    val totalAttempts: Int = 0,
)

internal enum class XrayRouteKind { Direct, Cdn }

internal data class VpnConnectionCandidate(
    val settings: WhiteZiaSettings,
    val nodeId: String,
    val routeKind: XrayRouteKind = XrayRouteKind.Cdn,
)

internal object VpnCandidatePlanner {
    fun plan(settings: WhiteZiaSettings): List<VpnConnectionCandidate> {
        val result = mutableListOf<VpnConnectionCandidate>()
        val selectedTransport = settings.selectedTransportMode()
        val dnsOnly = selectedTransport == WhiteZiaOptions.TransportDns
        val xrayOnly = selectedTransport == WhiteZiaOptions.TransportXray
        if (!dnsOnly) {
            val seen = mutableSetOf<String>()
            fun addRoute(uri: String, nodeId: String, limit: Long, kind: XrayRouteKind) {
                if (uri.isNotBlank() && seen.add(uri)) result += VpnConnectionCandidate(
                    settings.copy(transportMode = WhiteZiaOptions.TransportXray, xrayUri = uri,
                        xrayDailyLimitBytes = limit, activeXrayNodeId = nodeId), nodeId, kind,
                )
            }
            settings.xrayCandidates.forEach { node ->
                addRoute(node.directUri, node.nodeId, 0, XrayRouteKind.Direct)
            }
            settings.xrayCandidates.forEach { node ->
                addRoute(node.uri, node.nodeId, node.dailyLimitBytes, XrayRouteKind.Cdn)
            }
            addRoute(settings.xrayUri, settings.activeXrayNodeId, settings.xrayDailyLimitBytes, XrayRouteKind.Cdn)
        }
        if (!xrayOnly) {
            val dns = settings.copy(transportMode = WhiteZiaOptions.TransportDns)
            val seen = mutableSetOf<Pair<String, String>>()
            if (dns.customServerDomain.isNotBlank() && dns.customServerEncryptionKey.isNotBlank()) {
                seen += dns.customServerDomain to dns.customServerEncryptionKey
                result += VpnConnectionCandidate(dns, dns.activeStormDnsNodeId)
            }
            settings.stormDnsCandidates.forEach { node ->
                if (node.domain.isNotBlank() && node.encryptionKey.isNotBlank() && seen.add(node.domain to node.encryptionKey)) {
                    val profileId = settings.selectedConnectionProfile().id
                    val selected = settings.copy(
                        transportMode = WhiteZiaOptions.TransportDns,
                        activeStormDnsNodeId = node.nodeId,
                        connectionProfiles = settings.normalizedConnectionProfiles().map { profile ->
                            if (profile.id == profileId) profile.copy(customServerDomain = node.domain,
                                customServerEncryptionKey = node.encryptionKey, customServerEncryptionMethod = node.encryptionMethod) else profile
                        },
                    ).syncSelectedConnectionProfileFields()
                    result += VpnConnectionCandidate(selected, node.nodeId)
                }
            }
        }
        return result
    }
}

/** Held across native startup/cleanup, including consecutive service instances. */
internal object VpnRuntimeOwnership {
    val mutex = Mutex()
}

internal interface VpnTransportRuntime {
    suspend fun stop()
    suspend fun awaitStopped()
    suspend fun prepare(candidates: List<VpnConnectionCandidate>): List<VpnConnectionCandidate> = candidates
    suspend fun start(candidate: VpnConnectionCandidate)
    suspend fun verify(candidate: VpnConnectionCandidate): Boolean
}

internal class VpnConnectionOrchestrator(
    private val runtime: VpnTransportRuntime,
    private val settleDelayMillis: Long = 3_000L,
    private val pause: suspend (Long) -> Unit = { delay(it) },
    private val onState: (VpnConnectionState) -> Unit = {},
    private val onFailure: (VpnConnectionCandidate, Exception) -> Unit = { _, _ -> },
) {
    private val mutableState = MutableStateFlow(VpnConnectionState())
    val state = mutableState.asStateFlow()

    suspend fun connect(candidates: List<VpnConnectionCandidate>, settleFirst: Boolean): VpnConnectionCandidate =
        VpnRuntimeOwnership.mutex.withLock {
            require(candidates.isNotEmpty()) { "В подписке нет доступных конфигураций VPN" }
            publish(candidates.first(), VpnConnectionPhase.Stopping, 0, candidates.size)
            withContext(NonCancellable) { runtime.stop(); runtime.awaitStopped() }
            currentCoroutineContext().ensureActive()
            if (settleFirst) pause(settleDelayMillis)
            publish(candidates.first(), VpnConnectionPhase.Preparing, 0, candidates.size)
            val prepared = runtime.prepare(candidates)
            require(prepared.isNotEmpty()) { "Нет доступных VPN маршрутов в текущей сети" }
            var lastError: Exception? = null
            for ((index, candidate) in prepared.withIndex()) {
                currentCoroutineContext().ensureActive()
                // A failed stop is terminal: never raise another native tunnel over it.
                if (index > 0) {
                    publish(candidate, VpnConnectionPhase.Stopping, index, prepared.size)
                    withContext(NonCancellable) { runtime.stop(); runtime.awaitStopped() }
                    pause(settleDelayMillis)
                }
                currentCoroutineContext().ensureActive()
                try {
                    publish(candidate, VpnConnectionPhase.Connecting, index, prepared.size)
                    runtime.start(candidate)
                    publish(candidate, VpnConnectionPhase.Checking, index, prepared.size)
                    check(runtime.verify(candidate)) { "Проверка подключения не пройдена" }
                    currentCoroutineContext().ensureActive()
                    publish(candidate, VpnConnectionPhase.Connected, index, prepared.size)
                    return@withLock candidate
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    lastError = error
                    onFailure(candidate, error)
                }
            }
            withContext(NonCancellable) { runtime.stop(); runtime.awaitStopped() }
            mutableState.value = mutableState.value.copy(phase = VpnConnectionPhase.Failed)
            onState(mutableState.value)
            throw IllegalStateException("Все доступные VPN маршруты недоступны", lastError)
        }

    private fun publish(candidate: VpnConnectionCandidate, phase: VpnConnectionPhase, index: Int, total: Int) {
        mutableState.value = VpnConnectionState(phase, candidate.settings.transportMode, candidate.nodeId, index + 1, total)
        onState(mutableState.value)
    }
}

internal class TunnelLivenessPolicy(private val failureThreshold: Int = 3) {
    private var failures = 0
    init { require(failureThreshold > 0) }
    fun observe(healthy: Boolean): Boolean {
        failures = if (healthy) 0 else failures + 1
        return failures >= failureThreshold
    }
}
