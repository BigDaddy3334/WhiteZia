package shop.whitezia.client.vpn

import android.content.Context
import android.net.ConnectivityManager
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import shop.whitezia.client.model.WhiteZiaSettings
import shop.whitezia.client.model.CottenDnsMode
import shop.whitezia.client.model.WhiteZiaOptions
import shop.whitezia.client.model.resolve
import shop.whitezia.client.model.updateManualResolverText
import shop.whitezia.client.model.validateResolverText
import shop.whitezia.client.resolver.ResolverBenchmarkPolicy
import shop.whitezia.client.resolver.ResolverBenchmarkSchedule
import shop.whitezia.client.resolver.ResolverBenchmarkScore
import shop.whitezia.client.resolver.ResolverCacheStore
import shop.whitezia.client.resolver.VerifiedMobileResolverCatalog
import shop.whitezia.client.runtime.ConnectionProbeClient

internal class ServiceResolverCoordinator(
    private val cache: ResolverCacheStore,
    private val measureScore: suspend (WhiteZiaSettings, String, (String) -> Unit) -> ResolverBenchmarkScore,
    private val delayMillis: suspend (Long) -> Unit = { delay(it) },
    private val physicalDnsResolvers: () -> List<String> = { emptyList() },
    private val networkSnapshot: (() -> AutomaticResolverNetwork)? = null,
    private val operatorCatalog: VerifiedMobileResolverCatalog = VerifiedMobileResolverCatalog.Empty,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    constructor(context: Context) : this(
        cache = ResolverCacheStore(context, incrementLaunchCount = false),
        measureScore = ServiceResolverMeasurement(context.applicationContext)::measure,
        networkSnapshot = { automaticResolverNetwork(context.applicationContext) },
        operatorCatalog = bundledMobileResolverCatalog(context.applicationContext),
    )

    fun prepare(settings: WhiteZiaSettings): WhiteZiaSettings = preparePools(settings).first()

    /** Ordered runtime candidates; the caller must finish shutdown before starting another pool. */
    fun preparePools(settings: WhiteZiaSettings): List<WhiteZiaSettings> {
        if (settings.customResolversEnabled) {
            return listOf(settings.updateManualResolverText(normalize(settings.customResolverText).joinToString("\n")))
        }
        val network = currentNetwork()
        val seeds = AutomaticResolverSeeds(network.dnsResolvers, settings.customResolverText)
        val operatorResolvers = verifiedOperatorResolvers(network, settings)
        cache.mergeResolvers(seeds.physical, settings.operatorCode, seeds::eligibleAutomatic)
        val local = localResolvers(settings, seeds)
        if (seeds.physical.isNotEmpty() || operatorResolvers.isNotEmpty()) {
            // Old public winners cannot override the current network or join its native MTU tier.
            return seeds.pools(local, operatorResolvers).map { settings.updateManualResolverText(it.joinToString("\n")) }
        }

        val winner = cache.readBenchmarkWinner(settings.operatorCode, local)
        val bucket = cache.readBenchmarkLastLaunchBucket(settings.operatorCode, local)
        if (winner != null && bucket == null) {
            // Older cached winners have no launch metadata; keep them until the next bucket.
            cache.markBenchmarkAttempted(settings.operatorCode, local)
        }
        val due = ResolverBenchmarkSchedule.isDue(
            cache.readBenchmarkLastLaunchBucket(settings.operatorCode, local),
            cache.launchCount,
        )
        val resolvers = if (due) local else seeds.publicPool(when (winner) {
            WinnerYandex -> YandexResolvers
            WinnerLocal, null -> local
            else -> normalize(winner)
        })
        return listOf(settings.updateManualResolverText(resolvers.joinToString("\n")))
    }

    /** The prepared DNS candidate is running. Callbacks complete after readiness/shutdown or throw. */
    suspend fun optimize(
        settings: WhiteZiaSettings,
        start: suspend (WhiteZiaSettings) -> Unit,
        stop: suspend () -> Unit,
        log: (String) -> Unit = {},
    ): WhiteZiaSettings {
        if (settings.customResolversEnabled) return settings
        val network = currentNetwork()
        val seeds = AutomaticResolverSeeds(network.dnsResolvers, settings.customResolverText)
        val operatorResolvers = seeds.operatorPool(verifiedOperatorResolvers(network, settings))
        suspend fun stopAndSettle() {
            currentCoroutineContext().ensureActive()
            stop()
            delayMillis(ReconnectDelayMillis)
            currentCoroutineContext().ensureActive()
        }

        suspend fun reconnect(next: WhiteZiaSettings) {
            stopAndSettle()
            start(next)
        }

        val running = normalize(settings.resolverText)
        val currentResolvers = if (seeds.physical.isNotEmpty() || operatorResolvers.isNotEmpty()) {
            when {
                running.isNotEmpty() && running.all { it in seeds.physical } -> running
                running.isNotEmpty() && running.all(seeds::eligiblePublic) -> running
                else -> seeds.physical.ifEmpty { operatorResolvers }
            }
        } else seeds.publicPool(running)
        val current = settings.updateManualResolverText(currentResolvers.joinToString("\n"))
        if (normalize(settings.resolverText) != normalize(current.resolverText)) reconnect(current)
        if (seeds.physical.isNotEmpty() || operatorResolvers.isNotEmpty()) {
            // Startup already verified this isolated pool. Never disrupt a working physical pool to
            // benchmark public DNS, or immediately retry a physical pool whose health just failed.
            return current
        }
        val local = localResolvers(settings, seeds)
        val yandex = seeds.publicPool(YandexResolvers)
        val operator = settings.operatorCode
        val bucket = cache.readBenchmarkLastLaunchBucket(operator, local)
        if (local == yandex) return current
        if (bucket == null && cache.readBenchmarkWinner(operator, local) != null) {
            cache.markBenchmarkAttempted(operator, local)
            return current
        }
        if (!ResolverBenchmarkSchedule.isDue(bucket, cache.launchCount)) return current

        val localSettings = settings.updateManualResolverText(local.joinToString("\n"))
        cache.markBenchmarkAttempted(operator, local)
        if (normalize(current.resolverText) != local) reconnect(localSettings)
        log("Resolver benchmark: comparing local and Yandex")
        val localScore = measureScore(localSettings, "Local DNS", log)

        suspend fun restoreLocal(error: Exception): WhiteZiaSettings {
            currentCoroutineContext().ensureActive()
            log("Yandex resolver failed: ${error.message ?: error.javaClass.simpleName}; restoring local DNS")
            reconnect(localSettings)
            if (ResolverBenchmarkPolicy.isReliableWinner(localScore)) {
                cache.saveBenchmarkWinner(operator, local, WinnerLocal, local)
            }
            return localSettings
        }

        delayMillis(SwitchDelayMillis)
        currentCoroutineContext().ensureActive()
        val yandexSettings = settings.updateManualResolverText(yandex.joinToString("\n"))
        stopAndSettle()
        val yandexScore = try {
            start(yandexSettings)
            measureScore(yandexSettings, "Yandex DNS", log)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            return restoreLocal(error)
        }
        currentCoroutineContext().ensureActive()

        val decision = ResolverBenchmarkPolicy.decide(localScore, yandexScore)
        val preferYandex = decision.preferYandex
        val winnerSettings = if (preferYandex) yandexSettings else localSettings
        val winnerId = if (preferYandex) WinnerYandex else WinnerLocal
        log("Resolver benchmark winner: $winnerId")
        // Reconnect even when Yandex won so both outcomes use the winner transition.
        stopAndSettle()
        try {
            start(winnerSettings)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (!preferYandex) throw error
            return restoreLocal(error)
        }
        if (preferYandex || ResolverBenchmarkPolicy.shouldCacheLocal(localScore, yandexScore)) {
            cache.saveBenchmarkWinner(operator, local, winnerId, if (preferYandex) yandex else local)
        }
        return winnerSettings
    }

    private fun localResolvers(settings: WhiteZiaSettings, seeds: AutomaticResolverSeeds): List<String> = seeds.publicPool(
        normalize(settings.resolverText) +
            cache.readCachedResolvers(settings.operatorCode, seeds::eligiblePublic) +
            cache.readBenchmarkLocalResolvers(settings.operatorCode),
    )

    private fun currentNetwork(): AutomaticResolverNetwork =
        networkSnapshot?.invoke() ?: AutomaticResolverNetwork(physicalDnsResolvers())

    private fun verifiedOperatorResolvers(network: AutomaticResolverNetwork, settings: WhiteZiaSettings): List<String> =
        operatorCatalog.resolvers(network.cellularOperator, settings.cottenDnsMode, settings.cottenDnsResolverTransport, nowMillis())

    private fun normalize(text: String): List<String> = validateResolverText(text).normalizedResolvers

    private companion object {
        const val WinnerLocal = "local"
        const val WinnerYandex = "yandex"
        const val SwitchDelayMillis = 3_000L
        const val ReconnectDelayMillis = 3_000L
        val YandexResolvers = AutomaticResolverSeeds.FallbackResolvers
    }
}

