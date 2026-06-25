package shop.whitedns.client.vpn

import android.os.Build
import android.os.ParcelFileDescriptor
import java.io.ByteArrayInputStream
import org.amnezia.awg.GoBackend
import org.amnezia.awg.config.Config
import shop.whitedns.client.model.WhiteDnsOptions
import shop.whitedns.client.model.WhiteDnsSettings

class AmneziaWgBackend {
    private var handle: Int = InvalidHandle

    fun start(
        service: WhiteDnsVpnService,
        settings: WhiteDnsSettings,
        configText: String,
        onLog: (String) -> Unit,
    ) {
        stop()
        loadNativeBackend()
        val config = parseConfig(configText)
        val goConfig = config.toAwgUserspaceString()
        val requestedMtu = config.`interface`.mtu.orElse(DefaultMtu)
        val effectiveMtu = requestedMtu.coerceIn(MinMtu, MaxSafeMtu)
        onLog("AmneziaWG MTU requested=$requestedMtu effective=$effectiveMtu")
        val tun = buildTun(service, settings, config, effectiveMtu)
        val startedHandle = tun.useDetachedFd { fd ->
            GoBackend.awgTurnOn(InterfaceName, fd, goConfig)
        }
        if (startedHandle < 0) {
            throw IllegalStateException("AmneziaWG backend failed to start: $startedHandle")
        }
        handle = startedHandle
        protectBackendSockets(service)
        onLog("AmneziaWG backend started")
        waitForHandshake(onLog)
    }

    fun stop() {
        val activeHandle = handle
        handle = InvalidHandle
        if (activeHandle != InvalidHandle) {
            runCatching {
                GoBackend.awgTurnOff(activeHandle)
            }
        }
    }

    private fun parseConfig(configText: String): Config {
        val bytes = configText.trim().toByteArray(Charsets.UTF_8)
        if (bytes.isEmpty()) {
            throw IllegalArgumentException("AmneziaWG config is empty")
        }
        return Config.parse(ByteArrayInputStream(bytes))
    }

    private fun buildTun(
        service: WhiteDnsVpnService,
        settings: WhiteDnsSettings,
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
        settings: WhiteDnsSettings,
    ) {
        val selectedPackages = settings.splitTunnelPackages
            .map(String::trim)
            .filter { it.isNotEmpty() && it != packageName }
            .distinct()
        when (settings.splitTunnelMode) {
            WhiteDnsOptions.SplitTunnelModeInclude -> {
                selectedPackages.forEach { appPackage ->
                    runCatching { builder.addAllowedApplication(appPackage) }
                }
            }
            WhiteDnsOptions.SplitTunnelModeExclude -> {
                runCatching { builder.addDisallowedApplication(packageName) }
                selectedPackages.forEach { appPackage ->
                    runCatching { builder.addDisallowedApplication(appPackage) }
                }
            }
            else -> {
                runCatching { builder.addDisallowedApplication(packageName) }
            }
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

    private fun protectBackendSockets(service: WhiteDnsVpnService) {
        val activeHandle = handle
        if (activeHandle == InvalidHandle) {
            return
        }
        listOf(
            GoBackend.awgGetSocketV4(activeHandle),
            GoBackend.awgGetSocketV6(activeHandle),
        )
            .filter { it >= 0 }
            .forEach { fd -> service.protect(fd) }
    }

    private fun waitForHandshake(onLog: (String) -> Unit) {
        val deadline = System.currentTimeMillis() + HandshakeTimeoutMillis
        while (System.currentTimeMillis() < deadline) {
            if (latestHandshakeSeconds() > 0L) {
                onLog("AmneziaWG handshake completed")
                return
            }
            Thread.sleep(HandshakePollMillis)
        }
        throw IllegalStateException("AmneziaWG handshake timeout")
    }

    private fun latestHandshakeSeconds(): Long {
        val activeHandle = handle
        if (activeHandle == InvalidHandle) {
            return 0L
        }
        val config = GoBackend.awgGetConfig(activeHandle) ?: return 0L
        return config
            .lineSequence()
            .firstOrNull { it.startsWith("last_handshake_time_sec=") }
            ?.substringAfter('=')
            ?.toLongOrNull()
            ?: 0L
    }

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
