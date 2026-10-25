package shop.whitezia.client.runtime

import java.net.URL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class CottenMaxHealthDestinationTest {
    @Test fun marksOnlyTheFourOwnedHealthHosts() {
        listOf("connectivitycheck.gstatic.com", "www.google.com", "www.cloudflare.com", "ya.ru").forEach { host ->
            assertEquals("$host.whitezia-health.invalid", CottenMaxHealthDestination.forUrl(URL("https://$host/health")))
            CottenMaxHealthDestination.validateInitial("https://$host:443/health", "$host.whitezia-health.invalid")
        }
        assertEquals("www.cloudflare.com.whitezia-health.invalid", CottenMaxHealthDestination.forUrl(URL("https://WWW.CLOUDFLARE.COM/")))
    }

    @Test fun rejectsThroughputSitesUserDestinationsAndNonHttpsPorts() {
        listOf("https://speed.cloudflare.com/__down?bytes=65536", "https://example.com/", "https://www.google.com.attacker.invalid/",
            "https://www.cloudflare.com.whitezia-health.invalid/", "http://www.google.com/", "https://www.google.com:8443/",
            "https://user@www.google.com/").forEach { value ->
            assertThrows(IllegalArgumentException::class.java) { CottenMaxHealthDestination.forUrl(URL(value)) }
        }
    }

    @Test fun anOverrideCannotRedirectTlsToAnUnrelatedDestination() {
        assertThrows(IllegalArgumentException::class.java) {
            CottenMaxHealthDestination.validateInitial("https://www.google.com/", "www.cloudflare.com.whitezia-health.invalid")
        }
        assertThrows(IllegalArgumentException::class.java) {
            CottenMaxHealthDestination.validateInitial("https://www.google.com/", "www.google.com")
        }
    }

    @Test fun reserveRequiresALocalSocksListenerAndIsOptIn() {
        assertNull(ProbeHttpRequest("https://www.google.com/").socksDestinationHost)
        assertThrows(IllegalArgumentException::class.java) {
            OwnedHttpTransport().newCall(ProbeHttpRequest("https://www.google.com/",
                socksDestinationHost = "www.google.com.whitezia-health.invalid"))
        }
    }
}
