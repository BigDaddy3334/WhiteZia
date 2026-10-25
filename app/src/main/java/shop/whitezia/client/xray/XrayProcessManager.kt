package shop.whitezia.client.xray

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import shop.whitezia.client.runtime.ExternalProcessRunner
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicLong
import shop.whitezia.client.model.ResolvedWhiteZiaSettings
import shop.whitezia.client.model.WhiteZiaSettings
import shop.whitezia.client.vpn.physicalInternetNetwork

data class XrayLaunchSpec(
    val binaryFile: File,
    val workingDirectory: File,
    val configFile: File,
)

class XrayProcessManager(
    private val context: Context,
    private val binaryInstaller: XrayBinaryInstaller = XrayBinaryInstaller(context),
) {
    private data class BootstrapOperation(val future: Future<*>, val socket: AtomicReference<Socket?>) {
        fun cancel() {
            future.cancel(true)
            socket.getAndSet(null)?.runCatching { close() }
        }
    }
    private val bootstrapOperation = AtomicReference<BootstrapOperation?>()
    private val bootstrapStopGeneration = AtomicLong()

    private val runner = ExternalProcessRunner<XrayLaunchSpec>(
        name = "Xray",
        startProcess = { launch ->
            ProcessBuilder(
                launch.binaryFile.absolutePath,
                "run", "-config", launch.configFile.absolutePath,
            ).directory(launch.workingDirectory).redirectErrorStream(true).start()
        },
        cleanup = { launch ->
            runCatching { launch.configFile.delete() }
        },
        shouldForwardOutput = ::shouldForwardOutput,
    )

    fun prepareLaunch(
        settings: WhiteZiaSettings,
        resolvedSettings: ResolvedWhiteZiaSettings,
        bootstrapNetwork: Network? = null,
    ): XrayLaunchSpec {
        // The app UID bypasses the VPN for sockets, but native system DNS can still
        // observe the VPN's virtual DNS. Resolve the outer transport explicitly
        // against the physical network, before creating any new native process.
        val transportAddresses = resolveTransportAddresses(settings.xrayUri, bootstrapNetwork)
        val runtimeDir = File(context.noBackupFilesDir, "xray/runtime").apply {
            mkdirs()
        }
        cleanupStaleLaunchFiles(runtimeDir)
        val binaryFile = binaryInstaller.installExecutable()
        val launchId = UUID.randomUUID().toString()
        val configFile = File(runtimeDir, ".wx-$launchId.json")
        configFile.writeText(
            XrayConfigRenderer.renderClientJson(
                xrayUri = settings.xrayUri,
                resolvedSettings = resolvedSettings,
                transportAddresses = transportAddresses,
            ),
        )
        return XrayLaunchSpec(
            binaryFile = binaryFile,
            workingDirectory = runtimeDir,
            configFile = configFile,
        )
    }

    fun start(
        settings: WhiteZiaSettings,
        resolvedSettings: ResolvedWhiteZiaSettings,
        bootstrapNetwork: Network? = null,
        onOutput: (String) -> Unit = {},
    ): XrayLaunchSpec = runner.start({
        prepareLaunch(settings, resolvedSettings, bootstrapNetwork).also {
            onOutput("Xray transport addresses pinned using physical network DNS")
        }
    }, onOutput)

    private fun resolveTransportAddresses(uri: String, requestedNetwork: Network?): Map<XrayTransportEndpoint, String> {
        val generation = bootstrapStopGeneration.get()
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
            ?: error("Physical network service is unavailable for Xray")
        val network = requestedNetwork ?: connectivity.physicalInternetNetwork()
            ?: error("No physical network is available for Xray DNS")
        val capabilities = connectivity.getNetworkCapabilities(network)
            ?: error("Xray physical network is no longer available")
        check(!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            "Xray bootstrap requires a physical internet network"
        }
        val socket = AtomicReference<Socket?>()
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(BootstrapTimeoutMillis)
        val future = try {
            bootstrapWorkers.submit<Map<XrayTransportEndpoint, String>> {
                val client = XrayClientConfigParser.parseVlessUri(uri)
                XrayTransportBootstrap.resolve(client,
                    lookup = { host -> network.getAllByName(host).toList() },
                    canConnect = { address, port ->
                        val remaining = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime()).toInt()
                        check(remaining > 0) { "Xray physical DNS bootstrap timed out" }
                        val probe = network.socketFactory.createSocket()
                        socket.set(probe)
                        try {
                            check(!Thread.currentThread().isInterrupted) { "Xray bootstrap interrupted" }
                            probe.use {
                                it.connect(InetSocketAddress(address, port), remaining.coerceAtMost(1_500))
                            }
                            true
                        } catch (_: java.io.IOException) {
                            false
                        } finally {
                            socket.compareAndSet(probe, null)
                            runCatching { probe.close() }
                        }
                    },
                )
            }
        } catch (_: RejectedExecutionException) {
            error("Xray physical DNS workers are busy")
        }
        val operation = BootstrapOperation(future, socket)
        bootstrapOperation.set(operation)
        if (bootstrapStopGeneration.get() != generation) operation.cancel()
        return try {
            future.get(BootstrapTimeoutMillis, TimeUnit.MILLISECONDS)
        } catch (_: TimeoutException) {
            error("Xray physical DNS bootstrap timed out")
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IllegalStateException("Xray bootstrap interrupted", error)
        } catch (error: ExecutionException) {
            throw IllegalStateException(error.cause?.message ?: "Xray physical DNS bootstrap failed", error.cause)
        } finally {
            bootstrapOperation.compareAndSet(operation, null)
            operation.cancel()
        }
    }

    fun stop(gracePeriodMillis: Long = 1_500L) {
        bootstrapStopGeneration.incrementAndGet()
        bootstrapOperation.get()?.cancel()
        runner.stop(gracePeriodMillis)
    }
    fun isRunning(): Boolean = runner.isRunning()
    fun exitCodeOrNull(): Int? = runner.exitCodeOrNull()

    companion object {
        private const val StaleLaunchFileMaxAgeMillis = 24L * 60L * 60L * 1_000L
        private val LaunchFileRegex = Regex("""\.wx-[A-Za-z0-9-]+\.json""")
        private const val BootstrapTimeoutMillis = 8_000L
        // DNS on old Android may ignore interruption. Bound both live workers and
        // queued work, and never fall back to the process-wide system resolver.
        private val bootstrapWorkers = ThreadPoolExecutor(0, 2, 30L, TimeUnit.SECONDS, SynchronousQueue()) { task ->
            Thread(task, "whitezia-xray-bootstrap").apply { isDaemon = true }
        }

        internal fun shouldForwardOutput(line: String): Boolean {
            if (line.isBlank()) {
                return false
            }
            val normalized = line.lowercase()
            val importantMarkers = listOf(
                "[error]",
                "[warning]",
                " error",
                "failed",
                "unexpected",
                "fatal",
                "panic",
            )
            if (importantMarkers.any(normalized::contains)) {
                return true
            }
            val noisyConnectionMarkers = listOf(
                " accepted tcp:",
                " accepted udp:",
                "proxy/socks:",
                "app/dispatcher:",
                "transport/internet/udp: dispatch request",
                "transport/internet: dialing to",
                "proxy/vless/outbound: tunneling request",
                "transport/internet/splithttp: xhttp is dialing",
            )
            return noisyConnectionMarkers.none(normalized::contains)
        }

        internal fun cleanupStaleLaunchFiles(
            runtimeDir: File,
            nowMillis: Long = System.currentTimeMillis(),
            maxAgeMillis: Long = StaleLaunchFileMaxAgeMillis,
        ) {
            runtimeDir.listFiles()
                ?.asSequence()
                ?.filter { file ->
                    file.isFile &&
                        LaunchFileRegex.matches(file.name) &&
                        nowMillis - file.lastModified() > maxAgeMillis
                }
                ?.forEach { file ->
                    runCatching { file.delete() }
                }
        }
    }
}
