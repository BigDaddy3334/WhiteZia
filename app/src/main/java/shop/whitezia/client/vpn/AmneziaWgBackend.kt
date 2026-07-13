package shop.whitezia.client.vpn

import android.os.Build
import android.os.ParcelFileDescriptor
import java.io.ByteArrayInputStream
import kotlinx.coroutines.CancellationException
import org.amnezia.awg.GoBackend
import org.amnezia.awg.config.Config
import shop.whitezia.client.model.WhiteZiaOptions
import shop.whitezia.client.model.WhiteZiaSettings

class AmneziaWgBackend {
    private val stateLock = Any()
    private val nativeOperationLock = Any()
    private var handle = InvalidHandle
    private var retainedTun: ParcelFileDescriptor? = null
    private var generation = 0L

    fun start(
        service: WhiteZiaVpnService,
        settings: WhiteZiaSettings,
        configText: String,
        onLog: (String) -> Unit,
    ) {
        stop()
        val startGeneration = synchronized(stateLock) {
            generation += 1
            generation
        }
        loadNativeBackend()
        val config = parseConfig(configText)
        val goConfig = config.toAwgUserspaceString()
        val requestedMtu = config.`interface`.mtu.orElse(DefaultMtu)
        val effectiveMtu = requestedMtu.coerceIn(MinMtu, MaxSafeMtu)
        onLog("AmneziaWG MTU requested=$requestedMtu effective=$effectiveMtu")
        val tun = buildTun(service, settings, config, effectiveMtu)
        val retainedTunnel = try {
            ParcelFileDescriptor.dup(tun.fileDescriptor)
        } catch (error: Throwable) {
            runCatching { tun.close() }
            throw IllegalStateException("Unable to retain AmneziaWG tunnel descriptor", error)
        }
        var startedHandle = InvalidHandle
        var statePublished = false
        try {
            if (!isCurrentGeneration(startGeneration)) {
                throw CancellationException("AmneziaWG startup was cancelled")
            }
            startedHandle = tun.useDetachedFd { fd ->
                synchronized(nativeOperationLock) {
                    GoBackend.awgTurnOn(InterfaceName, fd, goConfig)
                }
            }
            if (startedHandle < 0) {
                throw IllegalStateException("AmneziaWG backend failed to start: $startedHandle")
            }
            if (!isCurrentGeneration(startGeneration)) {
                throw CancellationException("AmneziaWG startup was cancelled")
            }
            protectBackendSockets(service, startedHandle)
            statePublished = synchronized(stateLock) {
                if (generation != startGeneration) {
                    false
                } else {
                    handle = startedHandle
                    retainedTun = retainedTunnel
                    true
                }
            }
            if (!statePublished) {
                throw CancellationException("AmneziaWG startup was cancelled")
            }
            onLog("AmneziaWG backend started")
            waitForHandshake(startedHandle, startGeneration, onLog)
        } catch (error: Throwable) {
            if (statePublished) {
                stop()
            } else {
                if (startedHandle >= 0) {
                    turnOff(startedHandle)
                }
                closeQuietly(retainedTunnel)
            }
            throw error
        }
    }

    fun stop() {
        val active = synchronized(stateLock) {
            generation += 1
            ActiveTunnel(
                handle = handle,
                retainedTun = retainedTun,
            ).also {
                handle = InvalidHandle
                retainedTun = null
            }
        }
        if (active.handle != InvalidHandle) {
            turnOff(active.handle)
        }
        closeQuietly(active.retainedTun)
    }

    private fun parseConfig(configText: String): Config {
        val bytes = configText.trim().toByteArray(Charsets.UTF_8)
        if (bytes.isEmpty()) {
            throw IllegalArgumentException("AmneziaWG config is empty")
        }
        return Config.parse(ByteArrayInputStream(bytes))
    }

