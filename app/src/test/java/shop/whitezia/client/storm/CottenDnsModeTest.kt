package shop.whitezia.client.storm

import java.util.Base64
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import shop.whitezia.client.model.ConnectionProfile
import shop.whitezia.client.model.CottenDnsMode
import shop.whitezia.client.model.StormDnsServerProfile
import shop.whitezia.client.model.SubscriptionConnectionPreparer
import shop.whitezia.client.model.SubscriptionProfileManager
import shop.whitezia.client.model.WhiteZiaOptions
import shop.whitezia.client.model.WhiteZiaSettings
import shop.whitezia.client.model.importAdvancedSettingsProfileFromToml
import shop.whitezia.client.model.resolve
import shop.whitezia.client.resolver.ResolverBenchmarkScore
import shop.whitezia.client.resolver.ResolverCacheStore
import shop.whitezia.client.resolver.memoryResolverPreferences
import shop.whitezia.client.runtime.RuntimeLaunchRequest
import shop.whitezia.client.runtime.RuntimeLaunchRequestStore
import shop.whitezia.client.vpn.ServiceResolverCoordinator
import shop.whitezia.client.vpn.AutomaticResolverSeeds

class CottenDnsModeTest {
    @Test fun newAndUnknownModesRemainNormal() {
        assertEquals(CottenDnsMode.Normal, WhiteZiaSettings().cottenDnsMode)
        for (raw in listOf("", "normal", "legacy", "unknown", "cottenmax-typo")) {
            assertEquals(CottenDnsMode.Normal, CottenDnsMode.normalize(raw))
            assertEquals(CottenDnsMode.Normal, WhiteZiaSettings(cottenDnsMode = raw).resolve().cottenDnsMode)
            assertEquals("legacy", CottenDnsEngineContract.tunnelMode(raw))
        }
    }

    @Test fun explicitMaxNormalizesWithoutChangingNormalSessionFraming() {
        assertEquals(CottenDnsMode.Max, CottenDnsMode.normalize(" COTTENMAX "))
        assertEquals("cottenmax", CottenDnsEngineContract.tunnelMode(CottenDnsMode.Max))
        assertEquals("legacy", CottenDnsEngineContract.TunnelMode)
        val normal = StormDnsConfigRenderer.renderClientToml(server, settings(CottenDnsMode.Normal))
        assertTrue(normal.contains("TUNNEL_MODE = \"legacy\""))
        assertTrue(normal.contains("LEGACY_SESSION_ID = false"))
    }

    @Test fun maxRuntimeSnapshotRoundTripPreservesFourProfilesCredentialsAndXray() {
        val before = settings(CottenDnsMode.Max)
        val encoded = RuntimeLaunchRequestStore.encode(RuntimeLaunchRequest("mode-fixture", server, before))
        assertEquals(CottenDnsMode.Max, encoded.getJSONObject("settings").getString("cottenDnsMode"))
        val decoded = RuntimeLaunchRequestStore.decode(JSONObject(encoded.toString()))
        assertEquals(CottenDnsMode.Max, decoded.settings.cottenDnsMode)
        assertEquals(server, decoded.serverProfile)
        assertEquals(before.customServerDomain, decoded.settings.customServerDomain)
        assertEquals(before.customServerEncryptionKey, decoded.settings.customServerEncryptionKey)
        assertEquals(before.customServerEncryptionMethod, decoded.settings.customServerEncryptionMethod)
        assertEquals(before.xrayUri, decoded.settings.xrayUri)
        // Runtime requests intentionally flatten the selected profile. Saved profile records
        // remain owned by settings persistence, rather than being replaced by that snapshot.
        assertEquals(4, before.connectionProfiles.size)
    }

