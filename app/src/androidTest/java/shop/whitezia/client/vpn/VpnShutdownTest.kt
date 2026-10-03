package shop.whitezia.client.vpn

import android.net.VpnService
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
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import shop.whitezia.client.model.ConnectionProfile
import shop.whitezia.client.model.WhiteZiaSettings
import shop.whitezia.client.runtime.RuntimeLaunchRequestStore
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
        assertEquals("stopped", WhiteZiaRuntimeStateStore.read(context, "vpn")?.status)
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
