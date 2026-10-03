package shop.whitezia.client.vpn

import android.content.Context
import android.net.ConnectivityManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import shop.whitezia.client.BuildConfig
import shop.whitezia.client.model.WhiteZiaSettings
import shop.whitezia.client.model.XrayCandidate

@RunWith(AndroidJUnit4::class)
class XrayRoutePreflightDeviceTest {
    @Test fun reachableDirectWinsEvenWithFasterCdnAndDeadDirectIsFiltered() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = manager.physicalInternetNetwork()
        assumeTrue(network != null)
        assumeTrue(BuildConfig.BOOTSTRAP_XRAY_URI.startsWith("vless://"))
        val valid = BuildConfig.BOOTSTRAP_XRAY_URI
        val unreachable = valid.replace(Regex("@[^:]+:"), "@192.0.2.1:")
        val settings = WhiteZiaSettings(manualMode = true, transportMode = "xray",
            xrayUri = valid, xrayCandidates = listOf(
                XrayCandidate("healthy", "primary", valid, 5, valid),
                XrayCandidate("dead", "standby", unreachable, 5, unreachable),
            ))
        val probe = XrayEndpointProbe(requireNotNull(network))
        val seen = mutableSetOf<String>()
        val started = System.nanoTime()
        val prepared = XrayRoutePreflight(probe::probe, report = { candidate, _ -> seen += candidate.nodeId })
            .prepare(VpnCandidatePlanner.plan(settings))
        assertEquals(setOf("healthy", "dead"), seen)
        assertTrue("Bootstrap TCP/TLS unreachable on device", prepared.isNotEmpty())
        assertTrue(prepared.none { it.nodeId == "dead" })
        assertEquals(XrayRouteKind.Direct, prepared.first().routeKind)
        assertTrue("Preflight exceeded global route deadline", (System.nanoTime()-started)/1_000_000 < 8_000)
    }
}
