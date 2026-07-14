package shop.whitezia.client.resolver

import java.util.Locale

internal object ResolverBenchmarkSchedule {
    const val RefreshIntervalLaunches = 10

    fun launchBucket(launchCount: Int): Int {
        return launchCount.coerceAtLeast(1) / RefreshIntervalLaunches
    }

    fun isDue(lastBenchmarkBucket: Int?, launchCount: Int): Boolean {
        return lastBenchmarkBucket == null ||
            launchBucket(launchCount) > lastBenchmarkBucket
    }

    fun resolverSignature(resolvers: List<String>): String {
        return resolvers
            .asSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .map { it.lowercase(Locale.US) }
            .distinct()
            .sorted()
            .joinToString(separator = ",")
            .hashCode()
            .toString()
    }
}
