package shop.whitezia.client.vpn

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import shop.whitezia.client.model.WhiteZiaSettings
import shop.whitezia.client.model.XrayCandidate

class XrayRoutePreflightTest {
    private fun routes() = VpnCandidatePlanner.plan(WhiteZiaSettings(
        manualMode = true, transportMode = "xray", xrayUri = "cdn-1",
        xrayCandidates = listOf(
            XrayCandidate("n1", "primary", "cdn-1", 5, "direct-1"),
            XrayCandidate("n2", "standby", "cdn-2", 5, "direct-2"),
        ),
    ))

    @Test fun plannerIncludesAllDirectRoutesBeforeCdnAndDirectHasNoQuota() {
        assertEquals(listOf("direct-1", "direct-2", "cdn-1", "cdn-2"), routes().map { it.settings.xrayUri })
        assertEquals(listOf(0L, 0L, 5L, 5L), routes().map { it.settings.xrayDailyLimitBytes })
    }

    @Test fun fasterCdnNeverOutranksReachableDirect() = runBlocking {
        val seen = mutableSetOf<String>()
        val prepared = XrayRoutePreflight({ candidate ->
            val uri = candidate.settings.xrayUri
            seen += uri
            XrayRouteProbeResult(if (uri.startsWith("cdn")) 1 else if (uri == "direct-1") 90 else 70)
        }).prepare(routes())
        assertEquals(4, seen.size)
        assertEquals(listOf("direct-2", "direct-1", "cdn-1", "cdn-2"), prepared.map { it.settings.xrayUri })
    }

    @Test fun unreachableDirectIsSkippedButOtherDirectStillPrecedesCdn() = runBlocking {
        val prepared = XrayRoutePreflight({ candidate ->
            XrayRouteProbeResult(if (candidate.settings.xrayUri == "direct-1") null else 4)
        }).prepare(routes())
        assertEquals(listOf("direct-2", "cdn-1", "cdn-2"), prepared.map { it.settings.xrayUri })
    }

    @Test fun probeTimeoutIsBoundedAndDnsFallbackSurvives() = runBlocking {
        val dns = VpnConnectionCandidate(WhiteZiaSettings(transportMode = "dns"), "dns")
        val prepared = XrayRoutePreflight({ delay(500); XrayRouteProbeResult(1) }, timeoutMillis = 10)
            .prepare(routes() + dns)
        assertEquals(listOf(dns), prepared)
    }

    @Test fun cancellationDoesNotConnectOrStartFallback() = runBlocking {
        val failure = runCatching {
            XrayRoutePreflight({ throw CancellationException("stop") }).prepare(routes())
        }.exceptionOrNull()
        assertTrue(failure is CancellationException)
    }

    @Test fun probesAreConcurrentButBounded() = runBlocking {
        var running = 0
        var maxRunning = 0
        XrayRoutePreflight({
            running++
            maxRunning = maxOf(maxRunning, running)
            try { delay(20); XrayRouteProbeResult(1) } finally { running-- }
        }, parallelism = 2).prepare(routes())
        assertEquals(2, maxRunning)
        assertEquals(0, running)
    }
}
