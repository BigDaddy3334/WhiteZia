package shop.whitezia.client.vpn

import android.content.SharedPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import shop.whitezia.client.model.ConnectionProfile
import shop.whitezia.client.model.ResolverProfile
import shop.whitezia.client.model.WhiteZiaOptions
import shop.whitezia.client.model.WhiteZiaSettings
import shop.whitezia.client.resolver.ResolverBenchmarkScore
import shop.whitezia.client.resolver.ResolverCacheStore
import shop.whitezia.client.resolver.memoryResolverPreferences

class ServiceResolverCoordinatorTest {
    @Test fun cellularCatalogUsesDetectedCarrierAfterPhysicalAndBeforeGenericFallback() {
        val fixture = Fixture(physicalDns = listOf("10.20.0.53"), cellularOperator = WhiteZiaOptions.OperatorMts,
            operatorCatalog = operatorCatalog())
        val pools = fixture.coordinator.preparePools(fixture.settings.copy(resolverText = "77.88.8.8"))
        assertEquals(listOf("10.20.0.53", CatalogAddress, Yandex.joinToString("\n")), pools.map { it.resolverText })
    }

    @Test fun wifiAndUnknownCarrierCannotUseTheSelectedSimOperatorCatalog() {
        val fixture = Fixture(physicalDns = listOf("192.168.1.1"), operatorCatalog = operatorCatalog())
        val pools = fixture.coordinator.preparePools(fixture.settings.copy(resolverText = "77.88.8.8"))
        assertEquals(2, pools.size)
        assertTrue(pools.none { CatalogAddress in it.resolverText })
    }

    @Test fun catalogUnsupportedModeOrProtocolDoesNotEnterStartupPools() {
        val fixture = Fixture(physicalDns = listOf("10.20.0.53"), cellularOperator = WhiteZiaOptions.OperatorMts,
            operatorCatalog = operatorCatalog())
        for (settings in listOf(fixture.settings.copy(cottenDnsMode = "cottenmax", resolverText = "77.88.8.8"),
                fixture.settings.copy(cottenDnsResolverTransport = "tcp", resolverText = "77.88.8.8"))) {
            assertEquals(2, fixture.coordinator.preparePools(settings).size)
        }
    }

    @Test fun successfulOperatorPoolIsNotMixedWithGenericDnsOrInterruptedByBenchmark() = runBlocking {
        val fixture = Fixture(cellularOperator = WhiteZiaOptions.OperatorMts, operatorCatalog = operatorCatalog())
        val settings = fixture.coordinator.preparePools(fixture.settings.copy(resolverText = "77.88.8.8")).first()
        assertEquals(CatalogAddress, settings.resolverText)
        assertEquals(settings, fixture.choose(settings))
        assertTrue(fixture.events.isEmpty())
    }

    @Test fun manualResolversBypassCatalogAndPhysicalDns() {
        val fixture = Fixture(physicalDns = listOf("10.20.0.53"), cellularOperator = WhiteZiaOptions.OperatorMts,
            operatorCatalog = operatorCatalog())
        val manual = fixture.settings.copy(customResolversEnabled = true, customResolverText = "1.1.1.1")
        assertEquals(listOf("1.1.1.1"), fixture.coordinator.preparePools(manual).map { it.resolverText })
    }

    @Test
    fun customEnabledUsesOnlyCustomWithoutBenchmarkOrCacheWrites() = runBlocking {
        val fixture = Fixture(physicalDns = Local)
        val settings = fixture.settings.copy(customResolversEnabled = true, customResolverText = "8.8.8.8")
        val prepared = fixture.coordinator.prepare(settings)
        assertEquals("8.8.8.8", prepared.resolverText)
        assertEquals(prepared, fixture.choose(prepared))
        assertTrue(fixture.events.isEmpty())
        assertNull(fixture.cache.readBenchmarkLastLaunchBucket(settings.operatorCode, LocalRuntime))
        assertTrue(fixture.cache.readCachedResolvers(settings.operatorCode) { true }.isEmpty())
    }

