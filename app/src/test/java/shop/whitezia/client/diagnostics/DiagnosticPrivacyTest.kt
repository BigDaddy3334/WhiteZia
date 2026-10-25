package shop.whitezia.client.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import shop.whitezia.client.model.ConnectionProfile
import shop.whitezia.client.model.StormDnsCandidate
import shop.whitezia.client.model.WhiteZiaSettings

class DiagnosticPrivacyTest {
    @Test fun removesCredentialsFromUntrustedNativeErrorsAndUrls() {
        val uuid = "12345678-1234-1234-1234-123456789abc"
        val raw = "failed vless://$uuid@node.example:443?type=xhttp; " +
            "stormbundle://privateBundle Bearer jwt.secret.value token=secretValue&route=direct " +
            "{\"refresh_token\":\"refreshValue\",\"password\":\"with spaces\"} id=$uuid"
        val safe = DiagnosticPrivacy.redact(raw)
        listOf(uuid, "privateBundle", "jwt.secret.value", "secretValue", "refreshValue", "with spaces").forEach {
            assertFalse("Credential leaked: $it", safe.contains(it))
        }
        assertTrue(safe.contains("route=direct"))
        assertTrue(safe.contains("failed"))
    }

    @Test fun removesKeysFromEverySavedProfileEvenWhenErrorDoesNotLabelThem() {
        val settings = WhiteZiaSettings(
            customServerEncryptionKey = "active-key",
            connectionProfiles = listOf(ConnectionProfile("saved", "Saved", customServerEncryptionKey = "saved-key")),
            stormDnsCandidates = listOf(StormDnsCandidate("node", "primary", "dns.example", "candidate-key", 1)),
        )
        val safe = settings.redactDiagnostics("rejected active-key saved-key candidate-key; TLS timeout node2")
        assertEquals("rejected [redacted] [redacted] [redacted]; TLS timeout node2", safe)
    }

    @Test fun keepsNetworkFactsAndIsIdempotent() {
        val safe = DiagnosticPrivacy.redact("\u001b[31m[ERROR] TLS timeout 192.0.2.1:443 320 ms\u001b[0m token=private")
        assertTrue(safe.contains("TLS timeout 192.0.2.1:443 320 ms"))
        assertFalse(safe.contains("\u001b"))
        assertEquals(safe, DiagnosticPrivacy.redact(safe))
    }

    @Test fun escapedJsonSecretsAndAuthorizationValuesCannotLeakSuffixes() {
        val raw = """{"password":"prefix\"suffix","private_key":"wg-secret"} Basic dXNlcjpwYXNz eyJhbGciOiJIUzI1NiJ9.payload.signature"""
        val safe = DiagnosticPrivacy.redact(raw)
        listOf("prefix", "suffix", "wg-secret", "dXNlcjpwYXNz", "eyJhbGciOiJIUzI1NiJ9").forEach {
            assertFalse("Credential leaked: $it", safe.contains(it))
        }
    }

    @Test fun normalizedAndShortKnownSecretsAreMaskedWithoutErasingWords() {
        val safe = DiagnosticPrivacy.redact("bad dns-key, value abc; callback available", listOf(" dns-key ", "abc"))
        assertEquals("bad [redacted], value [redacted]; callback available", safe)
    }
}
