package shop.whitezia.client.vpn

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import shop.whitezia.client.model.WhiteZiaOptions
import shop.whitezia.client.model.WhiteZiaSettings
import shop.whitezia.client.model.AmneziaWgCandidate
import shop.whitezia.client.model.XrayCandidate

class VpnConnectionOrchestratorTest {
    private fun settings() = WhiteZiaSettings(
        amneziaWgConfig = "awg-primary", xrayUri = "vless-primary",
        customServerDomain = "dns.example", customServerEncryptionKey = "key",
    )

    @Test fun automaticOrderSkipsLegacyAwg() {
        val candidates = VpnCandidatePlanner.plan(settings().copy(transportMode = WhiteZiaOptions.TransportXray))
        assertEquals(listOf("xray", "dns"), candidates.map { it.settings.transportMode })
    }

    @Test fun manualXrayNeverStartsAwgOrDns() {
        val candidates = VpnCandidatePlanner.plan(settings().copy(manualMode = true, transportMode = "xray"))
        assertEquals(listOf("xray"), candidates.map { it.settings.transportMode })
    }

    @Test fun manualDnsNeverStartsOtherTransports() {
        assertEquals(listOf("dns"), VpnCandidatePlanner.plan(settings().copy(manualMode = true, forceDnsTunnel = true)).map { it.settings.transportMode })
    }

    @Test fun disabledDnsSwitchWithStaleDnsTransportStillTriesXrayFirst() {
        val input = settings().copy(manualMode = true, transportMode = "dns", forceDnsTunnel = false)
        assertEquals(listOf("xray", "dns"), VpnCandidatePlanner.plan(input).map { it.settings.transportMode })
    }

    @Test fun automaticModeIgnoresStaleForcedDnsSelection() {
        val input = settings().copy(manualMode = false, transportMode = "dns", forceDnsTunnel = true)
        assertEquals(listOf("xray", "dns"), VpnCandidatePlanner.plan(input).map { it.settings.transportMode })
    }

    @Test fun legacyAwgCandidatesAreNeverStarted() {
        val input = settings().copy(amneziaWgCandidates = listOf(AmneziaWgCandidate("n1", "primary", "awg-primary"), AmneziaWgCandidate("n2", "standby", "awg-secondary")))
        assertEquals(listOf("xray", "dns"), VpnCandidatePlanner.plan(input).map { it.settings.transportMode })
    }

    @Test fun manualXrayCanTryStandbyXrayNode() {
        val input = settings().copy(manualMode = true, transportMode = "xray", xrayCandidates = listOf(XrayCandidate("n1", "primary", "vless-primary", 0), XrayCandidate("n2", "standby", "vless-secondary", 0)))
        assertEquals(2, VpnCandidatePlanner.plan(input).size)
    }

    @Test fun fallbackConfirmsStopAndWaitsBeforeStartingNextTunnel() = runBlocking {
        val calls = mutableListOf<String>()
        val runtime = object : VpnTransportRuntime {
            override suspend fun stop() { calls += "stop" }
            override suspend fun awaitStopped() { calls += "stopped" }
            override suspend fun start(candidate: VpnConnectionCandidate) {
                calls += candidate.settings.transportMode
                if (candidate.settings.transportMode == "xray") error("route health check failed")
            }
            override suspend fun verify(candidate: VpnConnectionCandidate) = true
        }
        val winner = VpnConnectionOrchestrator(runtime, pause = { calls += "delay:$it" }).connect(VpnCandidatePlanner.plan(settings()), false)
        assertEquals("dns", winner.settings.transportMode)
        assertEquals(listOf("stop", "stopped", "xray", "stop", "stopped", "delay:3000", "dns"), calls)
    }

    @Test fun unsafeStopNeverStartsAnyTunnel() = runBlocking {
        var starts = 0
        val runtime = object : VpnTransportRuntime {
            override suspend fun stop() {}
            override suspend fun awaitStopped() { error("native runner still alive") }
            override suspend fun start(candidate: VpnConnectionCandidate) { starts++ }
            override suspend fun verify(candidate: VpnConnectionCandidate) = true
        }
        assertTrue(runCatching { VpnConnectionOrchestrator(runtime).connect(VpnCandidatePlanner.plan(settings()), false) }.isFailure)
        assertEquals(0, starts)
    }

    @Test fun cancellationNeverFallsThroughToAnotherTransport() = runBlocking {
        var starts = 0
        val runtime = object : VpnTransportRuntime {
            override suspend fun stop() {}
            override suspend fun awaitStopped() {}
            override suspend fun start(candidate: VpnConnectionCandidate) { starts++; throw CancellationException("user stop") }
            override suspend fun verify(candidate: VpnConnectionCandidate) = true
        }
        val failure = runCatching { VpnConnectionOrchestrator(runtime).connect(VpnCandidatePlanner.plan(settings()), false) }.exceptionOrNull()
        assertTrue(failure is CancellationException)
        assertEquals(1, starts)
    }

    @Test fun livenessUsesConsecutiveFailuresAndResetsAfterSuccess() {
        val policy = TunnelLivenessPolicy()
        assertFalse(policy.observe(false))
        assertFalse(policy.observe(false))
        assertFalse(policy.observe(true))
        assertFalse(policy.observe(false))
        assertFalse(policy.observe(false))
        assertTrue(policy.observe(false))
    }
}