    @Test
    fun disabledCustomIsExcludedFromCurrentAndCacheWithoutDeletingInput() {
        val fixture = Fixture()
        fixture.cache.mergeResolvers(Local + "176.59.127.147", fixture.settings.operatorCode) { true }
        val prepared = fixture.coordinator.prepare(fixture.settings.copy(
            customResolverText = Local.single(),
            selectedResolverProfileId = ResolverProfile.CustomId,
            connectionProfiles = listOf(ConnectionProfile.defaultProfile().copy(resolverProfileId = ResolverProfile.CustomId)),
        ))
        assertEquals("176.59.127.147\n77.88.8.8\n77.88.8.1", prepared.resolverText)
        assertEquals(Local.single(), prepared.customResolverText)
        assertFalse(prepared.customResolversEnabled)
        assertEquals("", prepared.selectedResolverProfileId)
        assertEquals("", prepared.connectionProfiles.first().resolverProfileId)
    }

    @Test
    fun disabledCustomWithNoLocalResolversUsesYandexWithoutRestoringCustom() = runBlocking {
        val fixture = Fixture()
        val prepared = fixture.coordinator.prepare(fixture.settings.copy(customResolverText = Local.single()))
        assertEquals(Yandex.joinToString("\n"), prepared.resolverText)
        assertEquals(prepared, fixture.choose(prepared))
        assertTrue(fixture.events.isEmpty())
    }

    @Test
    fun brandNewInstallUsesPhysicalDnsAndMergesOperatorAndGlobalCache() {
        val fixture = Fixture(physicalDns = Local)
        val prepared = fixture.coordinator.prepare(fixture.settings.copy(resolverText = ""))
        assertEquals(Local.joinToString("\n"), prepared.resolverText)
        assertEquals(Local, fixture.cache.readCachedResolvers(fixture.settings.operatorCode) { true })
        assertEquals(Local, fixture.cache.readCachedResolvers(WhiteZiaOptions.OperatorBeeline) { true })
        assertEquals(1, fixture.cache.launchCount)
        assertNull(fixture.cache.readBenchmarkLastLaunchBucket(fixture.settings.operatorCode, LocalRuntime))
    }

    @Test
    fun physicalDnsFiltersUnusableButKeepsCurrentPublicAndDisabledCustomEntries() {
        val fixture = Fixture(physicalDns = listOf(
            "invalid", "8.8.8.8", "77.88.8.8", "0.0.0.0", "127.0.0.1", "224.0.0.1",
            Local.single(), "176.59.127.147:53", "176.59.127.147",
        ))
        val prepared = fixture.coordinator.prepare(fixture.settings.copy(customResolverText = Local.single()))
        assertEquals("8.8.8.8\n77.88.8.8\n176.59.127.146\n176.59.127.147", prepared.resolverText)
        assertEquals(listOf("8.8.8.8", "77.88.8.8", "176.59.127.146", "176.59.127.147"),
            fixture.cache.readCachedResolvers(fixture.settings.operatorCode) { true })
        assertEquals(Local.single(), prepared.customResolverText)
    }

    @Test
    fun freshPhysicalDnsIsReadOnceEvenWhenStalePrivateCacheAndSettingsExist() {
        val fixture = Fixture()
        fixture.cache.mergeResolvers(listOf("10.99.0.53", "192.168.1.1"), fixture.settings.operatorCode) { true }
        var reads = 0
        val coordinator = ServiceResolverCoordinator(fixture.cache,
            measureScore = { _, _, _ -> error("Must not measure") },
            physicalDnsResolvers = { reads++; listOf("10.20.0.53", "10.20.0.54") },
        )
        val prepared = coordinator.prepare(fixture.settings.copy(resolverText = "10.99.0.53\n100.64.1.1"))
        assertEquals("10.20.0.53\n10.20.0.54", prepared.resolverText)
        assertEquals(1, reads)
    }

    @Test
    fun emptyPhysicalDnsUsesYandexEvenWithoutLocalCandidates() = runBlocking {
        val fixture = Fixture()
        val prepared = fixture.coordinator.prepare(fixture.settings.copy(resolverText = ""))
        assertEquals(Yandex.joinToString("\n"), prepared.resolverText)
        assertEquals(prepared, fixture.choose(prepared))
        assertTrue(fixture.events.isEmpty())
        assertTrue(fixture.cache.readCachedResolvers(fixture.settings.operatorCode) { true }.isEmpty())
    }

