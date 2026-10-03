package shop.whitezia.client.vpn

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
    @Test
    fun customEnabledUsesOnlyCustomWithoutBenchmarkOrCacheWrites() = runBlocking {
        val fixture = Fixture(physicalDns = Local)
        val settings = fixture.settings.copy(customResolversEnabled = true, customResolverText = "8.8.8.8")
        val prepared = fixture.coordinator.prepare(settings)
        assertEquals("8.8.8.8", prepared.resolverText)
        assertEquals(prepared, fixture.choose(prepared))
        assertTrue(fixture.events.isEmpty())
        assertNull(fixture.cache.readBenchmarkLastLaunchBucket(settings.operatorCode, Local))
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
        assertEquals("176.59.127.147", prepared.resolverText)
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
        assertEquals(Local.single(), prepared.resolverText)
        assertEquals(Local, fixture.cache.readCachedResolvers(fixture.settings.operatorCode) { true })
        assertEquals(Local, fixture.cache.readCachedResolvers(WhiteZiaOptions.OperatorBeeline) { true })
        assertEquals(1, fixture.cache.launchCount)
        assertNull(fixture.cache.readBenchmarkLastLaunchBucket(fixture.settings.operatorCode, Local))
    }

    @Test
    fun physicalDnsFiltersInvalidPublicUnusableAndDisabledCustomEntriesBeforeCaching() {
        val fixture = Fixture(physicalDns = listOf(
            "invalid", "8.8.8.8", "77.88.8.8", "0.0.0.0", "127.0.0.1", "224.0.0.1",
            Local.single(), "176.59.127.147:53", "176.59.127.147",
        ))
        val prepared = fixture.coordinator.prepare(fixture.settings.copy(customResolverText = Local.single()))
        assertEquals("176.59.127.147", prepared.resolverText)
        assertEquals(listOf("176.59.127.147"), fixture.cache.readCachedResolvers(fixture.settings.operatorCode) { true })
        assertEquals(Local.single(), prepared.customResolverText)
    }

    @Test
    fun physicalDnsIsNotReadWhenAutomaticCacheAlreadyExists() {
        val fixture = Fixture()
        fixture.cache.mergeResolvers(Local, fixture.settings.operatorCode) { true }
        val coordinator = ServiceResolverCoordinator(fixture.cache,
            measureScore = { _, _, _ -> error("Must not measure") },
            physicalDnsResolvers = { error("Must not read physical DNS") },
        )
        assertEquals(Local.single(), coordinator.prepare(fixture.settings.copy(resolverText = "")).resolverText)
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
        fixture.cache.markBenchmarkAttempted(fixture.settings.operatorCode, Local)
        fixture.cache.saveBenchmarkWinner(fixture.settings.operatorCode, Local, "yandex", Yandex)
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
        assertEquals(Local.single(), fixture.choose(fixture.settings).resolverText)
        assertEquals(listOf(
            "measure:Local DNS", "delay:3000", "stop", "delay:3000", "start:yandex",
            "stop", "delay:3000", "start:local",
        ), fixture.events)
        assertEquals("local", fixture.cache.readBenchmarkWinner(fixture.settings.operatorCode, Local))
        assertEquals(0, fixture.cache.readBenchmarkLastLaunchBucket(fixture.settings.operatorCode, Local))
        assertTrue(fixture.logs.any { it.contains("Yandex health failed") && it.contains("restoring local") })
        fixture.events.clear()
        fixture.choose(fixture.coordinator.prepare(fixture.settings))
        assertTrue(fixture.events.isEmpty())
        assertEquals(1, fixture.cache.launchCount)
    }

    @Test
    fun yandexFailureRestoresWorkingButUnreliableLocalWithoutCachingWinner() = runBlocking {
        val fixture = Fixture(localScore = score(samples = 0), startFailure = IllegalStateException("Yandex failed"))
        assertEquals(Local.single(), fixture.choose(fixture.settings).resolverText)
        assertEquals("start:local", fixture.events.last())
        assertNull(fixture.cache.readBenchmarkWinner(fixture.settings.operatorCode, Local))
        assertEquals(0, fixture.cache.readBenchmarkLastLaunchBucket(fixture.settings.operatorCode, Local))
    }

    @Test
    fun yandexMeasurementFailureAlsoRestoresWorkingLocal() = runBlocking {
        val fixture = Fixture(yandexMeasurementFailure = IllegalStateException("probe failed"))
        assertEquals(Local.single(), fixture.choose(fixture.settings).resolverText)
        assertEquals("start:local", fixture.events.last())
        assertTrue(fixture.events.contains("measure:Yandex DNS"))
    }

    @Test
    fun yandexWinnerReadinessFailureRestoresWorkingLocal() = runBlocking {
        val fixture = Fixture(localScore = score(successes = 6), yandexScore = score(speed = 200),
            startFailure = IllegalStateException("winner failed"), failOnStart = 2)
        assertEquals(Local.single(), fixture.choose(fixture.settings).resolverText)
        assertEquals("start:local", fixture.events.last())
        assertNull(fixture.cache.readBenchmarkWinner(fixture.settings.operatorCode, Local))
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
            assertNull(fixture.cache.readBenchmarkWinner(fixture.settings.operatorCode, Local))
        }
    }

    @Test
    fun cachedYandexWinnerIsAppliedBetweenRefreshLaunches() = runBlocking {
        val fixture = Fixture()
        fixture.cache.markBenchmarkAttempted(fixture.settings.operatorCode, Local)
        fixture.cache.saveBenchmarkWinner(fixture.settings.operatorCode, Local, "yandex", Yandex)
        val prepared = fixture.coordinator.prepare(fixture.settings)
        assertEquals(Yandex.joinToString("\n"), prepared.resolverText)
        assertEquals(prepared, fixture.choose(prepared))
        assertTrue(fixture.events.isEmpty())
        assertEquals(1, fixture.cache.launchCount)
    }

    @Test
    fun legacyWinnerIsKeptAndGetsCurrentBucket() {
        val fixture = Fixture()
        fixture.cache.saveBenchmarkWinner(fixture.settings.operatorCode, Local, "yandex", Yandex)
        assertEquals(Yandex.joinToString("\n"), fixture.coordinator.prepare(fixture.settings).resolverText)
        assertEquals(0, fixture.cache.readBenchmarkLastLaunchBucket(fixture.settings.operatorCode, Local))
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
        assertEquals("yandex", fixture.cache.readBenchmarkWinner(fixture.settings.operatorCode, Local))
        assertEquals(1, fixture.cache.launchCount)
    }

    @Test
    fun fastButUnstableYandexDoesNotBeatReliableLocal() = runBlocking {
        val fixture = Fixture(yandexScore = score(speed = 500, successes = 3))
        val winner = fixture.choose(fixture.settings)
        assertEquals(Local.single(), winner.resolverText)
        assertEquals("start:local", fixture.events.last())
        assertEquals("local", fixture.cache.readBenchmarkWinner(fixture.settings.operatorCode, Local))
    }

    @Test
    fun unreliableLocalIsRestoredButNotCached() = runBlocking {
        val fixture = Fixture(localScore = score(samples = 0, successes = 2), yandexScore = score(samples = 0))
        val winner = fixture.choose(fixture.settings)
        assertEquals(Local.single(), winner.resolverText)
        assertNull(fixture.cache.readBenchmarkWinner(fixture.settings.operatorCode, Local))
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
            assertNull(fixture.cache.readBenchmarkWinner(fixture.settings.operatorCode, Local))
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
            assertNull(fixture.cache.readBenchmarkWinner(fixture.settings.operatorCode, Local))
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
            assertNull(fixture.cache.readBenchmarkWinner(fixture.settings.operatorCode, Local))
        }
    }

    private class Fixture(
        localScore: ResolverBenchmarkScore = score(),
        yandexScore: ResolverBenchmarkScore = score(),
        cancelOnDelay: Boolean = false,
        physicalDns: List<String> = emptyList(),
        private val startFailure: Exception? = null,
        private val failOnStart: Int = 1,
        yandexMeasurementFailure: Exception? = null,
    ) {
        private val preferences = memoryResolverPreferences()
        val cache = ResolverCacheStore(preferences, incrementLaunchCount = false)
        val settings = WhiteZiaSettings(operatorCode = WhiteZiaOptions.OperatorMts, resolverText = Local.single())
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
        }, physicalDnsResolvers = { physicalDns })

        init { uiLaunch() }

        fun uiLaunch() { ResolverCacheStore(preferences) }

        suspend fun choose(settings: WhiteZiaSettings): WhiteZiaSettings = coordinator.optimize(
            settings,
            start = {
                events += "start:${if (it.resolverText == Local.single()) "local" else "yandex"}"
                if (++starts == failOnStart && startFailure != null) throw startFailure
            },
            stop = { events += "stop" },
            log = { logs += it },
        )
    }

    private companion object {
        val Local = listOf("176.59.127.146")
        val Yandex = listOf("77.88.8.8", "77.88.8.1", "77.88.8.2", "77.88.8.3", "77.88.8.7", "77.88.8.88")

        fun score(speed: Long = 100, samples: Int = 1, successes: Int = 10) = ResolverBenchmarkScore(
            label = "test", speedBytesPerSecond = speed, speedSuccessfulSamples = samples,
            healthSuccesses = 1, resolverSuccesses = successes, resolverAttempts = 10,
            averageResolverLatencyMillis = 10,
        )
    }
}
