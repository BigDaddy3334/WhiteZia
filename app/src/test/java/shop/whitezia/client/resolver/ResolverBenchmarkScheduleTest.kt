package shop.whitezia.client.resolver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResolverBenchmarkScheduleTest {
    @Test
    fun firstConnectionRunsBenchmark() {
        assertTrue(ResolverBenchmarkSchedule.isDue(lastBenchmarkBucket = null, launchCount = 1))
    }

    @Test
    fun benchmarkRunsOncePerTenLaunchBucket() {
        assertFalse(ResolverBenchmarkSchedule.isDue(lastBenchmarkBucket = 0, launchCount = 1))
        assertFalse(ResolverBenchmarkSchedule.isDue(lastBenchmarkBucket = 0, launchCount = 9))
        assertTrue(ResolverBenchmarkSchedule.isDue(lastBenchmarkBucket = 0, launchCount = 10))
        assertTrue(ResolverBenchmarkSchedule.isDue(lastBenchmarkBucket = 0, launchCount = 14))
        assertFalse(ResolverBenchmarkSchedule.isDue(lastBenchmarkBucket = 1, launchCount = 19))
        assertTrue(ResolverBenchmarkSchedule.isDue(lastBenchmarkBucket = 1, launchCount = 20))
    }

    @Test
    fun resolverSignatureIsStableWhenCacheChangesOrder() {
        val original = ResolverBenchmarkSchedule.resolverSignature(
            listOf("176.59.127.146", "77.88.8.8"),
        )
        val withFastResolverFirst = ResolverBenchmarkSchedule.resolverSignature(
            listOf("77.88.8.8", "176.59.127.146", "77.88.8.8"),
        )

        assertEquals(original, withFastResolverFirst)
    }
}