    @Test
    fun disabledCustomYandexListDoesNotSuppressFixedFallback() = runBlocking {
        val fixture = Fixture()
        val customText = Yandex.joinToString("\n")
        val prepared = fixture.coordinator.prepare(fixture.settings.copy(
            resolverText = "", customResolversEnabled = false, customResolverText = customText,
        ))
        assertEquals(customText, prepared.resolverText)
        assertEquals(customText, prepared.customResolverText)
        assertFalse(prepared.customResolversEnabled)
        assertEquals(prepared, fixture.choose(prepared))
        assertTrue(fixture.events.isEmpty())
    }

    @Test
    fun disabledCustomYandexListDoesNotSuppressCachedWinner() {
        val fixture = Fixture()
        fixture.cache.markBenchmarkAttempted(fixture.settings.operatorCode, LocalRuntime)
        fixture.cache.saveBenchmarkWinner(fixture.settings.operatorCode, LocalRuntime, "yandex", Yandex)
        val prepared = fixture.coordinator.prepare(fixture.settings.copy(customResolverText = Yandex.joinToString("\n")))
        assertEquals(Yandex.joinToString("\n"), prepared.resolverText)
    }

    @Test
    fun disabledCustomYandexListDoesNotSuppressBenchmarkCandidate() = runBlocking {
        val fixture = Fixture(localScore = score(successes = 6), yandexScore = score(speed = 200))
        val winner = fixture.choose(fixture.settings.copy(customResolverText = Yandex.joinToString("\n")))
        assertEquals(Yandex.joinToString("\n"), winner.resolverText)
        assertEquals("start:yandex", fixture.events.last())
        assertTrue(fixture.events.contains("measure:Yandex DNS"))
    }

    @Test
    fun yandexStartupHealthFailureRestoresReliableLocalAndDoesNotRetrySameLaunch() = runBlocking {
        val fixture = Fixture(startFailure = IllegalStateException("Yandex health failed"))
        assertEquals(LocalRuntime.joinToString("\n"), fixture.choose(fixture.settings).resolverText)
        assertEquals(listOf(
            "measure:Local DNS", "delay:3000", "stop", "delay:3000", "start:yandex",
            "stop", "delay:3000", "start:local",
        ), fixture.events)
        assertEquals("local", fixture.cache.readBenchmarkWinner(fixture.settings.operatorCode, LocalRuntime))
        assertEquals(0, fixture.cache.readBenchmarkLastLaunchBucket(fixture.settings.operatorCode, LocalRuntime))
        assertTrue(fixture.logs.any { it.contains("Yandex health failed") && it.contains("restoring local") })
        fixture.events.clear()
        fixture.choose(fixture.coordinator.prepare(fixture.settings))
        assertTrue(fixture.events.isEmpty())
        assertEquals(1, fixture.cache.launchCount)
    }

    @Test
    fun yandexFailureRestoresWorkingButUnreliableLocalWithoutCachingWinner() = runBlocking {
        val fixture = Fixture(localScore = score(samples = 0), startFailure = IllegalStateException("Yandex failed"))
        assertEquals(LocalRuntime.joinToString("\n"), fixture.choose(fixture.settings).resolverText)
        assertEquals("start:local", fixture.events.last())
        assertNull(fixture.cache.readBenchmarkWinner(fixture.settings.operatorCode, LocalRuntime))
        assertEquals(0, fixture.cache.readBenchmarkLastLaunchBucket(fixture.settings.operatorCode, LocalRuntime))
    }

    @Test
    fun yandexMeasurementFailureAlsoRestoresWorkingLocal() = runBlocking {
        val fixture = Fixture(yandexMeasurementFailure = IllegalStateException("probe failed"))
        assertEquals(LocalRuntime.joinToString("\n"), fixture.choose(fixture.settings).resolverText)
        assertEquals("start:local", fixture.events.last())
        assertTrue(fixture.events.contains("measure:Yandex DNS"))
    }