private class ServiceResolverMeasurement(context: Context) {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val probes = ConnectionProbeClient()

    suspend fun measure(settings: WhiteZiaSettings, label: String, log: (String) -> Unit): ResolverBenchmarkScore =
        withContext(Dispatchers.IO) {
            val port = settings.resolve().listenPort
            val health = probes.measureHttpHealthScore(log, "$label HTTP", socksProxyPort = port,
                useCottenMaxHealthReserve = settings.transportMode == WhiteZiaOptions.TransportDns &&
                    CottenDnsMode.normalize(settings.cottenDnsMode) == CottenDnsMode.Max)
            currentCoroutineContext().ensureActive()
            val speed = probes.measureBenchmarkSpeed(log, label, socksProxyPort = port)
            currentCoroutineContext().ensureActive()
            val network = connectivity?.physicalInternetNetwork()
            var attempts = 0
            var successes = 0
            var latencySum = 0L
            for (resolver in validateResolverText(settings.resolverText).normalizedResolvers) {
                var resolverSuccesses = 0
                repeat(3) {
                    currentCoroutineContext().ensureActive()
                    attempts += 1
                    val latency = runCatching {
                        DatagramSocket().use { socket ->
                            network?.bindSocket(socket)
                            socket.soTimeout = 650
                            val query = DnsQuery
                            val endpoint = resolverEndpoint(resolver)
                            val host = endpoint.host.removeSurrounding("[", "]")
                            val dnsPort = endpoint.port.takeIf { it > 0 } ?: 53
                            socket.connect(InetAddress.getByName(host), dnsPort)
                            val started = System.nanoTime()
                            socket.send(DatagramPacket(query, query.size))
                            val response = DatagramPacket(ByteArray(512), 512)
                            socket.receive(response)
                            val bytes = response.data
                            val valid = response.length >= 12 && bytes[0] == query[0] && bytes[1] == query[1] &&
                                (bytes[2].toInt() and 0x80) != 0 && (bytes[3].toInt() and 0x0F) == 0
                            if (valid) ((System.nanoTime() - started) / 1_000_000).coerceAtLeast(1L) else null
                        }
                    }.getOrNull()
                    if (latency != null) {
                        successes += 1
                        resolverSuccesses += 1
                        latencySum += latency
                    }
                }
                log("$label DNS $resolver: $resolverSuccesses/3")
            }
            ResolverBenchmarkScore(
                label = label,
                speedBytesPerSecond = speed.bestBytesPerSecond,
                speedSuccessfulSamples = speed.successfulSamples,
                healthSuccesses = health.successes,
                resolverSuccesses = successes,
                resolverAttempts = attempts,
                averageResolverLatencyMillis = if (successes > 0) latencySum / successes else 0L,
            )
        }

    private companion object {
        val DnsQuery = byteArrayOf(
            0x42, 0x24, 0x01, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            10, 99, 108, 111, 117, 100, 102, 108, 97, 114, 101, 3, 99, 111, 109,
            0x00, 0x00, 0x01, 0x00, 0x01,
        )
    }
}

private fun resolverEndpoint(resolver: String): URI {
    val authority = if (resolver.count { it == ':' } > 1 && !resolver.startsWith('[')) "[$resolver]" else resolver
    return URI("dns://$authority")
}
