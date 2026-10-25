package shop.whitezia.client.storm

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import shop.whitezia.client.model.CottenDnsMode
import shop.whitezia.client.runtime.awaitProxyStartup
import shop.whitezia.client.runtime.parseStormDnsConnectionProgressLine
import shop.whitezia.client.runtime.parseStormDnsTrafficStatsLine
import shop.whitezia.client.runtime.parseStormDnsResolverStateLine
import shop.whitezia.client.scan.StormDnsScanTelemetry
import shop.whitezia.client.scan.parseStormDnsScanLine

class CottenDnsStartupStateTest {
    @Test fun normalKeepsItsExistingPortReadiness() {
        val state = CottenDnsStartupState(CottenDnsMode.Normal)
        assertTrue(state.isProtocolReady())
        state.observe("CottenMax authentication failed: fixture")
        assertNull(state.failureOrNull())
    }

    @Test fun earlyMaxListenerCannotPassUntilAuthenticatedTransferProgress() = runBlocking {
        val state = CottenDnsStartupState(CottenDnsMode.Max)
        var now = 0L
        var portChecks = 0
        awaitProxyStartup("CottenDNS", CottenDnsEngineContract.SocksStartupTimeoutMillis,
            state::failureOrNull, { true }, { null },
            { portChecks++; state.isProtocolReady() },
            monotonicMillis = { now }, pause = {
                now += it
                if (now == 20_000L) state.observe(Connected)
            })
        assertEquals(20_000L, now)
        assertTrue(portChecks > 1)
        assertTrue(state.isProtocolReady())
    }

    @Test fun otherModesOrIncompleteProgressCannotAuthenticateMax() = runBlocking {
        val state = CottenDnsStartupState(CottenDnsMode.Max)
        for (line in listOf("WD_PROGRESS phase=connected percent=100", "WD_PROGRESS phase=connected percent=100 mode=legacy",
            "WD_PROGRESS phase=runtime percent=100 mode=cottenmax", "WD_PROGRESS phase=connected percent=99 mode=cottenmax")) {
            state.observe(line)
            assertFalse(state.isProtocolReady())
        }
        var now = 0L
        val failure = runCatching {
            awaitProxyStartup("CottenDNS", CottenDnsEngineContract.SocksStartupTimeoutMillis,
                state::failureOrNull, { true }, { null }, state::isProtocolReady,
                monotonicMillis = { now }, pause = { now += it })
        }.exceptionOrNull()
        assertTrue(failure is IllegalStateException && failure.message.orEmpty().contains("timed out"))
        assertEquals(75_000L, now)
    }

    @Test fun authenticationFailureIsTerminalForThisMaxAttemptAndSanitized() = runBlocking {
        val state = CottenDnsStartupState(CottenDnsMode.Max)
        var now = 0L
        val failure = runCatching {
            awaitProxyStartup("CottenDNS", 75_000L, state::failureOrNull, { true }, { null }, state::isProtocolReady,
                monotonicMillis = { now }, pause = {
                    now += it
                    if (now == 2_000L) state.observe("CottenMax authentication failed: private.fixture.invalid (fixture-key)")
                })
        }.exceptionOrNull()
        assertTrue(failure is IllegalStateException && failure.message.orEmpty().contains("authentication failed"))
        assertFalse(failure?.message.orEmpty().contains("fixture-key"))
        assertEquals(2_000L, now)
        state.observe(Connected)
        assertFalse(state.isProtocolReady())
    }

    @Test fun transferFailureDoesNotFallBackToNormalReadiness() {
        val state = CottenDnsStartupState(CottenDnsMode.Max)
        state.observe("CottenMax authenticated, but tunnel transfer verification failed; check the matching server build and resolver path (timeout)")
        assertFalse(state.isProtocolReady())
        assertEquals("CottenMax transfer verification failed", state.failureOrNull())
    }

    @Test fun successfulAuthenticatedStreamIsNotUndoneByAnOlderWarmupWarning() {
        val state = CottenDnsStartupState(CottenDnsMode.Max)
        state.observe(Connected)
        state.observe("CottenMax authentication failed: superseded warmup timeout")
        assertTrue(state.isProtocolReady())
        assertNull(state.failureOrNull())
    }

