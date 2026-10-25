package shop.whitezia.client.storm

import java.io.File
import shop.whitezia.client.model.CottenDnsMode

/** Stable external-engine boundary. Historical StormDns model/storage names remain compatible. */
object CottenDnsEngineContract {
    const val SourceRevision = "b3d20fe7fdeba8f157e94223c99cae33116207bf"
    const val NativeLibraryName = "libcottendns_client.so"
    const val DisplayName = "CottenDNS"

    // Normal auto transport may scan a full UDP fleet before retrying it over TCP.
    const val SocksStartupTimeoutMillis = 75_000L

    // Upstream calls Normal "legacy"; this is independent of one-byte session framing.
    const val TunnelMode = "legacy"

    fun tunnelMode(mode: String): String = if (CottenDnsMode.normalize(mode) == CottenDnsMode.Max) "cottenmax" else TunnelMode

    fun command(
        binaryFile: File,
        configFile: File,
        resolversFile: File,
        scanOnly: Boolean = false,
    ): List<String> = buildList {
        add(binaryFile.absolutePath)
        add("-config")
        add(configFile.absolutePath)
        add("-resolvers")
        add(resolversFile.absolutePath)
        if (scanOnly) add("-scan-only")
    }
}