    @Test fun missingAndInvalidRuntimeModeDecodeToNormal() {
        for (raw in listOf<String?>(null, "misspelled-max")) {
            val json = JSONObject().put("settings", JSONObject())
            if (raw != null) json.getJSONObject("settings").put("cottenDnsMode", raw)
            val decoded = RuntimeLaunchRequestStore.decode(json)
            assertEquals(CottenDnsMode.Normal, decoded.settings.cottenDnsMode)
            assertEquals("legacy", CottenDnsEngineContract.tunnelMode(decoded.settings.cottenDnsMode))
        }
    }

    @Test fun maxRuntimeAndScannerUseSameBaseDomainAndExistingCredential() {
        val before = settings(CottenDnsMode.Max)
        for (rendered in listOf(
            StormDnsConfigRenderer.renderClientToml(server, before),
            StormDnsConfigRenderer.renderScanClientToml(server, before),
        )) {
            assertTrue(rendered.contains("TUNNEL_MODE = \"cottenmax\""))
            assertTrue(rendered.contains("DOMAINS = [\"mode.example.test\"]"))
            assertTrue(rendered.contains("ENCRYPTION_KEY = \"fixture-existing-credential\""))
            assertTrue(rendered.contains("DATA_ENCRYPTION_METHOD = 1"))
            assertFalse(rendered.contains("cottenmax-v1.mode.example.test"))
        }
        assertEquals(4, before.connectionProfiles.size)
    }

    @Test fun switchingModeDoesNotReplaceAnySavedProfileOrXraySetting() {
        val normal = settings(CottenDnsMode.Normal)
        val max = normal.copy(cottenDnsMode = CottenDnsMode.Max)
        assertEquals(normal.connectionProfiles, max.connectionProfiles)
        assertEquals(normal.customServerDomain, max.customServerDomain)
        assertEquals(normal.customServerEncryptionKey, max.customServerEncryptionKey)
        assertEquals(normal.xrayUri, max.xrayUri)
        assertEquals(normal, max.copy(cottenDnsMode = CottenDnsMode.Normal))
    }

    @Test fun subscriptionRefreshAndConnectRetainExplicitMax() {
        val before = settings(CottenDnsMode.Max)
        val manager = SubscriptionProfileManager()
        val imported = manager.importProfile(before, bundle(), rememberSubscriptionLink = true)
        val prepared = SubscriptionConnectionPreparer.prepare(
            settings = before,
            rawLink = bundle(),
            operatorCode = WhiteZiaOptions.OperatorMts,
            cachedResolverEntries = listOf("10.20.0.53"),
        )
        for (value in listOf(imported, prepared)) {
            assertEquals(CottenDnsMode.Max, value.cottenDnsMode)
            assertEquals(before.xrayUri, value.xrayUri)
            assertEquals(server.domain, value.customServerDomain)
            assertEquals(server.encryptionKey, value.customServerEncryptionKey)
            assertEquals(server.encryptionMethod, value.customServerEncryptionMethod)
            for (profile in before.connectionProfiles) {
                assertTrue(value.connectionProfiles.contains(profile))
            }
        }
    }

    @Test fun advancedPresetImportRetainsMaxAndAllSavedCredentials() {
        val before = settings(CottenDnsMode.Max)
        val imported = before.importAdvancedSettingsProfileFromToml(
            "Fixture advanced preset", "LISTEN_PORT = 12345\nUPLOAD_PACKET_DUPLICATION_COUNT = 2\n",
        )
        assertEquals(CottenDnsMode.Max, imported.cottenDnsMode)
        assertEquals(before.connectionProfiles, imported.connectionProfiles)
        assertEquals(before.customServerDomain, imported.customServerDomain)
        assertEquals(before.customServerEncryptionKey, imported.customServerEncryptionKey)
        assertEquals(before.xrayUri, imported.xrayUri)
        assertEquals("12345", imported.listenPort)
    }

