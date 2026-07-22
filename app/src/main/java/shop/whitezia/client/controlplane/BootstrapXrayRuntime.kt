package shop.whitezia.client.controlplane

import android.content.Context
import android.util.Log
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ServerSocket
import java.net.Socket
import shop.whitezia.client.BuildConfig
import shop.whitezia.client.model.WhiteZiaOptions
import shop.whitezia.client.model.WhiteZiaSettings
import shop.whitezia.client.model.resolve
import shop.whitezia.client.model.runtimeConnectionSettings
import shop.whitezia.client.xray.XrayProcessManager

internal class BootstrapXrayRuntime private constructor(context: Context) {
    private val processManager = XrayProcessManager(context.applicationContext)
    private val lock = Any()
    private var listenPort = 0
    private var activeLeases = 0
    private var idleStopGeneration = 0L

    val isConfigured: Boolean
        get() = BuildConfig.BOOTSTRAP_XRAY_URI.startsWith("vless://", ignoreCase = true)

    fun acquireProxy(): BootstrapProxyLease = synchronized(lock) {
        val proxy = ensureProxyLocked()
        activeLeases += 1
        idleStopGeneration += 1
        BootstrapProxyLease(proxy, ::releaseProxy)
    }

    private fun ensureProxyLocked(): Proxy {
        check(isConfigured) { "Bootstrap transport is not configured" }
        if (processManager.isRunning() && listenPort > 0 && canConnect(listenPort)) {
            return httpProxy(listenPort)
        }

        var lastError: Exception? = null
        repeat(StartupAttempts) { attempt ->
            processManager.stop()
            val candidatePort = findAvailablePort()
            val settings = WhiteZiaSettings(
                connectionMode = "proxy",
                transportMode = WhiteZiaOptions.TransportXray,
                xrayUri = BuildConfig.BOOTSTRAP_XRAY_URI,
                listenIp = LoopbackAddress,
                listenPort = candidatePort.toString(),
                protocolType = "HTTP",
                socks5Authentication = false,
                logLevel = "ERROR",
            ).runtimeConnectionSettings()
            try {
                processManager.start(
                    settings = settings,
                    resolvedSettings = settings.resolve(),
                    onOutput = { line -> Log.w(Tag, line) },
                )
                waitUntilReady(candidatePort)
                listenPort = candidatePort
                Log.i(Tag, "Bootstrap control-plane HTTP transport is ready on a dynamic local port")
                return httpProxy(candidatePort)
            } catch (error: InterruptedException) {
                processManager.stop()
                Thread.currentThread().interrupt()
                throw IllegalStateException("Bootstrap Xray startup was interrupted", error)
            } catch (error: Exception) {
                processManager.stop()
                lastError = error
                Log.w(Tag, "Bootstrap Xray startup attempt ${attempt + 1} failed", error)
            }
        }
        throw IllegalStateException("Bootstrap Xray could not start", lastError)
    }

    private fun releaseProxy() {
        val generation = synchronized(lock) {
            activeLeases = (activeLeases - 1).coerceAtLeast(0)
            idleStopGeneration += 1
            if (activeLeases != 0) return
            idleStopGeneration
        }
        Thread({
            try {
                Thread.sleep(IdleStopDelayMillis)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return@Thread
            }
            synchronized(lock) {
                if (activeLeases == 0 && idleStopGeneration == generation) {
                    processManager.stop()
                    listenPort = 0
                    Log.i(Tag, "Stopped idle bootstrap control-plane transport")
                }
            }
        }, "whitezia-bootstrap-idle-stop").apply {
            isDaemon = true
            start()
        }
    }

    private fun waitUntilReady(port: Int) {
        val deadline = System.nanoTime() + StartupTimeoutMillis * 1_000_000L
        while (System.nanoTime() < deadline) {
            if (!processManager.isRunning()) {
                val exitCode = processManager.exitCodeOrNull()
                error("Bootstrap Xray exited during startup${exitCode?.let { " (code $it)" }.orEmpty()}")
            }
            if (canConnect(port)) return
            Thread.sleep(StartupPollMillis)
        }
        processManager.stop()
        error("Bootstrap Xray HTTP listener did not start")
    }

    private fun findAvailablePort(): Int = ServerSocket(0, 1, InetAddress.getByName(LoopbackAddress)).use {
        it.localPort
    }

    private fun canConnect(port: Int): Boolean = runCatching {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(LoopbackAddress, port), SocketProbeTimeoutMillis)
        }
        true
    }.getOrDefault(false)

    private fun httpProxy(port: Int): Proxy = Proxy(
        Proxy.Type.HTTP,
        InetSocketAddress(LoopbackAddress, port),
    )

    companion object {
        private const val Tag = "WhiteZiaBootstrap"
        private const val LoopbackAddress = "127.0.0.1"
        private const val StartupTimeoutMillis = 6_000L
        private const val StartupPollMillis = 50L
        private const val SocketProbeTimeoutMillis = 150
        private const val StartupAttempts = 3
        private const val IdleStopDelayMillis = 60_000L

        @Volatile
        private var instance: BootstrapXrayRuntime? = null

        fun get(context: Context): BootstrapXrayRuntime = instance ?: synchronized(this) {
            instance ?: BootstrapXrayRuntime(context).also { instance = it }
        }
    }
}

internal class BootstrapProxyLease(
    val proxy: Proxy,
    private val release: () -> Unit,
) : AutoCloseable {
    private var closed = false

    override fun close() {
        if (closed) return
        closed = true
        release()
    }
}
