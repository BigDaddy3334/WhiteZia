package shop.whitezia.client.runtime

import java.net.IDN
import java.net.URL
import java.util.Locale

/** Private local SOCKS marker. The Max adapter removes it before the authenticated remote dial. */
internal object CottenMaxHealthDestination {
    const val Suffix = ".whitezia-health.invalid"
    private val Hosts = setOf("connectivitycheck.gstatic.com", "www.google.com", "www.cloudflare.com", "ya.ru")

    fun forUrl(url: URL): String {
        val host = IDN.toASCII(url.host).lowercase(Locale.ROOT)
        require(url.protocol == "https" && (url.port == -1 || url.port == 443) && url.userInfo == null && host in Hosts) {
            "CottenMax health reserve requires an allowed HTTPS probe on port 443"
        }
        return host + Suffix
    }

    fun validateInitial(url: String, destination: String) {
        require(destination == forUrl(URL(url))) { "Invalid CottenMax health SOCKS marker" }
    }
}
