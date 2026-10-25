package shop.whitezia.client.vpn

import android.net.VpnService
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.app.ActivityManager
import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import java.util.UUID
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import shop.whitezia.client.model.ConnectionProfile
import shop.whitezia.client.model.WhiteZiaSettings
import shop.whitezia.client.runtime.RuntimeLaunchRequestStore
import shop.whitezia.client.runtime.VpnDesiredSessionStore
import shop.whitezia.client.runtime.WhiteZiaRuntimeStateStore

@RunWith(AndroidJUnit4::class)
class VpnShutdownTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val sessions = mutableListOf<String>()
    private var testStartedAtMillis = 0L
    private val runtimeLogs = CopyOnWriteArrayList<Pair<String, String>>()
    private var receiverRegistered = false
    private var endpoint: ServerSocket? = null
    private var endpointThread: Thread? = null
    private val endpointConnections = CopyOnWriteArrayList<Socket>()
    private val testXrayUri get() = "vless://11111111-1111-4111-8111-111111111111@127.0.0.1:${requireNotNull(endpoint).localPort}?encryption=none&security=none&type=tcp"
    private val runtimeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.getStringExtra(WhiteZiaVpnService.BroadcastExtraType) == WhiteZiaVpnService.BroadcastTypeLog) {
                runtimeLogs += intent.getStringExtra(WhiteZiaVpnService.BroadcastExtraSessionId).orEmpty() to
                    intent.getStringExtra(WhiteZiaVpnService.BroadcastExtraMessage).orEmpty()
            }
        }
    }

    @Before fun trackNativeProcessExits() {
        assumeTrue(android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R)
        testStartedAtMillis = System.currentTimeMillis()
        ContextCompat.registerReceiver(context, runtimeReceiver, IntentFilter(WhiteZiaVpnService.BroadcastAction), ContextCompat.RECEIVER_NOT_EXPORTED)
        receiverRegistered = true
        // A local TCP sink exercises native startup/teardown without external CDN availability.
        endpoint = ServerSocket(0)
        endpointThread = thread(name = "vpn-test-endpoint", isDaemon = true) {
            try {
                while (!requireNotNull(endpoint).isClosed) {
                    endpointConnections += requireNotNull(endpoint).accept()
                }
            } catch (_: java.io.IOException) {
                // Closing the fixture unblocks accept.
            }
        }
    }

    @Test fun cancellingXrayStartupRepeatedlyDoesNotCrashNativeProcess() {
        compose.setContent { Text("Xray restart test") }
        grantVpnPermission()
        repeat(3) {
            startAndCancel(settings().copy(manualMode = true, transportMode = "xray", xrayUri = testXrayUri), 2_000L)
        }
    }

    @Test fun stoppingXrayImmediatelyAfterRoutingStartsDoesNotLeaveNativeRunner() {
        compose.setContent { Text("Xray immediate cancellation test") }
        grantVpnPermission()
        repeat(3) {
            startAndCancel(settings().copy(manualMode = true, transportMode = "xray", xrayUri = testXrayUri), 0L)
        }
    }

    @Test fun automaticModeSkipsLegacyAwgAndClosesXray() {
        compose.setContent { Text("VPN shutdown test") }
        grantVpnPermission()
        repeat(3) {
            startAndCancel(settings().copy(amneziaWgConfig = UnreachableAwgConfig,
                xrayUri = testXrayUri), 700L)
        }
    }

    @Test fun switchingManualXrayAndAutoUsesTheSameSerializedStopPath() {
        compose.setContent { Text("VPN transition test") }
        grantVpnPermission()
        repeat(2) {
            startAndCancel(settings().copy(manualMode = true, transportMode = "xray", xrayUri = testXrayUri), 2_000L)
            startAndCancel(settings().copy(amneziaWgConfig = UnreachableAwgConfig,
                xrayUri = testXrayUri), 700L)
        }
    }

    @Test fun reconfiguringTransportReusesVpnNetworkAndFinalStopReleasesIt() {
        compose.setContent { Text("VPN retained interface test") }
        grantVpnPermission()
        val id = UUID.randomUUID().toString().also(sessions::add)
        val configuration = settings().copy(manualMode = true, transportMode = "xray", xrayUri = testXrayUri)
        WhiteZiaVpnService.start(context, id, settings = configuration)
        assertTrue("Native tunnel never started", waitUntil {
            runtimeLogs.any { it.first == id && it.second == "Xray VPN routing started" }
        })
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        fun vpnNetworks() = connectivity.allNetworks.filter {
            connectivity.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        }.toSet()
        assertTrue("VPN network was not registered", waitUntil { vpnNetworks().isNotEmpty() })
        val initial = vpnNetworks()
        // Allocate enough identities to catch a reset even if the OS performs a
        // few DNS queries when a VPN network is first announced.
        val suffix = UUID.randomUUID().toString() + ".example.com"
        val cachedName = "cached.$suffix"
        val cachedAddress = virtualDns(cachedName, seed = true)
        WhiteZiaVpnService.start(context, id, settings = configuration.copy(manualMode = false, transportMode = "auto"), reconfigureOnly = true)
        assertTrue("Replacement native reader never started", waitUntil {
            runtimeLogs.count { it.first == id && it.second == "Xray VPN routing started" } >= 2
        })
        assertTrue("Transport restart did not retain the interface", runtimeLogs.any {
            it.first == id && it.second == "Reusing Android VPN interface"
        })
        assertEquals("Transport restart replaced the Android VPN network", initial, vpnNetworks())
        assertEquals("Transport restart discarded the virtual DNS mapping", cachedAddress,
            virtualDns(cachedName))
        assertEquals("Transport restart created a new reader and discarded its DNS map", 1,
            runtimeLogs.count { it.first == id && it.second == "tun2proxy: tun2proxy native runner started" })
        WhiteZiaVpnService.stop(context)
        assertTrue("Stop did not release the retained VPN network", waitUntil {
            WhiteZiaRuntimeStateStore.read(context, "vpn")?.let { it.sessionId == id && it.status == "stopped" } == true &&
                vpnNetworks().intersect(initial).isEmpty()
        })
    }

    @Test fun changingSplitTunnelSettingsReplacesVpnDnsSession() {
        compose.setContent { Text("VPN DNS session replacement test") }
        grantVpnPermission()
        val id = UUID.randomUUID().toString().also(sessions::add)
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        fun vpnNetworks() = connectivity.allNetworks.filter {
            connectivity.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        }.toSet()
        var configuration = settings().copy(manualMode = true, transportMode = "xray", xrayUri = testXrayUri)
        WhiteZiaVpnService.start(context, id, settings = configuration)
        assertTrue(waitUntil { runtimeLogs.count { it.first == id && it.second == "Xray VPN routing started" } >= 1 })
        assertTrue(waitUntil { vpnNetworks().isNotEmpty() })
        var previous = vpnNetworks()
        listOf(
            configuration.copy(splitTunnelMode = "exclude", splitTunnelPackages = listOf("com.android.settings")),
            configuration.copy(splitTunnelMode = "off", splitTunnelPackages = emptyList()),
        ).forEachIndexed { index, next ->
            configuration = next
            WhiteZiaVpnService.start(context, id, settings = configuration, reconfigureOnly = true)
            assertTrue("Replacement reader did not start", waitUntil {
                runtimeLogs.count { it.first == id && it.second == "Xray VPN routing started" } >= index + 2
            })
            assertTrue("A fresh reader retained the old DNS network", waitUntil {
                vpnNetworks().isNotEmpty() && vpnNetworks().intersect(previous).isEmpty()
            })
            previous = vpnNetworks()
        }
        WhiteZiaVpnService.stop(context)
        assertTrue(waitUntil { vpnNetworks().isEmpty() })
    }

    private fun virtualDns(host: String, seed: Boolean = false): String {
        // The app UID is excluded from its own VPN to avoid a proxy loop.
        // Probe as shell, which is included in the VPN like other apps.
        require(host.matches(Regex("[a-zA-Z0-9.-]+")))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val apk = instrumentation.context.applicationInfo.sourceDir
        val command = "/system/bin/env CLASSPATH=" + apk +
            " /system/bin/app_process /system/bin shop.whitezia.client.vpn.VirtualDnsShellProbe " + host +
            if (seed) " seed" else ""
        val output = android.os.ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand(command),
        ).bufferedReader().use { it.readText().trim() }
        assertTrue("Virtual DNS probe failed: " + output,
            output.matches(Regex("198\\.1[89]\\.[0-9]+\\.[0-9]+")))
        return output
    }

    @Test fun immediateStopThenStartKeepsNewServiceInForeground() {
        compose.setContent { Text("VPN restart test") }
        grantVpnPermission()
        repeat(2) {
            val initial = UUID.randomUUID().toString().also(sessions::add)
            val next = UUID.randomUUID().toString().also(sessions::add)
            val configuration = settings().copy(amneziaWgConfig = UnreachableAwgConfig,
                xrayUri = testXrayUri)
            WhiteZiaVpnService.start(context, initial, settings = configuration)
            assertTrue(waitUntil { WhiteZiaRuntimeStateStore.read(context, "vpn")?.sessionId == initial })
            SystemClock.sleep(700L)
            WhiteZiaVpnService.stop(context)
            WhiteZiaVpnService.start(context, next, settings = configuration)
            assertTrue("New START was abandoned", waitUntil {
                WhiteZiaRuntimeStateStore.read(context, "vpn")?.let { it.sessionId == next && it.status == "starting" } == true
            })
            SystemClock.sleep(1_000L)
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            @Suppress("DEPRECATION")
            assertTrue("Old STOP removed the new foreground service", manager.getRunningServices(Int.MAX_VALUE)
                .any { it.service.className == WhiteZiaVpnService::class.java.name && it.foreground })
            WhiteZiaVpnService.stop(context)
            assertTrue(waitUntil {
                WhiteZiaRuntimeStateStore.read(context, "vpn")?.let { it.sessionId == next && it.status == "stopped" } == true
            })
        }
    }

    @Test fun boundServiceResumesReadySnapshotAndLateRestoreCannotUndoStop() {
        compose.setContent { Text("VPN bound process restoration test") }
        grantVpnPermission()
        assertTrue("Previous VPN service still exists", waitUntil { !vpnServicePresent() })
        val id = UUID.randomUUID().toString().also(sessions::add)
        val configuration = settings().copy(manualMode = true, transportMode = "xray", xrayUri = testXrayUri)
        val desired = VpnDesiredSessionStore(context.noBackupFilesDir)
        // These are the files left by a formerly Ready process. Binding alone
        // must recreate its native runtime even if Android sends no sticky START.
        RuntimeLaunchRequestStore.save(context, id, null, configuration)
        desired.request(id)
        desired.markReady(id)
        WhiteZiaRuntimeStateStore.markReady(context, configuration, id)
        VpnRuntimeBinding.hold(context, id)
        assertTrue("Binding recreated an idle service without its tunnel", waitUntil {
            runtimeLogs.any { it.first == id && it.second == "Xray VPN routing started" }
        })
        assertEquals("Recreated process left the old Ready status", "starting",
            WhiteZiaRuntimeStateStore.read(context, "vpn")?.status)

        // Real systems may also deliver their sticky/always-on command after the
        // binding fallback. It must not restart a still-starting native reader.
        context.startService(Intent(context, WhiteZiaVpnService::class.java).setAction(VpnService.SERVICE_INTERFACE))
        SystemClock.sleep(700L)
        assertEquals("Duplicate system restoration restarted Xray", 1,
            runtimeLogs.count { it.first == id && it.second == "Xray process started" })
        WhiteZiaVpnService.stop(context)
        assertNull("Stop must clear intent before dispatch", desired.read())
        assertTrue(waitUntil { !vpnServicePresent() })
        context.startService(Intent(context, WhiteZiaVpnService::class.java)
            .setAction(WhiteZiaVpnService.ActionRestore)
            .putExtra(WhiteZiaVpnService.BroadcastExtraSessionId, id))
        SystemClock.sleep(700L)
        assertTrue(waitUntil { !vpnServicePresent() })
        assertNull("Late restore reauthorized a stopped VPN", desired.read())
        assertEquals("stopped", WhiteZiaRuntimeStateStore.read(context, "vpn")?.status)
    }

    @Test fun restoreForSupersededSessionCannotRestartCurrentReader() {
        compose.setContent { Text("VPN stale restoration test") }
        grantVpnPermission()
        val old = UUID.randomUUID().toString().also(sessions::add)
        val current = UUID.randomUUID().toString().also(sessions::add)
        val configuration = settings().copy(manualMode = true, transportMode = "xray", xrayUri = testXrayUri)
        WhiteZiaVpnService.start(context, current, settings = configuration)
        assertTrue(waitUntil {
            runtimeLogs.any { it.first == current && it.second == "Xray VPN routing started" }
        })
        context.startService(Intent(context, WhiteZiaVpnService::class.java)
            .setAction(WhiteZiaVpnService.ActionRestore)
            .putExtra(WhiteZiaVpnService.BroadcastExtraSessionId, old))
        SystemClock.sleep(700L)
        assertEquals(current, VpnDesiredSessionStore(context.noBackupFilesDir).read()?.sessionId)
        assertEquals(current, WhiteZiaRuntimeStateStore.read(context, "vpn")?.sessionId)
        assertEquals(1, runtimeLogs.count { it.first == current && it.second == "Xray process started" })
        assertTrue(runtimeLogs.none { it.first == old && it.second == "Xray process started" })
    }

    @Suppress("DEPRECATION")
    private fun vpnServicePresent(): Boolean = context.getSystemService(ActivityManager::class.java)
        .getRunningServices(100).any { it.service.className == WhiteZiaVpnService::class.java.name }

    private fun startAndCancel(settings: WhiteZiaSettings, startupMillis: Long) {
        val id = UUID.randomUUID().toString().also(sessions::add)
        WhiteZiaVpnService.start(context, id, settings = settings)
        assertTrue("Service did not acknowledge session", waitUntil {
            WhiteZiaRuntimeStateStore.read(context, "vpn")?.let { it.sessionId == id && it.status == "starting" } == true
        })
        val startedMessage = if (settings.xrayUri.isNotBlank()) {
            "Xray VPN routing started"
        } else {
            "AmneziaWG backend started"
        }
        assertTrue("Native tunnel never started for $id", waitUntil {
            runtimeLogs.any { it.first == id && it.second == startedMessage }
        })
        assertTrue("Legacy AWG must not start in the new connection chain", runtimeLogs.none {
            it.first == id && it.second == "AmneziaWG backend started"
        })
        SystemClock.sleep(startupMillis)
        WhiteZiaVpnService.stop(context)
        assertTrue("Native runtime did not stop after cancellation", waitUntil {
            WhiteZiaRuntimeStateStore.read(context, "vpn")?.let { it.sessionId == id && it.status == "stopped" } == true
        })
        // The assertion above observes the STOP acknowledgement for this session.
        // onDestroy may briefly write Stopping during its idempotent final cleanup;
        // a second unsynchronized read here can race that cleanup.
    }

    private fun grantVpnPermission() {
        val intent = VpnService.prepare(context) ?: return
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .first().startActivityForResult(intent, 704)
        }
        assertTrue("VPN consent dialog was not confirmed", waitUntil {
            val root = instrumentation.uiAutomation.rootInActiveWindow
            root?.findAccessibilityNodeInfosByViewId("android:id/button1")?.firstOrNull()
                ?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            VpnService.prepare(context) == null
        })
    }

    private fun waitUntil(predicate: () -> Boolean): Boolean {
        val deadline = SystemClock.elapsedRealtime() + 25_000L
        while (SystemClock.elapsedRealtime() < deadline) {
            if (predicate()) return true
            SystemClock.sleep(100L)
        }
        return false
    }

    private fun settings() = WhiteZiaSettings(
        connectionMode = "vpn",
        connectionProfiles = listOf(ConnectionProfile(id = ConnectionProfile.DefaultId, name = "Test", connectionMode = "vpn")),
    )

    @After fun cleanup() {
        if (testStartedAtMillis == 0L) return
        WhiteZiaVpnService.stop(context)
        waitUntil { WhiteZiaRuntimeStateStore.read(context, "vpn")?.status !in setOf("starting", "stopping", "ready") }
        sessions.forEach { RuntimeLaunchRequestStore.delete(context, it) }
        endpoint?.close()
        endpointThread?.join(1_000L)
        endpointConnections.forEach { runCatching { it.close() } }
        // Catch delayed native termination after the last STOP, not only between attempts.
        SystemClock.sleep(3_000L)
        if (receiverRegistered) {
            context.unregisterReceiver(runtimeReceiver)
            receiverRegistered = false
        }
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val unexpectedExits = manager.getHistoricalProcessExitReasons(context.packageName, 0, 32)
            .filter { it.timestamp >= testStartedAtMillis && it.processName.endsWith(":vpn") }
            .filter { it.reason == android.app.ApplicationExitInfo.REASON_CRASH_NATIVE ||
                it.reason == android.app.ApplicationExitInfo.REASON_CRASH ||
                (it.reason == android.app.ApplicationExitInfo.REASON_EXIT_SELF && it.status != 0) }
        assertTrue("VPN process crashed during test: ${unexpectedExits.map { "${it.reason}/${it.status}: ${it.description}" }}", unexpectedExits.isEmpty())
    }

    private companion object {
        val UnreachableAwgConfig = """
            [Interface]
            PrivateKey = AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAE=
            Address = 10.8.0.2/32
            DNS = 1.1.1.1
            [Peer]
            PublicKey = AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAI=
            Endpoint = 192.0.2.1:51820
            AllowedIPs = 0.0.0.0/0
            PersistentKeepalive = 25
        """.trimIndent()
    }
}
