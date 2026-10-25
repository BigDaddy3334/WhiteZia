package shop.whitezia.client.vpn

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import shop.whitezia.client.model.WhiteZiaOptions
import shop.whitezia.client.model.WhiteZiaSettings
import shop.whitezia.client.model.AmneziaWgCandidate
import shop.whitezia.client.model.XrayCandidate
import shop.whitezia.client.model.ConnectionProfile
import shop.whitezia.client.resolver.ResolverCacheStore
import shop.whitezia.client.resolver.memoryResolverPreferences
import shop.whitezia.client.runtime.HealthProbeSummary

class VpnConnectionOrchestratorTest {
    private fun settings() = WhiteZiaSettings(
        amneziaWgConfig = "awg-primary", xrayUri = "vless-primary",
        customServerDomain = "dns.example", customServerEncryptionKey = "key",
    ).let { it.copy(connectionProfiles = listOf(ConnectionProfile.fromSettings(it))) }

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

    @Test fun dnsStartupAndActualHealthFailureTryIsolatedPublicPoolAfterAwaitedShutdown() = runBlocking {
        for (startupFailure in listOf(true, false)) {
            val calls = mutableListOf<String>()
            val physical = "10.20.0.53\n10.20.0.54"
            val coordinator = resolverCoordinator { physical.lines() }
            val input = VpnConnectionCandidate(settings().copy(transportMode = "dns", resolverText = ""), "dns-node")
            var active = ""
            val runtime = object : VpnTransportRuntime {
                override suspend fun stop() { calls += "stop" }
                override suspend fun awaitStopped() { calls += "stopped" }
                override suspend fun expand(candidate: VpnConnectionCandidate): List<VpnConnectionCandidate> {
                    calls += "snapshot"
                    return coordinator.preparePools(candidate.settings).map { candidate.copy(settings = it) }
                }
                override suspend fun start(candidate: VpnConnectionCandidate) {
                    active = if (candidate.settings.resolverText == physical) "physical" else "public"
                    calls += "start:$active"
                    assertFalse(candidate.settings.customResolversEnabled)
                    assertEquals(input.settings.customServerEncryptionKey, candidate.settings.customServerEncryptionKey)
                    if (active == "physical" && startupFailure) error("No usable resolver before bounded startup deadline")
                }
                override suspend fun verify(candidate: VpnConnectionCandidate): Boolean {
                    calls += "verify:$active"
                    return if (active == "physical") HealthProbeSummary(1, 0).isHealthy(false, requireDnsContent = true)
                    else HealthProbeSummary(2, 1).isHealthy(false, requireDnsContent = true)
                }
            }
            val winner = VpnConnectionOrchestrator(runtime, pause = { calls += "settle" }).connect(listOf(input), false)
            assertEquals("dns-node", winner.nodeId)
            assertTrue(winner.settings.resolverText.lines().all { !it.startsWith("10.") })
            assertEquals(listOf("stop", "stopped", "snapshot", "start:physical") +
                (if (startupFailure) emptyList() else listOf("verify:physical")) +
                listOf("stop", "stopped", "settle", "start:public", "verify:public"), calls)
        }
    }

    @Test fun successfulPhysicalPoolNeverStartsPublicFallback() = runBlocking {
        val physical = listOf("10.20.0.53", "10.20.0.54")
        val coordinator = resolverCoordinator { physical }
        val input = VpnConnectionCandidate(settings().copy(transportMode = "dns", resolverText = ""), "dns-node")
        val started = mutableListOf<String>()
        val runtime = object : VpnTransportRuntime {
            override suspend fun stop() {}
            override suspend fun awaitStopped() {}
            override suspend fun expand(candidate: VpnConnectionCandidate) = coordinator.preparePools(candidate.settings).map { candidate.copy(settings = it) }
            override suspend fun start(candidate: VpnConnectionCandidate) { started += candidate.settings.resolverText }
            override suspend fun verify(candidate: VpnConnectionCandidate) = true
        }
        val winner = VpnConnectionOrchestrator(runtime, pause = {}).connect(listOf(input), false)
        assertEquals(physical.joinToString("\n"), winner.settings.resolverText)
        assertEquals(listOf(winner.settings.resolverText), started)
    }

