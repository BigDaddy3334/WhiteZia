package shop.whitezia.client.model

import java.util.Locale

/** DNS carrier transport; independent of the application TCP/UDP carried by the tunnel. */
enum class CottenDnsResolverTransport(val storedValue: String) {
    Auto("auto"),
    Udp("udp"),
    Tcp("tcp");

    companion object {
        fun fromStoredValue(raw: String?): CottenDnsResolverTransport =
            entries.firstOrNull { it.storedValue == raw?.trim()?.lowercase(Locale.ROOT) } ?: Auto

        fun normalize(raw: String?): String = fromStoredValue(raw).storedValue
    }
}
