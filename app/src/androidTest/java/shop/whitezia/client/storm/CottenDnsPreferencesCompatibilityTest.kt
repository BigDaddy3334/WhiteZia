package shop.whitezia.client.storm

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import shop.whitezia.client.model.WhiteZiaSettingsStore
import shop.whitezia.client.model.CottenDnsMode
import shop.whitezia.client.model.CottenDnsResolverTransport
import shop.whitezia.client.model.saveCurrentAdvancedProfileAs
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class CottenDnsPreferencesCompatibilityTest {
    @Test fun existingPreferenceNamesAndSavedDnsCredentialsSurviveEngineSwap() {
        // Instrumentation runs as the target UID. Isolate the preference namespace within that UID.
        // Do not open or clear the user's real whitezia_settings file, even via applicationContext.
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val fixtureName = "cotten_preferences_fixture_" + UUID.randomUUID().toString()
        val migrationDataDir = File(targetContext.cacheDir, fixtureName)
        check(!migrationDataDir.exists()) { "Requires a fresh fixture migration path" }
        val context = object : ContextWrapper(targetContext) {
            override fun getApplicationContext(): Context = this

            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
                check(name == "whitezia_settings") { "Unexpected fixture preference namespace" }
                return targetContext.getSharedPreferences(fixtureName, mode)
            }

            override fun getApplicationInfo(): ApplicationInfo = ApplicationInfo(targetContext.applicationInfo).apply {
                // SettingsStore's legacy file migration must never inspect/copy real preference files.
                dataDir = migrationDataDir.absolutePath
            }
        }
        assertSame(context, context.applicationContext)
        val preferences = context.getSharedPreferences("whitezia_settings", Context.MODE_PRIVATE)
        check(preferences.all.isEmpty()) { "Requires an empty test preference fixture" }
        val candidates = JSONArray().put(JSONObject().put("nodeId", "saved-dns-node").put("role", "primary")
            .put("domain", "saved.example.test").put("encryptionKey", "saved-fixture-key").put("encryptionMethod", 2))
        val uri = "vless://11111111-1111-4111-8111-111111111111@cdn.example.test:443?type=xhttp&security=tls"
        var testFailure: Throwable? = null
        try {
            assertTrue("Fixture preference seed must be writable", preferences.edit()
                .putInt("advanced_defaults_revision", 10)
                .putString("custom_server_domain", "saved.example.test")
                .putString("custom_server_encryption_key", "saved-fixture-key")
                .putInt("custom_server_encryption_method", 2)
                .putString("stormdns_candidates", candidates.toString())
                .putString("active_stormdns_node_id", "saved-dns-node")
                .putString("startup_mode", "logs")
                .putString("xray_uri", uri)
                .putString("split_tunnel_mode", "exclude")
                .putString("split_tunnel_packages", "[\"org.telegram.messenger\"]")
                .commit())
            val store = WhiteZiaSettingsStore(context)
            val loaded = store.load()
            assertEquals(CottenDnsMode.Normal, loaded.cottenDnsMode)
            assertEquals("auto", loaded.cottenDnsResolverTransport)
            assertEquals("saved.example.test", loaded.customServerDomain)
            assertEquals("saved-fixture-key", loaded.customServerEncryptionKey)
            assertEquals(2, loaded.customServerEncryptionMethod)
            assertEquals("saved-dns-node", loaded.stormDnsCandidates.single().nodeId)
            assertEquals("saved-dns-node", loaded.activeStormDnsNodeId)
            assertEquals("logs", loaded.startupMode)
            assertEquals(uri, loaded.xrayUri)
            assertEquals(listOf("org.telegram.messenger"), loaded.splitTunnelPackages)
            store.save(loaded)
            assertEquals(loaded, store.load())
            assertTrue(preferences.contains("stormdns_candidates"))
            assertTrue(preferences.contains("active_stormdns_node_id"))
            assertEquals("saved-fixture-key", preferences.getString("custom_server_encryption_key", null))
            store.save(loaded.copy(cottenDnsMode = CottenDnsMode.Max))
            val recreatedMax = WhiteZiaSettingsStore(context).load()
            assertEquals(CottenDnsMode.Max, recreatedMax.cottenDnsMode)
            assertEquals(loaded.connectionProfiles, recreatedMax.connectionProfiles)
            assertEquals(loaded.stormDnsCandidates, recreatedMax.stormDnsCandidates)
            assertEquals(loaded.xrayUri, recreatedMax.xrayUri)
            assertEquals("saved-fixture-key", recreatedMax.customServerEncryptionKey)
            store.save(recreatedMax.copy(cottenDnsMode = CottenDnsMode.Normal))
            assertEquals(loaded, WhiteZiaSettingsStore(context).load())
            for (transport in CottenDnsResolverTransport.entries) {
                val withTransport = loaded.copy(cottenDnsResolverTransport = transport.storedValue)
                    .saveCurrentAdvancedProfileAs("Transport fixture")
                store.save(withTransport)
                val recreated = WhiteZiaSettingsStore(context).load()
                assertEquals(transport.storedValue, recreated.cottenDnsResolverTransport)
                assertEquals(transport.storedValue, recreated.advancedProfiles.single().cottenDnsResolverTransport)
                assertEquals(loaded.connectionProfiles, recreated.connectionProfiles)
                assertEquals(loaded.xrayUri, recreated.xrayUri)
            }
            store.save(loaded)
            val toml = StormDnsConfigRenderer.renderClientToml(loaded.connectionProfiles.first(), loaded)
            assertTrue(toml.contains("LEGACY_SESSION_ID = false"))
            assertTrue(toml.contains("STARTUP_MODE = \"resolvers\""))
        } catch (failure: Throwable) {
            testFailure = failure
            throw failure
        } finally {
            val cleanupFailure = runCatching {
                check(targetContext.deleteSharedPreferences(fixtureName)) { "Could not delete fixture preferences" }
                check(!migrationDataDir.exists()) { "Fixture unexpectedly created legacy migration files" }
            }.exceptionOrNull()
            if (cleanupFailure != null) {
                val originalFailure = testFailure
                if (originalFailure != null) originalFailure.addSuppressed(cleanupFailure) else throw cleanupFailure
            }
        }
    }
}