    @Test fun stopAndNativeExitInterruptAnAlreadyListeningButUnauthenticatedMax() = runBlocking {
        for (stop in listOf(true, false)) {
            val state = CottenDnsStartupState(CottenDnsMode.Max)
            var now = 0L
            val failure = runCatching {
                awaitProxyStartup("CottenDNS", 75_000L, state::failureOrNull,
                    { stop || now < 1_000L }, { 9 }, state::isProtocolReady,
                    keepWaiting = { !stop || now < 1_000L },
                    monotonicMillis = { now }, pause = { now += it })
            }.exceptionOrNull()
            assertEquals(1_000L, now)
            if (stop) assertTrue(failure is CancellationException)
            else assertTrue(failure is IllegalStateException && failure.message.orEmpty().contains("exit code 9"))
        }
    }

    @Test fun coroutineCancellationDoesNotWaitForMaxAuthentication() {
        var now = 0L
        val state = CottenDnsStartupState(CottenDnsMode.Max)
        val failure = runCatching {
            runBlocking {
                awaitProxyStartup("CottenDNS", 75_000L, state::failureOrNull, { true }, { null }, state::isProtocolReady,
                    monotonicMillis = { now }, pause = {
                        now += it
                        if (now >= 1_000L) currentCoroutineContext().cancel()
                        yield()
                    })
            }
        }.exceptionOrNull()
        assertTrue(failure is CancellationException)
        assertEquals(1_000L, now)
    }

    @Test fun maxProgressAndSharedTrafficReporterRemainReadable() {
        val progress = requireNotNull(parseStormDnsConnectionProgressLine(Connected))
        assertEquals("connected", progress.phase)
        assertEquals(100, progress.percent)
        val traffic = requireNotNull(parseStormDnsTrafficStatsLine(
            "📊 ↑ 12 B/s (Total: 123 B) | ↓ 34 B/s (Total: 456 B) | loss 0.0% | resolvers 2 | transport AUTO | queues 0/0/0 | drops rx=0 tx=0 | recoveries 0 | stream-fail dial=0 write=0"))
        assertEquals(123L, traffic.uploadBytes)
        assertEquals(456L, traffic.downloadBytes)
    }

    @Test fun maxRequiresRealContentHealthEvenForExplicitCustomResolvers() {
        assertTrue(CottenDnsMode.requiresStrongDnsHealth(CottenDnsMode.Max, true))
        assertTrue(CottenDnsMode.requiresStrongDnsHealth(CottenDnsMode.Max, false))
        assertTrue(CottenDnsMode.requiresStrongDnsHealth(CottenDnsMode.Normal, false))
        assertFalse(CottenDnsMode.requiresStrongDnsHealth(CottenDnsMode.Normal, true))
    }

    @Test fun maxAuthenticatedScanAndVerifiedResolverTelemetryUseExistingAuthoritativeEvents() {
        assertNull(parseStormDnsScanLine("WD_SCAN event=path resolver=8.8.8.8:53 transport=tcp authenticated=true transfer_verified=false qname_bytes=140 answer_bytes=500"))
        assertEquals(StormDnsScanTelemetry.Valid("8.8.8.8:53"), parseStormDnsScanLine("WD_SCAN event=valid resolver=8.8.8.8:53"))
        assertEquals(StormDnsScanTelemetry.Complete(2, 1, 1), parseStormDnsScanLine("WD_SCAN event=complete total=2 valid=1 rejected=1"))
        val state = requireNotNull(parseStormDnsResolverStateLine("WD_RESOLVERS active=8.8.8.8:53 standby=- valid=8.8.8.8:53"))
        assertEquals(listOf("8.8.8.8:53"), state.activeResolvers)
        assertTrue(state.standbyResolvers.isEmpty())
        assertEquals(state.activeResolvers, state.validResolvers)
    }

    private companion object { const val Connected = "WD_PROGRESS phase=connected percent=100 mode=cottenmax" }
}
