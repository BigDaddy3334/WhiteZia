package shop.whitezia.client.account

import android.app.Application
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WhiteZiaAccountViewModel(application: Application) : AndroidViewModel(application) {
    var state: AccountUiState by mutableStateOf(AccountUiState())
        private set

    private val repository = AccountRepository(application)
    private var actionJob: Job? = null
    private var profilePollingJob: Job? = null
    private var lastResumeRefreshAt = 0L
    private var operationGeneration = 0L

    init {
        state = state.copy(managedProfileInstalled = repository.hasManagedProfile())
        restoreSession()
    }

    fun showSignIn() {
        state = state.copy(stage = AccountStage.SIGN_IN, feedback = "", feedbackIsError = false)
    }

    fun showRegister() {
        state = state.copy(stage = AccountStage.REGISTER, feedback = "", feedbackIsError = false)
    }

    fun showRecovery() {
        state = state.copy(stage = AccountStage.RECOVERY, feedback = "", feedbackIsError = false)
    }

    fun login(email: String, password: String) = launchAction {
        val session = withContext(Dispatchers.IO) { repository.login(email, password) }
        loadSignedIn(session.account)
    }

    fun register(email: String, password: String, displayName: String) = launchAction {
        withContext(Dispatchers.IO) { repository.register(email, password, displayName) }
        state = state.copy(
            stage = AccountStage.VERIFY_EMAIL,
            email = email.trim(),
            feedback = "Код отправлен на почту",
            feedbackIsError = false,
        )
    }

    fun verifyEmail(code: String) = launchAction {
        val session = withContext(Dispatchers.IO) { repository.verifyEmail(state.email, code) }
        loadSignedIn(session.account)
    }

    fun resendVerification() = launchAction {
        withContext(Dispatchers.IO) { repository.resendVerification(state.email) }
        state = state.copy(feedback = "Новый код отправлен", feedbackIsError = false)
    }

    fun requestPasswordReset(email: String) = launchAction {
        withContext(Dispatchers.IO) { repository.requestPasswordReset(email) }
        state = state.copy(
            stage = AccountStage.RESET_PASSWORD,
            email = email.trim(),
            feedback = "Если аккаунт найден, код отправлен",
            feedbackIsError = false,
        )
    }

    fun resetPassword(code: String, password: String) = launchAction {
        withContext(Dispatchers.IO) { repository.resetPassword(state.email, code, password) }
        state = AccountUiState(
            stage = AccountStage.SIGN_IN,
            managedProfileInstalled = state.managedProfileInstalled,
            email = state.email,
            feedback = "Пароль изменён. Теперь можно войти",
        )
    }

    fun refreshDashboard() = launchAction {
        val loaded = loadDashboard()
        state = state.copy(
            stage = AccountStage.DASHBOARD,
            dashboard = loaded.dashboard,
            currentDeviceId = loaded.currentDeviceId,
        )
        if (loaded.dashboard.shouldSyncCurrentDevice(loaded.currentDeviceId)) {
            syncCurrentDevice()
        }
    }

    fun refreshAfterResume() {
        if (state.stage == AccountStage.SIGN_IN && !state.busy && repository.canRestoreSession()) {
            restoreSession()
            return
        }
        if (state.stage != AccountStage.DASHBOARD || state.busy) return
        val now = System.currentTimeMillis()
        if (now - lastResumeRefreshAt < ResumeRefreshIntervalMillis) return
        lastResumeRefreshAt = now
        refreshDashboard()
    }

    fun startPayment(planId: String) = launchAction {
        val plan = state.dashboard?.plans?.firstOrNull { it.id == planId }
        if (plan?.isTrial == true) {
            withContext(Dispatchers.IO) { repository.redeemTrial() }
            val loaded = loadDashboard()
            state = state.copy(
                dashboard = loaded.dashboard,
                currentDeviceId = loaded.currentDeviceId,
                feedback = "Пробный период активирован",
            )
            if (loaded.dashboard.shouldSyncCurrentDevice(loaded.currentDeviceId)) {
                syncCurrentDevice()
            }
            return@launchAction
        }
        val paymentUrl = withContext(Dispatchers.IO) { repository.createOrder(planId) }
        state = state.copy(paymentUrl = paymentUrl, feedback = "Открываем страницу оплаты")
    }

    fun paymentOpened() {
        state = state.copy(paymentUrl = null, feedback = "После оплаты вернитесь в приложение")
    }

    fun disableDevice(deviceId: String) = launchAction {
        withContext(Dispatchers.IO) { repository.disableDevice(deviceId) }
        val dashboard = withContext(Dispatchers.IO) { repository.dashboard() }
        state = state.copy(
            dashboard = dashboard,
            currentDeviceId = state.currentDeviceId.takeUnless { it == deviceId }.orEmpty(),
            feedback = "Устройство отключено",
        )
    }

    fun logout() {
        operationGeneration += 1
        actionJob?.cancel()
        actionJob = null
        profilePollingJob?.cancel()
        profilePollingJob = null
        val refreshToken = repository.clearLocalSession()
        repository.clearManagedProfile()
        state = AccountUiState(stage = AccountStage.SIGN_IN)
        if (!refreshToken.isNullOrBlank()) {
            viewModelScope.launch(Dispatchers.IO) {
                repository.revokeSession(refreshToken)
            }
        }
    }

    fun attachCurrentDevice() = launchAction {
        syncCurrentDevice(reportErrors = true)
    }

    fun profileBundleApplied(bundle: String) {
        repository.markManagedProfileInstalled(bundle)
        state = state.copy(
            managedProfileInstalled = true,
            pendingProfileBundle = null,
            feedback = "Профиль устройства применён",
        )
    }

    fun profileBundleRejected(message: String) {
        state = state.copy(
            pendingProfileBundle = null,
            feedback = message,
            feedbackIsError = true,
        )
    }

    fun retrySessionRestore() {
        if (state.stage == AccountStage.SIGN_IN && !state.busy && repository.canRestoreSession()) {
            restoreSession()
        }
    }

    private fun restoreSession() {
        if (state.busy) return
        val generation = operationGeneration
        state = state.copy(stage = AccountStage.RESTORING, busy = true, feedback = "")
        actionJob = viewModelScope.launch {
            try {
                val account = withContext(Dispatchers.IO) { repository.restore() }
                if (generation != operationGeneration) return@launch
                if (account == null) {
                    state = AccountUiState(
                        stage = AccountStage.SIGN_IN,
                        managedProfileInstalled = state.managedProfileInstalled,
                    )
                } else {
                    loadSignedIn(account)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (generation != operationGeneration) return@launch
                state = AccountUiState(
                    stage = AccountStage.SIGN_IN,
                    managedProfileInstalled = state.managedProfileInstalled,
                    feedback = readableError(error),
                    feedbackIsError = true,
                )
            } finally {
                if (generation == operationGeneration) {
                    state = state.copy(busy = false)
                }
            }
        }
    }

    private suspend fun loadSignedIn(account: AccountProfile) {
        val loaded = loadDashboard(account)
        state = AccountUiState(
            stage = AccountStage.DASHBOARD,
            managedProfileInstalled = state.managedProfileInstalled,
            dashboard = loaded.dashboard,
            currentDeviceId = loaded.currentDeviceId,
        )
        if (loaded.dashboard.shouldSyncCurrentDevice(loaded.currentDeviceId)) {
            syncCurrentDevice()
        }
    }

    private suspend fun loadDashboard(account: AccountProfile? = null): LoadedAccountDashboard =
        withContext(Dispatchers.IO) {
            val dashboard = repository.dashboard(account)
            val currentDevice = repository.currentDevice()
            LoadedAccountDashboard(
                dashboard = currentDevice?.let(dashboard::withCurrentDevice) ?: dashboard,
                currentDeviceId = currentDevice?.id.orEmpty(),
            )
        }

    private suspend fun syncCurrentDevice(reportErrors: Boolean = false) {
        val result = withContext(Dispatchers.IO) { runCatching { repository.enrollAndFetchBundle() } }
        result.onSuccess { sync ->
            applyDeviceSync(sync)
            if (!sync.bundle.isNullOrBlank()) {
                profilePollingJob?.cancel()
                val shouldApply = repository.shouldApplyManagedProfile(sync.bundle)
                state = state.copy(
                    pendingProfileBundle = sync.bundle.takeIf { shouldApply },
                    feedback = if (shouldApply) {
                        "Профиль устройства готов"
                    } else {
                        "Это устройство привязано"
                    },
                )
            } else if (state.dashboard?.subscription?.subscription != null) {
                startProfilePolling()
            }
        }.onFailure { error ->
            val hasSubscription = state.dashboard?.subscription?.subscription != null
            if (reportErrors || hasSubscription || error !is AccountApiException || error.statusCode !in setOf(404, 409)) {
                state = state.copy(feedback = readableError(error), feedbackIsError = true)
            }
        }
    }

    private fun startProfilePolling() {
        if (profilePollingJob?.isActive == true) return
        val generation = operationGeneration
        profilePollingJob = viewModelScope.launch {
            repeat(ProfilePollAttempts) {
                delay(ProfilePollIntervalMillis)
                if (generation != operationGeneration) return@launch
                val sync = withContext(Dispatchers.IO) {
                    try {
                        repository.enrollAndFetchBundle()
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        null
                    }
                }
                if (sync != null) {
                    applyDeviceSync(sync)
                }
                if (!sync?.bundle.isNullOrBlank()) {
                    val bundle = sync.bundle
                    val shouldApply = repository.shouldApplyManagedProfile(bundle)
                    state = state.copy(
                        pendingProfileBundle = bundle.takeIf { shouldApply },
                        feedback = if (shouldApply) {
                            "Профиль устройства готов"
                        } else {
                            "Это устройство привязано"
                        },
                    )
                    return@launch
                }
            }
            state = state.copy(feedback = "Настройка устройства продолжается. Обновите данные чуть позже")
        }
    }

    private fun applyDeviceSync(sync: AccountDeviceSync) {
        state = state.copy(
            currentDeviceId = sync.device.id,
            dashboard = state.dashboard?.withCurrentDevice(sync.device),
        )
    }

    private fun launchAction(block: suspend () -> Unit) {
        if (state.busy) return
        val generation = operationGeneration
        state = state.copy(busy = true, feedback = "", feedbackIsError = false)
        actionJob = viewModelScope.launch {
            try {
                block()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (generation == operationGeneration) {
                    if (error is AccountApiException && error.message == "email is not verified") {
                        state = state.copy(stage = AccountStage.VERIFY_EMAIL)
                    }
                    state = state.copy(feedback = readableError(error), feedbackIsError = true)
                }
            } finally {
                if (generation == operationGeneration) {
                    state = state.copy(busy = false)
                }
            }
        }
    }

    private fun readableError(error: Throwable): String = when (error.message) {
        "invalid email or password" -> "Неверная почта или пароль"
        "email is not verified" -> "Подтвердите почту кодом из письма"
        "account with this email already exists" -> "Аккаунт с этой почтой уже существует"
        "invalid or expired confirmation code" -> "Код неверный или уже истёк"
        "confirmation code was sent recently" -> "Код уже отправлен. Повторите через минуту"
        "invalid or expired session" -> "Сессия завершена. Войдите снова"
        "device limit reached" -> "Достигнут лимит устройств"
        "test period has already been used" -> "Пробный период уже был использован"
        "account service is temporarily unavailable" -> "Сервис аккаунтов временно недоступен"
        else -> error.message?.takeIf(String::isNotBlank) ?: "Не удалось выполнить запрос"
    }

    private companion object {
        const val ResumeRefreshIntervalMillis = 3_000L
        const val ProfilePollIntervalMillis = 5_000L
        const val ProfilePollAttempts = 24
    }
}

private class AccountRepository(application: Application) {
    private val api = WhiteZiaAccountApi(application)
    private val secureStore = SecureAccountStore(application)
    private val deviceName = listOf(Build.MANUFACTURER, Build.MODEL)
        .joinToString(" ")
        .trim()
        .replaceFirstChar { it.titlecase(Locale.getDefault()) }
        .ifBlank { "Android" }
    private var accessToken = ""
    private var account: AccountProfile? = null

    fun restore(): AccountProfile? {
        val refreshToken = secureStore.refreshToken() ?: return null
        return try {
            applySession(api.refresh(refreshToken)).account
        } catch (error: AccountApiException) {
            if (error.statusCode !in setOf(401, 403)) throw error
            secureStore.clearRefreshToken()
            accessToken = ""
            account = null
            null
        }
    }

    fun hasManagedProfile(): Boolean = secureStore.hasManagedProfile()

    fun shouldApplyManagedProfile(bundle: String): Boolean = secureStore.shouldApplyManagedProfile(bundle)

    fun markManagedProfileInstalled(bundle: String) = secureStore.markManagedProfileInstalled(bundle)

    fun clearManagedProfile() = secureStore.clearManagedProfile()

    fun canRestoreSession(): Boolean = secureStore.hasRefreshToken()

    fun register(email: String, password: String, displayName: String) =
        api.register(email, password, displayName)

    fun verifyEmail(email: String, code: String): AccountSession =
        applySession(api.verifyEmail(email, code))

    fun resendVerification(email: String) = api.resendVerification(email)

    fun login(email: String, password: String): AccountSession =
        applySession(api.login(email, password))

    fun requestPasswordReset(email: String) = api.requestPasswordReset(email)

    fun resetPassword(email: String, code: String, password: String) =
        api.resetPassword(email, code, password)

    fun dashboard(knownAccount: AccountProfile? = account): AccountDashboard = withAccess { token ->
        val currentAccount = knownAccount ?: api.account(token)
        account = currentAccount
        val subscription = api.subscription(token)
        AccountDashboard(
            account = currentAccount,
            subscription = subscription,
            devices = api.devices(token),
            payments = api.payments(token),
            plans = api.plans().availablePlans(subscription.trialAvailable),
        )
    }

    fun enrollAndFetchBundle(): AccountDeviceSync = withAccess { token ->
        val stableInstallationId = secureStore.stableInstallationId()
        val device = api.enrollDevice(
            accessToken = token,
            installationId = stableInstallationId,
            publicKey = secureStore.devicePublicKey(),
            name = deviceName,
        )
        secureStore.promoteStableInstallationId()
        val bundle = if (device.bundleReady || device.status == "active") {
            api.deviceBundle(token, device.id)
        } else {
            null
        }
        AccountDeviceSync(device = device, bundle = bundle)
    }

    fun currentDevice(): AccountDevice? = withAccess { token ->
        val installationId = secureStore.installationId()
        val stableInstallationId = secureStore.stableInstallationId()
        val current = api.currentDevice(token, installationId)
        if (current != null) {
            if (installationId != stableInstallationId) {
                runCatching {
                    api.linkCurrentDeviceIdentity(token, installationId, stableInstallationId)
                }.onSuccess {
                    secureStore.promoteStableInstallationId()
                }
            }
            return@withAccess current
        }
        if (installationId == stableInstallationId) {
            return@withAccess null
        }
        api.currentDevice(token, stableInstallationId)?.also {
            secureStore.promoteStableInstallationId()
        }
    }

    fun createOrder(planId: String): String = withAccess { api.createOrder(it, planId) }

    fun redeemTrial() = withAccess { api.redeemTrial(it) }

    fun disableDevice(deviceId: String) = withAccess { api.disableDevice(it, deviceId) }

    fun clearLocalSession(): String? {
        val refreshToken = secureStore.refreshToken()
        secureStore.clearRefreshToken()
        accessToken = ""
        account = null
        return refreshToken
    }

    fun revokeSession(refreshToken: String) {
        runCatching { api.logout(refreshToken) }
    }

    private fun applySession(session: AccountSession): AccountSession {
        accessToken = session.accessToken
        account = session.account
        secureStore.saveRefreshToken(session.refreshToken)
        return session
    }

    private inline fun <T> withAccess(block: (String) -> T): T {
        check(accessToken.isNotBlank()) { "Сессия завершена. Войдите снова" }
        return try {
            block(accessToken)
        } catch (error: AccountApiException) {
            if (error.statusCode != 401) throw error
            val refreshToken = secureStore.refreshToken() ?: throw error
            applySession(api.refresh(refreshToken))
            block(accessToken)
        }
    }
}

private data class LoadedAccountDashboard(
    val dashboard: AccountDashboard,
    val currentDeviceId: String,
)