    private fun buildTun(
        service: WhiteZiaVpnService,
        settings: WhiteZiaSettings,
        config: Config,
        effectiveMtu: Int,
    ): ParcelFileDescriptor {
        val builder = service.newVpnBuilder()
            .setSession("WhiteZia AmneziaWG")
            .setMtu(effectiveMtu)

        config.`interface`.addresses.forEach { address ->
            builder.addAddress(address.address, address.mask)
        }
        config.`interface`.dnsServers.forEach { dns ->
            builder.addDnsServer(dns)
        }
        config.`interface`.dnsSearchDomains.forEach { domain ->
            builder.addSearchDomain(domain)
        }
        config.peers.forEach { peer ->
            peer.allowedIps.forEach { route ->
                builder.addRoute(route.address, route.mask)
            }
        }
        configureSplitTunnel(builder, service.packageName, settings)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setMetered(false)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            service.setUnderlyingNetworks(null)
        }
        builder.setBlocking(true)
        return builder.establish()
            ?: throw IllegalStateException("Failed to establish AmneziaWG VPN interface")
    }

    private fun configureSplitTunnel(
        builder: android.net.VpnService.Builder,
        packageName: String,
        settings: WhiteZiaSettings,
    ) {
        val selectedPackages = settings.splitTunnelPackages
            .map(String::trim)
            .filter { it.isNotEmpty() && it != packageName }
            .distinct()
        when (settings.splitTunnelMode) {
            WhiteZiaOptions.SplitTunnelModeInclude -> {
                if (selectedPackages.isNotEmpty()) {
                    runCatching { builder.addAllowedApplication(packageName) }
                }
                selectedPackages.forEach { appPackage ->
                    runCatching { builder.addAllowedApplication(appPackage) }
                }
            }
            WhiteZiaOptions.SplitTunnelModeExclude -> {
                selectedPackages.forEach { appPackage ->
                    runCatching { builder.addDisallowedApplication(appPackage) }
                }
            }
            else -> Unit
        }
    }

    private fun ParcelFileDescriptor.useDetachedFd(block: (Int) -> Int): Int {
        var detached = false
        return try {
            val fd = detachFd()
            detached = true
            block(fd)
        } finally {
            if (!detached) {
                close()
            }
        }
    }

    private fun protectBackendSockets(service: WhiteZiaVpnService, activeHandle: Int) {
        val socketDescriptors = synchronized(nativeOperationLock) {
            listOf(
                GoBackend.awgGetSocketV4(activeHandle),
                GoBackend.awgGetSocketV6(activeHandle),
            )
        }
        socketDescriptors
            .filter { it >= 0 }
            .forEach { fd -> service.protect(fd) }
    }

    private fun waitForHandshake(
        activeHandle: Int,
        startGeneration: Long,
        onLog: (String) -> Unit,
    ) {
        val deadline = System.currentTimeMillis() + HandshakeTimeoutMillis
        while (System.currentTimeMillis() < deadline) {
            if (!isCurrent(activeHandle, startGeneration)) {
                throw CancellationException("AmneziaWG startup was cancelled")
            }
            if (latestHandshakeSeconds(activeHandle) > 0L) {
                onLog("AmneziaWG handshake completed")
                return
            }
            Thread.sleep(HandshakePollMillis)
        }
        if (!isCurrent(activeHandle, startGeneration)) {
            throw CancellationException("AmneziaWG startup was cancelled")
        }
        throw IllegalStateException("AmneziaWG handshake timeout")
    }

    private fun latestHandshakeSeconds(activeHandle: Int): Long {
        val config = synchronized(nativeOperationLock) {
            GoBackend.awgGetConfig(activeHandle)
        } ?: return 0L
        return config
            .lineSequence()
            .firstOrNull { it.startsWith("last_handshake_time_sec=") }
            ?.substringAfter('=')
            ?.toLongOrNull()
            ?: 0L
    }

    private fun isCurrentGeneration(candidateGeneration: Long): Boolean {
        return synchronized(stateLock) { generation == candidateGeneration }
    }

    private fun isCurrent(activeHandle: Int, candidateGeneration: Long): Boolean {
        return synchronized(stateLock) {
            generation == candidateGeneration && handle == activeHandle
        }
    }

    private fun turnOff(activeHandle: Int) {
        runCatching {
            synchronized(nativeOperationLock) {
                GoBackend.awgTurnOff(activeHandle)
            }
        }
    }

    private fun closeQuietly(tunnel: ParcelFileDescriptor?) {
        runCatching { tunnel?.close() }
    }

    private data class ActiveTunnel(
        val handle: Int,
        val retainedTun: ParcelFileDescriptor?,
    )

    private companion object {
        const val InterfaceName = "awg0"
        const val InvalidHandle = -1
        const val DefaultMtu = 1280
        const val MinMtu = 1280
        const val MaxSafeMtu = 1280
        const val HandshakeTimeoutMillis = 7_000L
        const val HandshakePollMillis = 300L

        @Volatile
        var nativeLoaded = false

        fun loadNativeBackend() {
            if (!nativeLoaded) {
                synchronized(AmneziaWgBackend::class.java) {
                    if (!nativeLoaded) {
                        System.loadLibrary("wg-go")
                        nativeLoaded = true
                    }
                }
            }
        }
    }
}
