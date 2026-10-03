package shop.whitezia.client.account

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.assertEquals
import shop.whitezia.client.ui.WhiteZiaTheme

@RunWith(AndroidJUnit4::class)
class AccountNavigationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun dashboardSectionsInDarkTheme() = sections("dark")
    @Test fun dashboardSectionsInLightTheme() = sections("light")

    private fun sections(theme: String) {
        show(fixture(), theme)
        compose.onNodeWithText("Тарифы").assertIsDisplayed()
        screenshot("account-$theme-subscription")
        compose.onNode(hasText("Устройства") and hasClickAction()).performClick()
        compose.onNodeWithText("Мои устройства").assertIsDisplayed()
        compose.onNodeWithText("Это устройство привязано к аккаунту").assertIsDisplayed()
        screenshot("account-$theme-devices")
        compose.onNodeWithText("Платежи").performClick()
        compose.onNodeWithText("История платежей").assertIsDisplayed()
        screenshot("account-$theme-payments")
    }

    @Test fun enrollmentShowsRealStagesAndRetry() {
        var retries = 0
        val state = mutableStateOf(fixture().copy(enrollment = DeviceEnrollmentProgress(
            stage = DeviceEnrollmentStage.PROVISIONING,
            startedAtMillis = SystemClock.elapsedRealtime() - 12_000L,
            pollAttempts = 4,
            polling = false,
        )))
        compose.setContent { WhiteZiaTheme(themeMode = "light") {
            dashboard(state.value, onAttach = { retries++ })
        } }
        compose.onNodeWithText("Подготовка протоколов").assertIsDisplayed()
        compose.onNodeWithText("Профиль ещё не готов. Автопроверка приостановлена").assertIsDisplayed()
        compose.onNodeWithText("Проверить готовность").performClick()
        assertEquals(1, retries)
        screenshot("account-enrollment-paused")
    }

    @Test fun transientFailureKeepsProvisioningVisible() {
        show(fixture().copy(enrollment = DeviceEnrollmentProgress(
            stage = DeviceEnrollmentStage.PROVISIONING,
            startedAtMillis = SystemClock.elapsedRealtime() - 150_000L,
            pollAttempts = 30,
            polling = true,
            pollError = "Read timed out",
        )), "light")
        compose.onNodeWithText("Подготовка протоколов").assertIsDisplayed()
        compose.onNodeWithText("Связь с сервером временно недоступна. Продолжаем проверку").assertIsDisplayed()
        compose.onNodeWithText("Повторить").assertDoesNotExist()
        compose.onNodeWithText("Read timed out").assertDoesNotExist()
        screenshot("account-enrollment-transient-timeout")
    }

    private fun show(state: AccountUiState, theme: String) {
        compose.setContent { WhiteZiaTheme(themeMode = theme) { dashboard(state) } }
    }

    @androidx.compose.runtime.Composable
    private fun dashboard(state: AccountUiState, onAttach: () -> Unit = {}) {
        WhiteZiaAccountDialog(state, onDismiss = {}, onShowSignIn = {}, onShowRegister = {}, onShowRecovery = {},
            onLogin = { _, _ -> }, onRegister = { _, _, _ -> }, onVerifyEmail = {}, onResendVerification = {},
            onRequestPasswordReset = {}, onResetPassword = { _, _ -> }, onRefresh = {}, onStartPayment = {},
            onPaymentOpened = {}, onAttachCurrentDevice = onAttach, onDisableDevice = {}, onLogout = {})
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        SystemClock.sleep(400L)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: error("Screenshot unavailable")
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "ui-tests").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    private fun fixture() = AccountUiState(
        stage = AccountStage.DASHBOARD, currentDeviceId = "device-1",
        dashboard = AccountDashboard(
            account = AccountProfile("test-account", "test@example.com", "Тестовый аккаунт", false),
            subscription = AccountSubscriptionStatus(AccountSubscription("subscription", "month", "active", "2026-11-01T00:00:00Z", false), 3, 1),
            devices = listOf(AccountDevice("device-1", "WhiteZia · Android", "active", "android", true, "2026-10-01T00:00:00Z")),
            payments = listOf(AccountPayment("payment-1", "month", 15000, "RUB", "paid", "2026-10-01T00:00:00Z")),
            plans = listOf(AccountPlan("month", "Месяц", 30, 15000, 25000, "RUB")),
        ),
    )
}
