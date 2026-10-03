package shop.whitezia.client.vpn

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import shop.whitezia.client.model.WhiteZiaOptions

internal data class XrayRouteProbeResult(val latencyMillis: Long?, val reason: String = "")

internal class XrayRoutePreflight(
    private val probe: suspend (VpnConnectionCandidate) -> XrayRouteProbeResult,
    private val report: (VpnConnectionCandidate, XrayRouteProbeResult) -> Unit = { _, _ -> },
    private val timeoutMillis: Long = 3_500,
    private val parallelism: Int = 4,
) {
    suspend fun prepare(candidates: List<VpnConnectionCandidate>): List<VpnConnectionCandidate> = coroutineScope {
        val gate = Semaphore(parallelism)
        val checked = candidates.filter { it.settings.transportMode == WhiteZiaOptions.TransportXray }
            .map { candidate ->
                async {
                    val result = gate.withPermit {
                        try {
                            withTimeoutOrNull(timeoutMillis) { probe(candidate) }
                                ?: XrayRouteProbeResult(null, "timeout")
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Exception) {
                            XrayRouteProbeResult(null, error.javaClass.simpleName)
                        }
                    }
                    report(candidate, result)
                    candidate to result
                }
            }.awaitAll()
        // Reachability is not authentication. Every surviving route still needs tunnel validation.
        checked.filter { it.second.latencyMillis != null }
            .sortedWith(compareBy({ it.first.routeKind.ordinal }, { it.second.latencyMillis }))
            .map { it.first } + candidates.filter { it.settings.transportMode != WhiteZiaOptions.TransportXray }
    }
}