    @Test fun dnsSnapshotIsLazyAfterPreviousXrayShutdownAndKeepsBothPoolsFromThatSnapshot() = runBlocking {
        var physical = listOf("10.99.0.53")
        var snapshotCount = 0
        val coordinator = resolverCoordinator { snapshotCount++; physical }
        val calls = mutableListOf<String>()
        var stops = 0
        val candidates = VpnCandidatePlanner.plan(settings().copy(resolverText = ""))
        val runtime = object : VpnTransportRuntime {
            override suspend fun stop() { stops++; calls += "stop" }
            override suspend fun awaitStopped() {
                calls += "stopped"
                if (stops == 2) physical = listOf("10.20.0.53")
            }
            override suspend fun expand(candidate: VpnConnectionCandidate): List<VpnConnectionCandidate> {
                if (candidate.settings.transportMode != "dns") return listOf(candidate)
                calls += "snapshot"
                return coordinator.preparePools(candidate.settings).map { candidate.copy(settings = it) }
            }
            override suspend fun start(candidate: VpnConnectionCandidate) {
                calls += "start:${candidate.settings.transportMode}"
                if (candidate.settings.transportMode == "xray") error("Xray unavailable")
                assertEquals("10.20.0.53", candidate.settings.resolverText)
            }
            override suspend fun verify(candidate: VpnConnectionCandidate) = true
        }
        VpnConnectionOrchestrator(runtime, pause = {}).connect(candidates, false)
        assertEquals(1, snapshotCount)
        assertEquals(listOf("stop", "stopped", "start:xray", "stop", "stopped", "snapshot", "start:dns"), calls)
    }

    @Test fun cancelledDnsHealthNeverFallsThroughToPublicOrAnotherNode() = runBlocking {
        val coordinator = resolverCoordinator { listOf("10.20.0.53") }
        var starts = 0
        var snapshots = 0
        val first = VpnConnectionCandidate(settings().copy(transportMode = "dns", resolverText = ""), "first")
        val runtime = object : VpnTransportRuntime {
            override suspend fun stop() {}
            override suspend fun awaitStopped() {}
            override suspend fun expand(candidate: VpnConnectionCandidate): List<VpnConnectionCandidate> {
                snapshots++
                return coordinator.preparePools(candidate.settings).map { candidate.copy(settings = it) }
            }
            override suspend fun start(candidate: VpnConnectionCandidate) { starts++ }
            override suspend fun verify(candidate: VpnConnectionCandidate): Boolean = throw CancellationException("User stopped or replaced the session")
        }
        val failure = runCatching { VpnConnectionOrchestrator(runtime, pause = {}).connect(listOf(first, first.copy(nodeId = "second")), false) }.exceptionOrNull()
        assertTrue(failure is CancellationException)
        assertEquals(1, starts)
        assertEquals(1, snapshots)
    }

    @Test fun failedPhysicalShutdownPreventsPublicFallback() = runBlocking {
        val coordinator = resolverCoordinator { listOf("10.20.0.53") }
        var starts = 0
        var stopCount = 0
        val runtime = object : VpnTransportRuntime {
            override suspend fun stop() { stopCount++ }
            override suspend fun awaitStopped() { if (stopCount == 2) error("Native process is still alive") }
            override suspend fun expand(candidate: VpnConnectionCandidate) = coordinator.preparePools(candidate.settings).map { candidate.copy(settings = it) }
            override suspend fun start(candidate: VpnConnectionCandidate) { starts++ }
            override suspend fun verify(candidate: VpnConnectionCandidate) = false
        }
        val input = VpnConnectionCandidate(settings().copy(transportMode = "dns", resolverText = ""), "dns-node")
        val failure = runCatching { VpnConnectionOrchestrator(runtime, pause = {}).connect(listOf(input), false) }.exceptionOrNull()
        assertEquals("Native process is still alive", failure?.message)
        assertEquals(1, starts)
    }

    private fun resolverCoordinator(physical: () -> List<String>) = ServiceResolverCoordinator(
        ResolverCacheStore(memoryResolverPreferences(), incrementLaunchCount = false),
        measureScore = { _, _, _ -> error("Orchestrator must not benchmark resolver pools") },
        physicalDnsResolvers = physical,
    )

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
