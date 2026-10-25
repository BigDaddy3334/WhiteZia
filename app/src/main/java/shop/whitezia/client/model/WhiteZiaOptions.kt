package shop.whitezia.client.model

import android.os.Process

object WhiteZiaRuntimeProxy {
    const val ListenIp = "127.0.0.1"
    const val DefaultListenPort = "10886"
    const val DefaultListenPortInt = 10886
    const val DefaultHttpProxyPort = "10887"
    const val DefaultHttpProxyPortInt = 10887
    const val DefaultLocalDnsPort = "10888"
    const val DefaultLocalDnsPortInt = 10888

    val ListenPort: String
        get() = currentPorts().listenPort.toString()
    val ListenPortInt: Int
        get() = currentPorts().listenPort
    val HttpProxyPort: String
        get() = currentPorts().httpProxyPort.toString()
    val HttpProxyPortInt: Int
        get() = currentPorts().httpProxyPort
    val LocalDnsPort: String
        get() = currentPorts().localDnsPort.toString()
    val LocalDnsPortInt: Int
        get() = currentPorts().localDnsPort

    internal fun portsForUid(uid: Int): WhiteZiaRuntimePorts {
        val userId = (uid.coerceAtLeast(0) / AndroidUidPerUserRange)
            .coerceAtMost(MaxRuntimeUserId)
        val offset = userId * RuntimePortStride
        return WhiteZiaRuntimePorts(
            listenPort = DefaultListenPortInt + offset,
            httpProxyPort = DefaultHttpProxyPortInt + offset,
            localDnsPort = DefaultLocalDnsPortInt + offset,
        )
    }

    private fun currentPorts(): WhiteZiaRuntimePorts {
        // JVM unit tests use Android stubs; Android itself always returns the current profile UID.
        val uid = runCatching { Process.myUid() }.getOrDefault(DefaultApplicationUid)
        return portsForUid(uid)
    }

    private const val DefaultApplicationUid = 10_000
    private const val AndroidUidPerUserRange = 100_000
    private const val RuntimePortStride = 16
    private const val MaxRuntimeUserId = (65_535 - DefaultLocalDnsPortInt) / RuntimePortStride
}

object WhiteZiaOptions {
    const val OperatorMegafonYota = "megafon_yota"
    const val OperatorMts = "mts"
    const val OperatorBeeline = "beeline"
    const val OperatorTele2 = "tele2"

    const val SplitTunnelModeOff = "off"
    const val SplitTunnelModeInclude = "include"
    const val SplitTunnelModeExclude = "exclude"

    const val TransportAuto = "auto"
    const val TransportXray = "xray"
    const val TransportDns = "dns"

    val connectionModes = listOf(
        Choice("proxy", "Proxy Mode"),
        Choice("vpn", "Full VPN"),
    )

    val themeModes = listOf(
        Choice(WhiteZiaThemeMode.System, "Auto"),
        Choice(WhiteZiaThemeMode.Light, "Light"),
        Choice(WhiteZiaThemeMode.Dark, "Dark"),
    )

    val splitTunnelModes = listOf(
        Choice(SplitTunnelModeOff, "Все приложения"),
        Choice(SplitTunnelModeInclude, "Только выбранные"),
        Choice(SplitTunnelModeExclude, "Исключить выбранные"),
    )

    val encryptionMethods = listOf(
        Choice(0, "None"),
        Choice(1, "XOR"),
        Choice(2, "ChaCha20"),
        Choice(3, "AES-128-GCM"),
        Choice(4, "AES-192-GCM"),
        Choice(5, "AES-256-GCM"),
    )

    val balancingStrategies = listOf(
        Choice(1, "Random"),
        Choice(2, "Round Robin"),
        Choice(3, "Least Loss"),
        Choice(4, "Lowest Latency"),
    )

    val compressionTypes = listOf(
        Choice(0, "OFF"),
        Choice(1, "ZSTD"),
        Choice(2, "LZ4"),
        Choice(3, "ZLIB"),
    )

    val startupModes = listOf(
        Choice("ask", "Ask each time"),
        Choice("resolvers", "Full scan"),
        Choice("logs", "From logs (fast)"),
    )

    val logLevels = listOf(
        Choice("DEBUG", "DEBUG"),
        Choice("INFO", "INFO"),
        Choice("WARN", "WARN"),
        Choice("ERROR", "ERROR"),
    )

    fun encryptionMethodLabel(methodId: Int): String {
        return encryptionMethods.firstOrNull { it.value == methodId }?.label ?: "Unknown"
    }

    fun connectionModeLabel(mode: String): String {
        return connectionModes.firstOrNull { it.value == mode }?.label ?: "Proxy Mode"
    }

    fun themeModeLabel(mode: String): String {
        return themeModes.firstOrNull { it.value == mode }?.label ?: "Auto"
    }

    fun splitTunnelModeLabel(mode: String): String {
        return splitTunnelModes.firstOrNull { it.value == mode }?.label ?: "Все приложения"
    }
}