    @Test
    fun yandexWinnerReadinessFailureRestoresWorkingLocal() = runBlocking {
        val fixture = Fixture(localScore = score(successes = 6), yandexScore = score(speed = 200),
            startFailure = IllegalStateException("winner failed"), failOnStart = 2)
        assertEquals(LocalRuntime.joinToString("\n"), fixture.choose(fixture.settings).resolverText)
        assertEquals("start:local", fixture.events.last())
        assertNull(fixture.cache.readBenchmarkWinner(fixture.settings.operatorCode, LocalRuntime))
    }

    @Test
    fun cancelledYandexStartDoesNotRestartLocal() = runBlocking {
        val fixture = Fixture(startFailure = CancellationException("user stopped VPN"))
        try {
            fixture.choose(fixture.settings)
            error("Expected cancellation")
        } catch (_: CancellationException) {
            assertEquals("start:yandex", fixture.events.last())
            assertEquals(1, fixture.events.count { it == "stop" })
            assertNull(fixture.cache.readBenchmarkWinner(fixture.settings.operatorCode, LocalRuntime))
        }
    }

    @Test
    fun cachedYandexWinnerIsAppliedBetweenRefreshLaunches() = runBlocking {
        val fixture = Fixture()
        fixture.cache.markBenchmarkAttempted(fixture.settings.operatorCode, LocalRuntime)
        fixture.cache.saveBenchmarkWinner(fixture.settings.operatorCode, LocalRuntime, "yandex", Yandex)
        val prepared = fixture.coordinator.prepare(fixture.settings)
        assertEquals(Yandex.joinToString("\n"), prepared.resolverText)
        assertEquals(prepared, fixture.choose(prepared))
        assertTrue(fixture.events.isEmpty())
        assertEquals(1, fixture.cache.launchCount)
    }

    @Test
    fun legacyWinnerIsKeptAndGetsCurrentBucket() {
        val fixture = Fixture()
        fixture.cache.saveBenchmarkWinner(fixture.settings.operatorCode, LocalRuntime, "yandex", Yandex)
        assertEquals(Yandex.joinToString("\n"), fixture.coordinator.prepare(fixture.settings).resolverText)
        assertEquals(0, fixture.cache.readBenchmarkLastLaunchBucket(fixture.settings.operatorCode, LocalRuntime))
    }

    @Test
    fun firstLaunchMeasuresRealInjectedScoresAndWaitsBeforeEveryTransition() = runBlocking {
        val fixture = Fixture(localScore = score(successes = 6), yandexScore = score(speed = 200, successes = 10))
        val winner = fixture.choose(fixture.coordinator.prepare(fixture.settings))
        assertEquals(Yandex.joinToString("\n"), winner.resolverText)
        assertEquals(listOf(
            "measure:Local DNS", "delay:3000", "stop", "delay:3000", "start:yandex",
            "measure:Yandex DNS", "stop", "delay:3000", "start:yandex",
        ), fixture.events)
        assertEquals("yandex", fixture.cache.readBenchmarkWinner(fixture.settings.operatorCode, LocalRuntime))
        assertEquals(1, fixture.cache.launchCount)
    }

    @Test
    fun fastButUnstableYandexDoesNotBeatReliableLocal() = runBlocking {
        val fixture = Fixture(yandexScore = score(speed = 500, successes = 3))
        val winner = fixture.choose(fixture.settings)
        assertEquals(LocalRuntime.joinToString("\n"), winner.resolverText)
        assertEquals("start:local", fixture.events.last())
        assertEquals("local", fixture.cache.readBenchmarkWinner(fixture.settings.operatorCode, LocalRuntime))
    }

    @Test
    fun unreliableLocalIsRestoredButNotCached() = runBlocking {
        val fixture = Fixture(localScore = score(samples = 0, successes = 2), yandexScore = score(samples = 0))
        val winner = fixture.choose(fixture.settings)
        assertEquals(LocalRuntime.joinToString("\n"), winner.resolverText)
        assertNull(fixture.cache.readBenchmarkWinner(fixture.settings.operatorCode, LocalRuntime))
    }

