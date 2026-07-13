package shop.whitezia.client.ui

internal object ResolverBenchmarkSchedule {
    const val RefreshIntervalLaunches = 10

    fun launchBucket(launchCount: Int): Int {
        return launchCount.coerceAtLeast(1) / RefreshIntervalLaunches
    }

    fun isDue(lastBenchmarkBucket: Int?, launchCount: Int): Boolean {
        return lastBenchmarkBucket == null ||
            launchBucket(launchCount) > lastBenchmarkBucket
    }
}
