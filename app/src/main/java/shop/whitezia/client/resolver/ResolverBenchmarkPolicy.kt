package shop.whitezia.client.resolver

data class ResolverBenchmarkScore(
    val label: String,
    val speedBytesPerSecond: Long,
    val speedSuccessfulSamples: Int,
    val healthSuccesses: Int,
    val resolverSuccesses: Int,
    val resolverAttempts: Int,
    val averageResolverLatencyMillis: Long,
) {
    val isUsable: Boolean
        get() = healthSuccesses > 0 && (speedBytesPerSecond > 0L || resolverSuccesses > 0)

    val resolverSuccessRatePercent: Int
        get() = if (resolverAttempts <= 0) {
            0
        } else {
            (resolverSuccesses * 100 / resolverAttempts).coerceIn(0, 100)
        }
}

data class ResolverBenchmarkDecision(
    val preferYandex: Boolean,
    val localReliable: Boolean,
    val yandexReliable: Boolean,
    val speedRatio: Double?,
    val notLessStable: Boolean,
    val latencyAcceptable: Boolean,
    val resolverQualityMargin: Boolean,
    val localUnstableEnough: Boolean,
)

object ResolverBenchmarkPolicy {
    fun decide(
        local: ResolverBenchmarkScore,
        yandex: ResolverBenchmarkScore,
    ): ResolverBenchmarkDecision {
        val localReliable = isReliableWinner(local)
        val yandexReliable = isReliableWinner(yandex)
        val speedRatio = yandex.speedBytesPerSecond
            .takeIf { local.speedBytesPerSecond > 0L }
            ?.toDouble()
            ?.div(local.speedBytesPerSecond)
        val clearSpeedAdvantage =
            yandex.speedBytesPerSecond * YandexSpeedAdvantageDenominator >=
                local.speedBytesPerSecond * YandexSpeedAdvantageNumerator
        val notLessStable = yandex.healthSuccesses >= local.healthSuccesses &&
            yandex.speedSuccessfulSamples >= local.speedSuccessfulSamples &&
            yandex.resolverSuccessRatePercent >= local.resolverSuccessRatePercent
        val latencyAcceptable = local.averageResolverLatencyMillis <= 0L ||
            yandex.averageResolverLatencyMillis <= 0L ||
            yandex.averageResolverLatencyMillis <=
            (local.averageResolverLatencyMillis * YandexLatencyMultiplier)
        val resolverQualityMargin = yandex.resolverSuccessRatePercent >=
            (local.resolverSuccessRatePercent + WinnerResolverRateMarginPercent)
        val localUnstableEnough =
            local.resolverSuccessRatePercent < MinWinnerResolverSuccessRatePercent
        val preferYandex = when {
            !local.isUsable -> yandexReliable
            !yandexReliable -> false
            else -> {
                clearSpeedAdvantage && notLessStable && latencyAcceptable &&
                    (resolverQualityMargin || localUnstableEnough)
            }
        }
        return ResolverBenchmarkDecision(
            preferYandex = preferYandex,
            localReliable = localReliable,
            yandexReliable = yandexReliable,
            speedRatio = speedRatio,
            notLessStable = notLessStable,
            latencyAcceptable = latencyAcceptable,
            resolverQualityMargin = resolverQualityMargin,
            localUnstableEnough = localUnstableEnough,
        )
    }

    fun shouldCacheLocal(
        local: ResolverBenchmarkScore,
        yandex: ResolverBenchmarkScore,
    ): Boolean {
        if (!isReliableWinner(local)) {
            return false
        }
        val yandexClearlyBetter = yandex.isUsable &&
            yandex.resolverSuccessRatePercent >=
                (local.resolverSuccessRatePercent + WinnerResolverRateMarginPercent)
        return !yandexClearlyBetter
    }

    fun isReliableWinner(score: ResolverBenchmarkScore): Boolean {
        return score.healthSuccesses >= MinWinnerHealthSuccesses &&
            score.speedSuccessfulSamples >= MinWinnerSpeedSamples &&
            score.resolverSuccessRatePercent >= MinWinnerResolverSuccessRatePercent
    }

    private const val YandexSpeedAdvantageNumerator = 2L
    private const val YandexSpeedAdvantageDenominator = 1L
    private const val YandexLatencyMultiplier = 2L
    private const val MinWinnerResolverSuccessRatePercent = 70
    private const val WinnerResolverRateMarginPercent = 20
    private const val MinWinnerHealthSuccesses = 1
    private const val MinWinnerSpeedSamples = 1
}