    @Test
    fun tenthAndTwentiethUiLaunchRefreshOnceWithoutServiceBump() = runBlocking {
        val fixture = Fixture()
        fixture.choose(fixture.settings)
        repeat(8) { fixture.uiLaunch() }
        fixture.events.clear()
        fixture.choose(fixture.coordinator.prepare(fixture.settings))
        assertTrue(fixture.events.isEmpty())
        fixture.uiLaunch()
        fixture.choose(fixture.coordinator.prepare(fixture.settings))
        assertEquals(2, fixture.events.count { it.startsWith("measure:") })
        fixture.events.clear()
        fixture.choose(fixture.coordinator.prepare(fixture.settings))
        assertTrue(fixture.events.isEmpty())
        repeat(10) { fixture.uiLaunch() }
        fixture.choose(fixture.coordinator.prepare(fixture.settings))
        assertEquals(2, fixture.events.count { it.startsWith("measure:") })
        assertEquals(20, fixture.cache.launchCount)
    }

    @Test
    fun cancelledSwitchDoesNotStartYandexOrCacheWinner() = runBlocking {
        val fixture = Fixture(cancelOnDelay = true)
        try {
            fixture.choose(fixture.settings)
            error("Expected cancellation")
        } catch (_: CancellationException) {
            assertEquals(listOf("measure:Local DNS", "delay:3000"), fixture.events)
            assertNull(fixture.cache.readBenchmarkWinner(fixture.settings.operatorCode, LocalRuntime))
        }
    }

    @Test
    fun failedShutdownDoesNotDelayOrStartAnotherCandidate() = runBlocking {
        val fixture = Fixture()
        try {
            fixture.coordinator.optimize(fixture.settings, start = { error("Must not start") }, stop = {
                throw IllegalStateException("runtime still alive")
            })
            error("Expected shutdown failure")
        } catch (error: IllegalStateException) {
            assertEquals("runtime still alive", error.message)
            assertEquals(listOf("measure:Local DNS", "delay:3000"), fixture.events)
            assertNull(fixture.cache.readBenchmarkWinner(fixture.settings.operatorCode, LocalRuntime))
        }
    }

    @Test
    fun failedWinnerReconnectDoesNotCacheWinner() = runBlocking {
        val fixture = Fixture()
        var starts = 0
        try {
            fixture.coordinator.optimize(fixture.settings, start = {
                if (++starts == 2) throw IllegalStateException("winner failed")
            }, stop = {})
            error("Expected winner readiness failure")
        } catch (error: IllegalStateException) {
            assertEquals("winner failed", error.message)
            assertNull(fixture.cache.readBenchmarkWinner(fixture.settings.operatorCode, LocalRuntime))
        }
    }

    @Test
    fun customEnabledNeverReadsPhysicalDnsOrMutatesAutomaticCache() = runBlocking {
        val backing = memoryResolverPreferences()
        val preferences = object : SharedPreferences by backing {
            override fun edit(): SharedPreferences.Editor = error("custom mode must not write automatic cache")
        }
        val cache = ResolverCacheStore(preferences, incrementLaunchCount = false)
        val coordinator = ServiceResolverCoordinator(cache,
            measureScore = { _, _, _ -> error("custom mode must not benchmark") },
            physicalDnsResolvers = { error("custom mode must not read physical network") })
        val settings = WhiteZiaSettings(customResolversEnabled = true, customResolverText = "10.99.0.53")
        val pools = coordinator.preparePools(settings)
        assertEquals(1, pools.size)
        val prepared = pools.single()
        assertEquals("10.99.0.53", prepared.resolverText)
        assertEquals(prepared, coordinator.optimize(prepared, start = { error("must not start") }, stop = { error("must not stop") }))
        assertEquals(0, cache.launchCount)
        assertTrue(cache.readCachedResolvers(settings.operatorCode) { true }.isEmpty())
        assertNull(cache.readBenchmarkWinner(settings.operatorCode, LocalRuntime))
    }

    @Test
    fun rawCachedWinnerCannotReintroduceStalePrivateEndpoints() {
        val fixture = Fixture(physicalDns = listOf("10.20.0.53", "10.20.0.54"))
        val expected = listOf("10.20.0.53", "10.20.0.54")
        val settings = fixture.settings.copy(resolverText = "10.99.0.53")
        fixture.cache.markBenchmarkAttempted(settings.operatorCode, expected)
        fixture.cache.saveBenchmarkWinner(settings.operatorCode, expected, "10.99.0.53\n100.64.1.1", listOf("10.99.0.53"))
        assertEquals(expected.joinToString("\n"), fixture.coordinator.prepare(settings).resolverText)
    }

