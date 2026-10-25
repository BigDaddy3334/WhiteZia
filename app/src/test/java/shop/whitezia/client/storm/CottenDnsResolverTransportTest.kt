package shop.whitezia.client.storm

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import shop.whitezia.client.model.CottenDnsMode
import shop.whitezia.client.model.CottenDnsResolverTransport
import shop.whitezia.client.model.AdvancedSettingsProfile
import shop.whitezia.client.model.StormDnsServerProfile
import shop.whitezia.client.model.WhiteZiaSettings
import shop.whitezia.client.model.WhiteZiaAutoTunePresets
import shop.whitezia.client.model.applyAdvancedProfile
import shop.whitezia.client.model.applyAutoTunePreset
import shop.whitezia.client.model.importAdvancedSettingsProfileFromToml
import shop.whitezia.client.model.resetAdvancedSettings
import shop.whitezia.client.model.recoverPersistedParallelTestPreset
import shop.whitezia.client.model.resolve
import shop.whitezia.client.model.runtimeConnectionSettings
import shop.whitezia.client.model.saveCurrentAdvancedProfileAs
import shop.whitezia.client.model.selectedAdvancedProfile
import shop.whitezia.client.model.syncSelectedConnectionProfileFields
import shop.whitezia.client.runtime.RuntimeLaunchRequest
import shop.whitezia.client.runtime.RuntimeLaunchRequestStore

class CottenDnsResolverTransportTest {
    private val server = StormDnsServerProfile("fixture", "Fixture", "tunnel.example.test", "fixture-key", 1)

    @Test fun oldAndInvalidConfigurationsKeepAutomaticTransport() {
        assertEquals("auto", WhiteZiaSettings().cottenDnsResolverTransport)
        for (raw in listOf<String?>(null, "", "garbage", "dot", "tcp\"\nQUERY_TYPES = []")) {
            assertEquals(CottenDnsResolverTransport.Auto, CottenDnsResolverTransport.fromStoredValue(raw))
            val json = JSONObject().put("settings", JSONObject())
            if (raw != null) json.getJSONObject("settings").put("cottenDnsResolverTransport", raw)
            assertEquals("auto", RuntimeLaunchRequestStore.decode(json).settings.cottenDnsResolverTransport)
        }
        assertEquals("auto", WhiteZiaSettings(cottenDnsResolverTransport = "invalid").resolve().cottenDnsResolverTransport)
    }

    @Test fun runtimeAndScannerForceOnlyTheChosenCarrierInBothModes() {
        for (mode in listOf(CottenDnsMode.Normal, CottenDnsMode.Max)) {
            for (transport in CottenDnsResolverTransport.entries) {
                val settings = WhiteZiaSettings(cottenDnsMode = mode, cottenDnsResolverTransport = transport.storedValue)
                for (toml in listOf(
                    StormDnsConfigRenderer.renderClientToml(server, settings),
                    StormDnsConfigRenderer.renderScanClientToml(server, settings),
                )) {
                    assertEquals(listOf("RESOLVER_TRANSPORT = \"${transport.storedValue}\""),
                        toml.lineSequence().filter { it.startsWith("RESOLVER_TRANSPORT =") }.toList())
                    assertTrue(toml.contains("TUNNEL_MODE = \"${CottenDnsEngineContract.tunnelMode(mode)}\""))
                    assertTrue(toml.contains("DOMAINS = [\"${server.domain}\"]"))
                    assertTrue(toml.contains("ENCRYPTION_KEY = \"fixture-key\""))
                }
            }
        }
    }

