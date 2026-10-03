package shop.whitezia.client.ui

import java.net.InetAddress
import org.amnezia.awg.config.Config

internal fun configuredAmneziaVpnAddresses(configurations: Iterable<String>): Set<InetAddress> =
    configurations.asSequence()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .distinct()
        .flatMap { configText ->
            runCatching {
                Config.parse(configText.byteInputStream(Charsets.UTF_8))
                    .`interface`.addresses.map { it.address }
            }.getOrDefault(emptyList()).asSequence()
        }
        .toSet()