    @Test
    fun optimizeRepairsStalePrivateSeedsEvenWhenBenchmarkIsNotDue() = runBlocking {
        val fixture = Fixture(physicalDns = listOf("10.20.0.53", "10.20.0.54"))
        val expected = listOf("10.20.0.53", "10.20.0.54")
        fixture.cache.markBenchmarkAttempted(fixture.settings.operatorCode, expected)
        val starts = mutableListOf<WhiteZiaSettings>()
        var stops = 0
        val selected = fixture.coordinator.optimize(fixture.settings.copy(resolverText = "10.99.0.53\n100.64.1.1"),
            start = { starts += it }, stop = { stops++ })
        assertEquals(expected.joinToString("\n"), selected.resolverText)
        assertEquals(listOf(selected), starts)
        assertEquals(1, stops)
        assertFalse(fixture.events.any { it.startsWith("measure:") })
    }

    @Test
    fun physicalPoolIsNeverMixedOrReplacedByCachedPublicWinnerOrBenchmark() = runBlocking {
        val physical = listOf("10.20.0.53", "10.20.0.54")
        val cache = ResolverCacheStore(memoryResolverPreferences(), incrementLaunchCount = false)
        val started = mutableListOf<List<String>>()
        val measured = mutableListOf<List<String>>()
        val coordinator = ServiceResolverCoordinator(cache,
            physicalDnsResolvers = { physical }, delayMillis = {}, measureScore = { settings, label, _ ->
                measured += settings.resolverText.lines()
                score(speed = if (label == "Yandex DNS") 200 else 100)
            })
        val original = WhiteZiaSettings(resolverText = "176.59.127.146\n10.99.0.53")
        cache.markBenchmarkAttempted(original.operatorCode, physical)
        cache.saveBenchmarkWinner(original.operatorCode, physical, "yandex", Yandex)
        val pools = coordinator.preparePools(original)
        assertEquals(2, pools.size)
        assertEquals(physical, pools.first().resolverText.lines())
        assertTrue(pools.last().resolverText.lines().none { it in physical || it == "10.99.0.53" })
        assertTrue(pools.last().resolverText.lines().any { it in Yandex })
        assertEquals("176.59.127.146\n10.99.0.53", original.resolverText)
        val settings = pools.first()
        val winner = coordinator.optimize(settings, start = { started += it.resolverText.lines() }, stop = {})
        assertEquals(settings, winner)
        assertTrue(measured.isEmpty())
        assertTrue(started.isEmpty())
        assertEquals(physical, winner.resolverText.lines())
    }

    @Test
    fun verifiedPublicFallbackIsNotImmediatelyReplacedByTheFailedPhysicalPool() = runBlocking {
        val fixture = Fixture(physicalDns = listOf("10.20.0.53", "10.20.0.54"))
        val fallback = fixture.coordinator.preparePools(fixture.settings).last()
        assertTrue(fallback.resolverText.lines().none { it.startsWith("10.") })
        assertEquals(fallback, fixture.choose(fallback))
        assertTrue(fixture.events.isEmpty())
        // A fresh connection still tries the current physical network before that public fallback.
        assertEquals("10.20.0.53\n10.20.0.54", fixture.coordinator.prepare(fallback).resolverText)
    }

    @Test
    fun optimizeRepairsAnOldMixedPoolToOnlyCurrentPhysicalDns() = runBlocking {
        val physical = listOf("10.20.0.53", "10.20.0.54")
        val fixture = Fixture(physicalDns = physical)
        val starts = mutableListOf<String>()
        var stops = 0
        val chosen = fixture.coordinator.optimize(fixture.settings.copy(resolverText = physical.first() + "\n77.88.8.8"),
            start = { starts += it.resolverText }, stop = { stops++ })
        assertEquals(physical.joinToString("\n"), chosen.resolverText)
        assertEquals(listOf(chosen.resolverText), starts)
        assertEquals(1, stops)
        assertTrue(fixture.events.none { it.startsWith("measure:") })
    }