    @Test fun sequentialPhysicalAndPublicResolverPoolsRetainMax() {
        val before = settings(CottenDnsMode.Max).copy(customResolversEnabled = false)
        val cache = ResolverCacheStore(memoryResolverPreferences())
        val coordinator = ServiceResolverCoordinator(
            cache = cache,
            measureScore = { _, _, _ -> error("Preparation must not measure traffic") },
            physicalDnsResolvers = { listOf("10.20.0.53", "10.20.0.54") },
        )
        val pools = coordinator.preparePools(before)
        assertEquals(2, pools.size)
        assertEquals("10.20.0.53\n10.20.0.54", pools.first().resolverText)
        for (pool in pools) {
            assertEquals(CottenDnsMode.Max, pool.cottenDnsMode)
            assertEquals(before.customServerEncryptionKey, pool.customServerEncryptionKey)
            assertEquals(before.xrayUri, pool.xrayUri)
            assertEquals(before.connectionProfiles.map { it.customServerEncryptionKey },
                pool.connectionProfiles.map { it.customServerEncryptionKey })
        }
    }

    @Test fun failedResolverPoolReturnsToPreviousPoolWithoutNormalDowngrade() = runBlocking {
        val cache = ResolverCacheStore(memoryResolverPreferences())
        val before = settings(CottenDnsMode.Max).copy(resolverText = "8.8.8.8", customResolversEnabled = false)
        val coordinator = ServiceResolverCoordinator(
            cache = cache,
            measureScore = { _, label, _ -> ResolverBenchmarkScore(
                label = label, speedBytesPerSecond = 100L, speedSuccessfulSamples = 1,
                healthSuccesses = 3, resolverSuccesses = 6, resolverAttempts = 6,
                averageResolverLatencyMillis = 10L,
            ) },
            delayMillis = {},
        )
        val prepared = coordinator.prepare(before)
        val yandexOnly = AutomaticResolverSeeds.FallbackResolvers.joinToString("\n")
        val attempted = mutableListOf<WhiteZiaSettings>()
        val result = coordinator.optimize(prepared, start = {
            attempted += it
            if (it.resolverText == yandexOnly) error("Fixture resolver authentication failure")
        }, stop = {})
        assertEquals(2, attempted.size)
        assertEquals(yandexOnly, attempted.first().resolverText)
        assertEquals(prepared.resolverText, result.resolverText)
        assertTrue(attempted.all { it.cottenDnsMode == CottenDnsMode.Max })
        assertEquals(CottenDnsMode.Max, result.cottenDnsMode)
        assertEquals(before.customServerEncryptionKey, result.customServerEncryptionKey)
        assertEquals(before.xrayUri, result.xrayUri)
    }

    private fun settings(mode: String) = WhiteZiaSettings(
        cottenDnsMode = mode,
        connectionProfiles = (0 until 4).map { index ->
            ConnectionProfile(id = "saved-$index", name = "Saved $index",
                customServerDomain = if (index == 0) server.domain else "saved-$index.example.test",
                customServerEncryptionKey = if (index == 0) server.encryptionKey else "fixture-key-$index",
                customServerEncryptionMethod = 1)
        },
        selectedConnectionProfileId = "saved-0",
        customServerDomain = server.domain,
        customServerEncryptionKey = server.encryptionKey,
        customServerEncryptionMethod = server.encryptionMethod,
        resolverText = "8.8.8.8",
        xrayUri = xrayUri,
    )

    private fun bundle(): String {
        val profile = JSONObject().put("name", "Subscription fixture")
            .put("stormdns", JSONObject().put("domain", server.domain)
                .put("encryption_key", server.encryptionKey).put("encryption_method", server.encryptionMethod))
            .put("xray", JSONObject().put("uri", xrayUri))
        val root = JSONObject().put("schema", "whitezia.bundle").put("version", 2).put("profile", profile)
        return "stormbundle://" + Base64.getUrlEncoder().withoutPadding().encodeToString(root.toString().toByteArray())
    }

    private val server = StormDnsServerProfile("mode-server", "Mode fixture", "mode.example.test", "fixture-existing-credential", 1)
    private val xrayUri = "vless://11111111-1111-4111-8111-111111111111@cdn.example.test:443?encryption=none&security=tls&type=xhttp&sni=cdn.example.test"
}
