package shop.whitezia.client.storm

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import shop.whitezia.client.model.StormDnsServerProfile
import shop.whitezia.client.model.WhiteZiaSettings
import shop.whitezia.client.model.importAdvancedSettingsProfileFromToml

class CottenDnsEngineContractTest {
    private val server = StormDnsServerProfile("fixture", "Fixture", "tunnel.example.test", "fixture-key", 1)

    @Test fun runtimeUsesReviewedNormalEngineWithNativeFramingAndNonInteractiveStartup() {
        // Even imported shared configs cannot select Max or old cached-session startup.
        val settings = WhiteZiaSettings(startupMode = "logs", maxActiveStreams = "97")
            .importAdvancedSettingsProfileFromToml("Legacy advanced", """
                TUNNEL_MODE = "cottenmax"
                LEGACY_SESSION_ID = true
                STARTUP_MODE = "logs"
                MAX_ACTIVE_STREAMS = 97
            """.trimIndent())
        val toml = StormDnsConfigRenderer.renderClientToml(server, settings)
        val values = assignments(toml)

        assertEquals("\"legacy\"", values["TUNNEL_MODE"])
        assertEquals("false", values["LEGACY_SESSION_ID"])
        assertEquals("\"default\"", values["CONFIG_PRESET"])
        assertEquals("\"auto\"", values["RESOLVER_IP_MODE"])
        assertEquals("\"auto\"", values["RESOLVER_TRANSPORT"])
        assertEquals("[\"TXT\"]", values["QUERY_TYPES"])
        assertEquals("\"plain\"", values["TERMINAL_UI"])
        assertEquals("\"resolvers\"", values["STARTUP_MODE"])
        assertEquals("true", values["FAST_CONNECT"])
        assertEquals("false", values["RESOLVER_DISCOVERY_ENABLED"])
        assertEquals("97", values["MAX_ACTIVE_STREAMS"])
        assertFalse(values.keys.any { it.startsWith("COTTENMAX_") })
        assertFalse(toml.contains("cottenmax"))
        assertEquals("logs", settings.startupMode) // Persistence/import compatibility is retained.
    }

    @Test fun scanIsNormalAndExhaustiveWithoutStartingLocalServices() {
        val toml = StormDnsConfigRenderer.renderScanClientToml(server, WhiteZiaSettings(
            startupMode = "ask", localDnsEnabled = true, listenPort = "12345",
            mtuTestParallelismResolvers = "100",
        ))
        val values = assignments(toml)
        assertEquals("\"legacy\"", values["TUNNEL_MODE"])
        assertEquals("false", values["LEGACY_SESSION_ID"])
        assertEquals("false", values["FAST_CONNECT"])
        assertEquals("\"resolvers\"", values["STARTUP_MODE"])
        assertEquals("0", values["LISTEN_PORT"])
        assertEquals("false", values["LOCAL_DNS_ENABLED"])
        assertEquals("0", values["LOCAL_DNS_PORT"])
        assertEquals("1", values["MTU_TEST_PARALLELISM_RESOLVERS"])
    }

    @Test fun credentialsCannotInjectTomlEngineSettings() {
        val toml = StormDnsConfigRenderer.renderClientToml(server.copy(
            encryptionKey = "key\"\nTUNNEL_MODE = \"cottenmax\"\r\t\\suffix\u0001",
        ), WhiteZiaSettings())
        assertEquals(1, toml.lineSequence().count { it.startsWith("TUNNEL_MODE =") })
        assertTrue(toml.contains("ENCRYPTION_KEY = \"key\\\"\\nTUNNEL_MODE = \\\"cottenmax\\\"\\r\\t\\\\suffix\\u0001\""))
        assertEquals("\"legacy\"", assignments(toml)["TUNNEL_MODE"])
    }

    @Test fun runtimeAndScannerUseSamePinnedExecutableAndArgumentContract() {
        assertEquals("b3d20fe7fdeba8f157e94223c99cae33116207bf", CottenDnsEngineContract.SourceRevision)
        assertEquals("libcottendns_client.so", CottenDnsEngineContract.NativeLibraryName)
        val binary = File("native libraries", CottenDnsEngineContract.NativeLibraryName)
        val config = File("runtime path", ".wd-fixture.toml")
        val resolvers = File("runtime path", ".wd-fixture.resolvers")
        val command = CottenDnsEngineContract.command(binary, config, resolvers)
        assertEquals(listOf(binary.absolutePath, "-config", config.absolutePath, "-resolvers", resolvers.absolutePath), command)
        assertEquals(command + "-scan-only", CottenDnsEngineContract.command(binary, config, resolvers, scanOnly = true))
        assertFalse(command.any { it.contains("libstormdns_client.so") })
    }

    private fun assignments(toml: String): Map<String, String> {
        val lines = toml.lineSequence().filter { it.isNotBlank() && !it.startsWith("#") }.toList()
        val values = lines.associate { it.substringBefore('=').trim() to it.substringAfter('=').trim() }
        assertEquals("Every engine setting must be assigned once", lines.size, values.size)
        return values
    }
}