    private class Fixture(
        localScore: ResolverBenchmarkScore = score(),
        yandexScore: ResolverBenchmarkScore = score(),
        cancelOnDelay: Boolean = false,
        physicalDns: List<String> = emptyList(),
        cellularOperator: String? = null,
        operatorCatalog: shop.whitezia.client.resolver.VerifiedMobileResolverCatalog = shop.whitezia.client.resolver.VerifiedMobileResolverCatalog.Empty,
        private val startFailure: Exception? = null,
        private val failOnStart: Int = 1,
        yandexMeasurementFailure: Exception? = null,
    ) {
        private val preferences = memoryResolverPreferences()
        val cache = ResolverCacheStore(preferences, incrementLaunchCount = false)
        val settings = WhiteZiaSettings(operatorCode = WhiteZiaOptions.OperatorMts, resolverText = LocalRuntime.joinToString("\n"))
        val events = mutableListOf<String>()
        val logs = mutableListOf<String>()
        private var starts = 0
        val coordinator = ServiceResolverCoordinator(cache, measureScore = { _, label, _ ->
            events += "measure:$label"
            if (label == "Yandex DNS" && yandexMeasurementFailure != null) throw yandexMeasurementFailure
            if (label == "Local DNS") localScore else yandexScore
        }, delayMillis = {
            events += "delay:$it"
            if (cancelOnDelay) throw CancellationException("user stopped VPN")
        }, physicalDnsResolvers = { physicalDns },
            networkSnapshot = { AutomaticResolverNetwork(physicalDns, cellularOperator) },
            operatorCatalog = operatorCatalog, nowMillis = { CatalogNow })

        init { uiLaunch() }

        fun uiLaunch() { ResolverCacheStore(preferences) }

        suspend fun choose(settings: WhiteZiaSettings): WhiteZiaSettings = coordinator.optimize(
            settings,
            start = {
                events += "start:${if (it.resolverText.startsWith(Local.single())) "local" else "yandex"}"
                if (++starts == failOnStart && startFailure != null) throw startFailure
            },
            stop = { events += "stop" },
            log = { logs += it },
        )
    }

    private companion object {
        const val CatalogAddress = "176.59.127.150"
        val CatalogNow = java.time.Instant.parse("2026-10-24T12:00:00Z").toEpochMilli()
        fun operatorCatalog() = shop.whitezia.client.resolver.VerifiedMobileResolverCatalog.parse("""
            {"schemaVersion":1,"generatedAt":"2026-10-24T00:00:00Z","expiresAt":"2026-10-27T00:00:00Z",
            "operators":[{"code":"mts","asns":[15378],"resolvers":[{"address":"$CatalogAddress","port":53,
            "source":{"kind":"bgp-origin-masscan","asn":15378,"prefix":"176.59.127.0/24","url":"https://bgp.he.net/AS15378#_prefixes",
            "prefixSourceUrl":"https://stat.ripe.net/data/ris-prefixes/data.json?resource=AS15378&types=o&af=v4",
            "prefixSnapshotSha256":"${"a".repeat(64)}"},
            "verifiedAt":"2026-10-23T23:00:00Z","expiresAt":"2026-10-27T00:00:00Z","checks":[
            {"kind":"dns-recursion","transport":"udp","passed":true},
            {"kind":"normal-tunnel","transport":"udp","passed":true}]}]}]}
        """.trimIndent(), CatalogNow)
        val Local = listOf("176.59.127.146")
        val Yandex = listOf("77.88.8.8", "77.88.8.1", "77.88.8.2", "77.88.8.3", "77.88.8.7", "77.88.8.88")
        val LocalRuntime = listOf("176.59.127.146", "77.88.8.8", "77.88.8.1")

        fun score(speed: Long = 100, samples: Int = 1, successes: Int = 10) = ResolverBenchmarkScore(
            label = "test", speedBytesPerSecond = speed, speedSuccessfulSamples = samples,
            healthSuccesses = 1, resolverSuccesses = successes, resolverAttempts = 10,
            averageResolverLatencyMillis = 10,
        )
    }
}