    @Test fun runtimeRoundTripPreservesExplicitTransportAndOtherRoutes() {
        for (transport in CottenDnsResolverTransport.entries) {
            val before = WhiteZiaSettings(cottenDnsMode = CottenDnsMode.Max,
                cottenDnsResolverTransport = " ${transport.storedValue.uppercase()} ",
                customServerDomain = server.domain, customServerEncryptionKey = server.encryptionKey,
                xrayUri = "vless://11111111-1111-4111-8111-111111111111@cdn.example.test:443?type=xhttp&security=tls")
            val request = RuntimeLaunchRequest("transport-fixture", server, before)
            val encoded = RuntimeLaunchRequestStore.encode(request)
            val after = RuntimeLaunchRequestStore.decode(JSONObject(encoded.toString()))
            assertEquals(transport.storedValue, after.settings.cottenDnsResolverTransport)
            assertEquals(server, after.serverProfile)
            assertEquals(before.xrayUri, after.settings.xrayUri)
            assertEquals(before.cottenDnsMode, after.settings.cottenDnsMode)
            assertEquals(transport.storedValue, before.runtimeConnectionSettings().cottenDnsResolverTransport)
        }
    }

    @Test fun advancedProfilesAndTomlRoundTripTransportWithoutReplacingConnections() {
        for (transport in CottenDnsResolverTransport.entries) {
            val before = WhiteZiaSettings(cottenDnsResolverTransport = transport.storedValue,
                cottenDnsMode = CottenDnsMode.Max).syncSelectedConnectionProfileFields()
            val saved = before.saveCurrentAdvancedProfileAs("Carrier fixture")
            assertEquals(transport.storedValue, saved.selectedAdvancedProfile().cottenDnsResolverTransport)
            assertEquals(transport.storedValue, WhiteZiaSettings().applyAdvancedProfile(saved.selectedAdvancedProfile()).cottenDnsResolverTransport)
            val exported = StormDnsConfigRenderer.renderAdvancedSettingsToml(saved)
            val imported = before.importAdvancedSettingsProfileFromToml("Imported carrier", exported)
            assertEquals(transport.storedValue, imported.resolve().cottenDnsResolverTransport)
            assertEquals(before.connectionProfiles, imported.connectionProfiles)
            assertEquals(before.cottenDnsMode, imported.cottenDnsMode)
            assertEquals("auto", imported.resetAdvancedSettings().cottenDnsResolverTransport)
        }
    }

    @Test fun advancedImportRejectsUnsupportedTransportsInsteadOfSilentlyChangingThem() {
        for (raw in listOf("dot", "doh", "tcp-typo", "")) {
            assertThrows(IllegalArgumentException::class.java) {
                WhiteZiaSettings().importAdvancedSettingsProfileFromToml("Bad carrier", "RESOLVER_TRANSPORT = \"$raw\"")
            }
        }
        val old = WhiteZiaSettings().importAdvancedSettingsProfileFromToml("Old preset", "LISTEN_PORT = 12345")
        assertEquals("auto", old.cottenDnsResolverTransport)
        val explicit = WhiteZiaSettings().importAdvancedSettingsProfileFromToml("TCP", "RESOLVER_TRANSPORT = \" TCP \"")
        assertEquals("tcp", explicit.cottenDnsResolverTransport)
    }

    @Test fun recoveringPerformancePresetDoesNotUndoTheUsersExplicitCarrierOverride() {
        val presetSettings = WhiteZiaSettings().applyAutoTunePreset(WhiteZiaAutoTunePresets.all.first())
        val profile = AdvancedSettingsProfile.fromSettings(presetSettings, "saved-preset", "Saved preset")
        for (transport in CottenDnsResolverTransport.entries) {
            // Settings dialog saves a global override; it does not rewrite the named advanced preset.
            val edited = presetSettings.copy(selectedAdvancedProfileId = profile.id,
                advancedProfiles = listOf(profile), cottenDnsResolverTransport = transport.storedValue)
            val reloaded = edited.recoverPersistedParallelTestPreset()
            assertEquals(transport.storedValue, reloaded.cottenDnsResolverTransport)
            assertEquals(transport.storedValue, reloaded.runtimeConnectionSettings().cottenDnsResolverTransport)
            assertEquals("auto", reloaded.advancedProfiles.single().cottenDnsResolverTransport)
            assertEquals(profile.id, reloaded.selectedAdvancedProfileId)
            assertEquals(profile.minUploadMtu, reloaded.minUploadMtu)
            assertEquals(profile.uploadDuplication, reloaded.uploadDuplication)
        }
    }
}
