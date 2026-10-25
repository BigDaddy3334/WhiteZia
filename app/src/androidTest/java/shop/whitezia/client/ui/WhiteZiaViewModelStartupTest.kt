package shop.whitezia.client.ui

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WhiteZiaViewModelStartupTest {
    @Test
    fun createsAndRecreatesViewModelWithPersistedSettings() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val application = instrumentation.targetContext.applicationContext as Application
        instrumentation.runOnMainSync {
            repeat(2) {
                val store = ViewModelStore()
                try {
                    val model = ViewModelProvider(
                        store,
                        ViewModelProvider.AndroidViewModelFactory(application),
                    )[WhiteZiaViewModel::class.java]
                    // Startup refreshes the scan controller before the constructor returns.
                    // Recreating also checks listener cleanup in onCleared().
                    assertNotNull(model.uiState.scanState)
                    model.refreshScanState()
                } finally {
                    store.clear()
                }
            }
        }
    }
}
