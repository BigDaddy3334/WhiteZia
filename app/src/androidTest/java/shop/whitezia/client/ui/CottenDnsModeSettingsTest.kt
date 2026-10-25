package shop.whitezia.client.ui

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import shop.whitezia.client.model.CottenDnsMode
import shop.whitezia.client.model.WhiteZiaSettingsStore
import shop.whitezia.client.ui.settings.WhiteZiaSettingsDialog

@RunWith(AndroidJUnit4::class)
class CottenDnsModeSettingsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun explicitModeSavesAndSurvivesSettingsUiRecreationWithoutChangingProfiles() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val fixtureName = "cotten_mode_ui_fixture_" + UUID.randomUUID()
        val migrationDir = File(target.cacheDir, fixtureName)
        val context = object : ContextWrapper(target) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
                check(name == "whitezia_settings")
                return target.getSharedPreferences(fixtureName, mode)
            }
            override fun getApplicationInfo(): ApplicationInfo = ApplicationInfo(target.applicationInfo).apply {
                dataDir = migrationDir.absolutePath
            }
        }
        check(!migrationDir.exists())
        val preferences = context.getSharedPreferences("whitezia_settings", Context.MODE_PRIVATE)
        check(preferences.all.isEmpty())
        var originalFailure: Throwable? = null
        try {
            val initial = WhiteZiaSettingsStore(context).load()
            assertEquals(CottenDnsMode.Normal, initial.cottenDnsMode)
            val restoration = StateRestorationTester(compose)
            restoration.setContent {
                // Recreated UI reloads the same real SettingsStore through a fixture-only namespace.
                var settings by remember { mutableStateOf(WhiteZiaSettingsStore(context).load()) }
                MaterialTheme {
                    WhiteZiaSettingsDialog(
                        settings = settings,
                        subscriptionLink = "",
                        accountManaged = false,
                        onDismiss = {},
                        onOpenSplitTunnelApps = { _, _ -> },
                        onScanSubscription = {},
                        isCheckingForUpdates = false,
                        onCheckForUpdates = {},
                        onSave = { saved, _ ->
                            WhiteZiaSettingsStore(context).save(saved)
                            settings = WhiteZiaSettingsStore(context).load()
                        },
                    )
                }
            }
            compose.onNodeWithText("CottenDNS").performClick()
            compose.onNodeWithText("Normal").assertIsSelected()
            compose.onNodeWithText("CottenMax (beta)").performClick()
            // Draft selection is explicit; only the actual Save action persists it.
            assertEquals(CottenDnsMode.Normal, WhiteZiaSettingsStore(context).load().cottenDnsMode)
            compose.onNodeWithText("Сохранить").performClick()
            compose.runOnIdle { assertEquals(CottenDnsMode.Max, WhiteZiaSettingsStore(context).load().cottenDnsMode) }
            restoration.emulateSavedInstanceStateRestore()
            compose.onNodeWithText("CottenMax (beta)").assertIsSelected()
            compose.runOnIdle {
                val recreated = WhiteZiaSettingsStore(context).load()
                assertEquals(initial.connectionProfiles, recreated.connectionProfiles)
                assertEquals(initial.xrayUri, recreated.xrayUri)
            }
            compose.onNodeWithText("Normal").performClick()
            compose.onNodeWithText("Сохранить").performClick()
            restoration.emulateSavedInstanceStateRestore()
            compose.onNodeWithText("Normal").assertIsSelected()
            compose.runOnIdle { assertEquals(CottenDnsMode.Normal, WhiteZiaSettingsStore(context).load().cottenDnsMode) }
        } catch (failure: Throwable) {
            originalFailure = failure
            throw failure
        } finally {
            val cleanup = runCatching {
                check(target.deleteSharedPreferences(fixtureName))
                check(!migrationDir.exists())
            }.exceptionOrNull()
            if (cleanup != null) {
                val failure = originalFailure
                if (failure != null) failure.addSuppressed(cleanup) else throw cleanup
            }
        }
    }
}
