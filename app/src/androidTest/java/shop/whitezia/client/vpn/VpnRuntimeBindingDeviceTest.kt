package shop.whitezia.client.vpn

import android.app.ActivityManager
import android.content.Context
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import shop.whitezia.client.runtime.VpnDesiredSessionStore
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class VpnRuntimeBindingDeviceTest {
    @Test
    fun delayedCancellationKeepsNewBindingAndFinalStopReleasesService() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val desired = VpnDesiredSessionStore(context.noBackupFilesDir)
        check(desired.read() == null) { "Test requires an inactive VPN" }
        val first = UUID.randomUUID().toString()
        val second = UUID.randomUUID().toString()
        try {
            // Binding alone never starts a tunnel or changes saved account/settings.
            desired.request(first)
            VpnRuntimeBinding.hold(context, first)
            assertTrue(awaitService(context, present = true))
            desired.clear(first)
            desired.request(second)
            VpnRuntimeBinding.hold(context, second)
            VpnRuntimeBinding.notifyCancellation(context)
            SystemClock.sleep(500)
            assertTrue(isServicePresent(context))
            desired.clear(second)
            VpnRuntimeBinding.notifyCancellation(context)
            assertTrue(awaitService(context, present = false))
            assertFalse(isServicePresent(context))
        } finally {
            desired.clear(second)
            desired.clear(first)
            VpnRuntimeBinding.notifyCancellation(context)
        }
    }

    @Suppress("DEPRECATION")
    private fun isServicePresent(context: Context): Boolean =
        context.getSystemService(ActivityManager::class.java).getRunningServices(100)
            .any { it.service.className == WhiteZiaVpnService::class.java.name }

    private fun awaitService(context: Context, present: Boolean): Boolean {
        val deadline = SystemClock.elapsedRealtime() + 5_000
        while (SystemClock.elapsedRealtime() < deadline) {
            if (isServicePresent(context) == present) return true
            SystemClock.sleep(50)
        }
        return false
    }
}
