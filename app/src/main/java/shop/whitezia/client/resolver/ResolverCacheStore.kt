package shop.whitezia.client.resolver

import android.content.Context
import android.content.SharedPreferences
import shop.whitezia.client.model.WhiteZiaOptions
import shop.whitezia.client.model.validateResolverText

internal class ResolverCacheStore(context: Context) {
    private val preferences: SharedPreferences = context.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)

    val launchCount: Int = nextLaunchCount()

    fun mergeResolvers(
        resolvers: List<String>,
        operatorCode: String,
        isCacheable: (String) -> Boolean,
    ) {
        val cacheable = resolvers
            .map(String::trim)
            .filter(String::isNotEmpty)
            .filter(isCacheable)
            .distinct()
        if (cacheable.isEmpty()) return

        val operator = normalizeOperatorCode(operatorCode)
        val global = (readResolverList(KeyCachedResolvers) + cacheable)
            .filter(isCacheable)
            .distinct()
        val operatorResolvers = (readResolverList(cachedResolversKey(operator)) + cacheable)
            .filter(isCacheable)
            .distinct()
        preferences.edit()
            .putString(KeyCachedResolvers, global.joinToString("\n"))
            .putString(cachedResolversKey(operator), operatorResolvers.joinToString("\n"))
            .apply()
    }

    fun discardResolvers(
        unavailableResolvers: List<String>,
        operatorCode: String,
        isCacheable: (String) -> Boolean,
    ): List<String> {
        val unavailable = unavailableResolvers.filter(isCacheable).distinct()
        if (unavailable.isEmpty()) return emptyList()
        val unavailableSet = unavailable.toSet()
        val operator = normalizeOperatorCode(operatorCode)
        val benchmarkLocalResolvers = readBenchmarkLocalResolvers(operator)
        val edit = preferences.edit()

        edit.writeResolverList(
            KeyCachedResolvers,
            readResolverList(KeyCachedResolvers).filterNot(unavailableSet::contains).filter(isCacheable),
        )
        edit.writeResolverList(
            cachedResolversKey(operator),
            readResolverList(cachedResolversKey(operator)).filterNot(unavailableSet::contains).filter(isCacheable),
        )
        if (preferences.getString(KeyLastSuccessfulResolver, null) in unavailableSet) {
            edit.remove(KeyLastSuccessfulResolver)
        }
        val operatorLastResolverKey = lastSuccessfulResolverKey(operator)
        if (preferences.getString(operatorLastResolverKey, null) in unavailableSet) {
            edit.remove(operatorLastResolverKey)
        }

        edit.removeBenchmark(operator, unavailable)
        if (benchmarkLocalResolvers.any(unavailableSet::contains)) {
            edit.removeBenchmark(operator, benchmarkLocalResolvers)
                .remove(benchmarkLastLaunchBucketKey(operator))
                .remove(benchmarkLocalResolversKey(operator))
        }
        edit.apply()
        return unavailable
    }

    fun readCachedResolvers(
        operatorCode: String,
        isCacheable: (String) -> Boolean,
    ): List<String> {
        val operator = normalizeOperatorCode(operatorCode)
        val lastSuccessful = preferences.getString(lastSuccessfulResolverKey(operator), null)
            ?: preferences.getString(KeyLastSuccessfulResolver, null)
        return (listOfNotNull(lastSuccessful) +
            readResolverList(cachedResolversKey(operator)) +
            readResolverList(KeyCachedResolvers))
            .mapNotNull { validateResolverText(it).normalizedResolvers.firstOrNull() }
            .filter(isCacheable)
            .distinct()
    }

    fun readAutoTuneWinner(operatorCode: String): String? = preferences
        .getString(autoTuneWinnerKey(operatorCode), null)
        ?.takeIf(String::isNotBlank)

    fun saveAutoTuneWinner(operatorCode: String, configId: String) {
        if (configId.isBlank()) return
        preferences.edit().putString(autoTuneWinnerKey(operatorCode), configId).apply()
    }

    fun readBenchmarkWinner(operatorCode: String, localResolvers: List<String>): String? {
        val currentKey = benchmarkWinnerKey(operatorCode, localResolvers)
        preferences.getString(currentKey, null)?.takeIf(String::isNotBlank)?.let { return it }
        val legacyWinner = preferences
            .getString(legacyBenchmarkWinnerKey(operatorCode, localResolvers), null)
            ?.takeIf(String::isNotBlank)
            ?: return null
        val edit = preferences.edit().putString(currentKey, legacyWinner)
        preferences.getString(legacyBenchmarkWinnerResolversKey(operatorCode, localResolvers), null)
            ?.takeIf(String::isNotBlank)
            ?.let { edit.putString(benchmarkWinnerResolversKey(operatorCode, localResolvers), it) }
        edit.apply()
        return legacyWinner
    }

    fun readBenchmarkLocalResolvers(operatorCode: String): List<String> =
        readResolverList(benchmarkLocalResolversKey(operatorCode))

    fun readBenchmarkLastLaunchBucket(operatorCode: String, localResolvers: List<String>): Int? {
        val operator = normalizeOperatorCode(operatorCode)
        val currentKey = benchmarkLastLaunchBucketKey(operator)
        if (preferences.contains(currentKey)) return preferences.getInt(currentKey, 0)
        val legacyKey = legacyBenchmarkLastLaunchBucketKey(operator, localResolvers)
        if (!preferences.contains(legacyKey)) return null
        return preferences.getInt(legacyKey, 0).also { bucket ->
            preferences.edit().putInt(currentKey, bucket).apply()
        }
    }

    fun markBenchmarkAttempted(operatorCode: String, localResolvers: List<String>) {
        preferences.edit()
            .putString(benchmarkLocalResolversKey(operatorCode), localResolvers.joinToString("\n"))
            .putInt(
                benchmarkLastLaunchBucketKey(operatorCode),
                ResolverBenchmarkSchedule.launchBucket(launchCount),
            )
            .apply()
    }

    fun saveBenchmarkWinner(
        operatorCode: String,
        localResolvers: List<String>,
        winnerId: String,
        winnerResolvers: List<String>,
    ) {
        preferences.edit()
            .putString(benchmarkWinnerKey(operatorCode, localResolvers), winnerId)
            .putString(benchmarkWinnerResolversKey(operatorCode, localResolvers), winnerResolvers.joinToString("\n"))
            .apply()
    }

    fun rememberSuccessfulResolver(operatorCode: String, resolver: String): Boolean {
        val key = lastSuccessfulResolverKey(operatorCode)
        if (preferences.getString(key, null) == resolver) return false
        preferences.edit()
            .putString(KeyLastSuccessfulResolver, resolver)
            .putString(key, resolver)
            .apply()
        return true
    }

    private fun nextLaunchCount(): Int {
        val next = preferences.getInt(KeyResolverBenchmarkLaunchCount, 0)
            .coerceIn(0, Int.MAX_VALUE - 1) + 1
        preferences.edit().putInt(KeyResolverBenchmarkLaunchCount, next).apply()
        return next
    }

    private fun readResolverList(key: String): List<String> = preferences
        .getString(key, null)
        ?.let { validateResolverText(it).normalizedResolvers }
        .orEmpty()

    private fun SharedPreferences.Editor.writeResolverList(
        key: String,
        resolvers: List<String>,
    ): SharedPreferences.Editor {
        return if (resolvers.isEmpty()) remove(key) else putString(key, resolvers.distinct().joinToString("\n"))
    }

    private fun SharedPreferences.Editor.removeBenchmark(
        operatorCode: String,
        resolvers: List<String>,
    ): SharedPreferences.Editor =
        remove(benchmarkWinnerKey(operatorCode, resolvers))
            .remove(benchmarkWinnerResolversKey(operatorCode, resolvers))
            .remove(benchmarkUseCountKey(operatorCode, resolvers))
            .remove(legacyBenchmarkLastLaunchBucketKey(operatorCode, resolvers))

    private fun cachedResolversKey(operatorCode: String) = "$KeyCachedResolvers.${normalizeOperatorCode(operatorCode)}"
    private fun lastSuccessfulResolverKey(operatorCode: String) =
        "$KeyLastSuccessfulResolver.${normalizeOperatorCode(operatorCode)}"
    private fun autoTuneWinnerKey(operatorCode: String) =
        "$KeyAutoTuneWinnerConfig.${normalizeOperatorCode(operatorCode)}"
    private fun benchmarkWinnerKey(operatorCode: String, resolvers: List<String>) =
        "$KeyResolverBenchmarkWinner.${normalizeOperatorCode(operatorCode)}.${signature(resolvers)}"
    private fun benchmarkWinnerResolversKey(operatorCode: String, resolvers: List<String>) =
        "$KeyResolverBenchmarkWinnerResolvers.${normalizeOperatorCode(operatorCode)}.${signature(resolvers)}"
    private fun legacyBenchmarkWinnerKey(operatorCode: String, resolvers: List<String>) =
        "$KeyResolverBenchmarkWinner.${normalizeOperatorCode(operatorCode)}.${legacySignature(resolvers)}"
    private fun legacyBenchmarkWinnerResolversKey(operatorCode: String, resolvers: List<String>) =
        "$KeyResolverBenchmarkWinnerResolvers.${normalizeOperatorCode(operatorCode)}.${legacySignature(resolvers)}"
    private fun benchmarkUseCountKey(operatorCode: String, resolvers: List<String>) =
        "$KeyResolverBenchmarkUseCount.${normalizeOperatorCode(operatorCode)}.${signature(resolvers)}"
    private fun benchmarkLocalResolversKey(operatorCode: String) =
        "$KeyResolverBenchmarkLocalResolvers.${normalizeOperatorCode(operatorCode)}"
    private fun benchmarkLastLaunchBucketKey(operatorCode: String) =
        "$KeyResolverBenchmarkLastLaunchBucketV2.${normalizeOperatorCode(operatorCode)}"
    private fun legacyBenchmarkLastLaunchBucketKey(operatorCode: String, resolvers: List<String>) =
        "$KeyResolverBenchmarkLastLaunchBucket.${normalizeOperatorCode(operatorCode)}.${legacySignature(resolvers)}"

    private fun signature(resolvers: List<String>) = ResolverBenchmarkSchedule.resolverSignature(normalize(resolvers))
    private fun legacySignature(resolvers: List<String>) =
        normalize(resolvers).distinct().joinToString(",").hashCode().toString()
    private fun normalize(resolvers: List<String>) =
        validateResolverText(resolvers.joinToString("\n")).normalizedResolvers

    private fun normalizeOperatorCode(operatorCode: String): String = when (operatorCode) {
        WhiteZiaOptions.OperatorMegafonYota,
        WhiteZiaOptions.OperatorMts,
        WhiteZiaOptions.OperatorBeeline,
        WhiteZiaOptions.OperatorTele2,
        -> operatorCode
        else -> WhiteZiaOptions.OperatorMegafonYota
    }

    private companion object {
        const val PreferencesName = "whitezia_fast_resolver"
        const val KeyLastSuccessfulResolver = "last_successful_resolver"
        const val KeyCachedResolvers = "cached_resolvers"
        const val KeyAutoTuneWinnerConfig = "auto_tune_winner_config"
        const val KeyResolverBenchmarkLaunchCount = "resolver_benchmark_launch_count"
        const val KeyResolverBenchmarkWinner = "resolver_benchmark_winner"
        const val KeyResolverBenchmarkWinnerResolvers = "resolver_benchmark_winner_resolvers"
        const val KeyResolverBenchmarkUseCount = "resolver_benchmark_use_count"
        const val KeyResolverBenchmarkLocalResolvers = "resolver_benchmark_local_resolvers"
        const val KeyResolverBenchmarkLastLaunchBucket = "resolver_benchmark_last_launch_bucket"
        const val KeyResolverBenchmarkLastLaunchBucketV2 = "resolver_benchmark_last_launch_bucket_v2"
    }
}
