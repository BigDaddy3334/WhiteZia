package shop.whitezia.client.resolver

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResolverBenchmarkPolicyTest {
    @Test
    fun choosesYandexOnlyWhenItHasClearSpeedAndResolverQualityMargin() {
        val decision = ResolverBenchmarkPolicy.decide(
            local = score(
                speed = 100L,
                health = 1,
                speedSamples = 1,
                resolverSuccesses = 2,
                resolverAttempts = 3,
                latencyMillis = 10L,
            ),
            yandex = score(
                speed = 200L,
                health = 1,
                speedSamples = 1,
                resolverSuccesses = 3,
                resolverAttempts = 3,
                latencyMillis = 20L,
            ),
        )

        assertTrue(decision.preferYandex)
    }

    @Test
    fun doesNotChooseYandexForSpeedAlone() {
        val decision = ResolverBenchmarkPolicy.decide(
            local = score(
                speed = 100L,
                health = 1,
                speedSamples = 1,
                resolverSuccesses = 3,
                resolverAttempts = 3,
                latencyMillis = 10L,
            ),
            yandex = score(
                speed = 400L,
                health = 1,
                speedSamples = 1,
                resolverSuccesses = 3,
                resolverAttempts = 3,
                latencyMillis = 10L,
            ),
        )

        assertFalse(decision.preferYandex)
    }

    @Test
    fun choosesReliableYandexWhenLocalSetIsUnusable() {
        val decision = ResolverBenchmarkPolicy.decide(
            local = score(
                speed = 0L,
                health = 0,
                speedSamples = 0,
                resolverSuccesses = 0,
                resolverAttempts = 3,
                latencyMillis = 0L,
            ),
            yandex = score(
                speed = 1L,
                health = 1,
                speedSamples = 1,
                resolverSuccesses = 3,
                resolverAttempts = 3,
                latencyMillis = 10L,
            ),
        )

        assertTrue(decision.preferYandex)
    }

    @Test
    fun doesNotCacheUnreliableLocalResult() {
        assertFalse(
            ResolverBenchmarkPolicy.shouldCacheLocal(
                local = score(
                    speed = 100L,
                    health = 1,
                    speedSamples = 0,
                    resolverSuccesses = 2,
                    resolverAttempts = 3,
                    latencyMillis = 10L,
                ),
                yandex = score(
                    speed = 1L,
                    health = 1,
                    speedSamples = 1,
                    resolverSuccesses = 3,
                    resolverAttempts = 3,
                    latencyMillis = 10L,
                ),
            ),
        )
    }

    private fun score(
        speed: Long,
        health: Int,
        speedSamples: Int,
        resolverSuccesses: Int,
        resolverAttempts: Int,
        latencyMillis: Long,
    ): ResolverBenchmarkScore {
        return ResolverBenchmarkScore(
            label = "test",
            speedBytesPerSecond = speed,
            speedSuccessfulSamples = speedSamples,
            healthSuccesses = health,
            resolverSuccesses = resolverSuccesses,
            resolverAttempts = resolverAttempts,
            averageResolverLatencyMillis = latencyMillis,
        )
    }
}
