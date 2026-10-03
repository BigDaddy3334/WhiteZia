package shop.whitezia.client.account

import android.app.Application
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
    private var enrollmentGeneration = 0L
    private var connectionProfileBundle: String? = null

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
            busy = true,
            managedProfileInstalled = state.managedProfileInstalled,
            email = state.email,
            feedback = "Пароль изменён. Теперь можно войти",
        )
    }

    fun refreshDashboard() = launchAction {
        val loaded = loadDashboard()
        if (loaded.currentDeviceId.isBlank()) invalidateCurrentDeviceProfile()
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
            if (loaded.currentDeviceId.isBlank()) invalidateCurrentDeviceProfile()
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
        profilePollingJob?.cancel()
        enrollmentGeneration += 1
        withContext(Dispatchers.IO) { repository.disableDevice(deviceId) }
        if (state.currentDeviceId == deviceId) invalidateCurrentDeviceProfile()
        val dashboard = withContext(Dispatchers.IO) { repository.dashboard() }
        state = state.copy(
            dashboard = dashboard,
            currentDeviceId = state.currentDeviceId.takeUnless { it == deviceId }.orEmpty(),
            feedback = "Устройство отключено",
            enrollment = null,
        )
    }

    fun logout() {
        operationGeneration += 1
        enrollmentGeneration += 1
        connectionProfileBundle = null
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
        syncCurrentDevice(reportErrors = true, allowEnrollment = true)
    }

    fun profileInvalidationHandled(revision: Long) {
        if (state.profileInvalidationRevision == revision) {
            state = state.copy(profileInvalidationRevision = 0L)
        }
    }

    fun profileBundleApplied(bundle: String) {
        val enrollmentApplied = state.pendingProfileBundle == bundle
        if (!enrollmentApplied && connectionProfileBundle != bundle) return
        connectionProfileBundle = null
        repository.markManagedProfileInstalled(bundle)
        state = state.copy(
            managedProfileInstalled = true,
            pendingProfileBundle = null,
            feedback = "Профиль устройства применён",
            enrollment = if (enrollmentApplied) state.enrollment?.copy(
                stage = DeviceEnrollmentStage.READY,
                finishedAtMillis = SystemClock.elapsedRealtime(),
                polling = false,
            ) else state.enrollment,
        )
    }

    fun profileBundleRejected(message: String) {
        if (state.pendingProfileBundle == null && connectionProfileBundle == null) return
        connectionProfileBundle = null
        state = state.copy(
            pendingProfileBundle = null,
            feedback = message,
            feedbackIsError = true,
            enrollment = state.enrollment?.copy(
                stage = DeviceEnrollmentStage.FAILED,
                finishedAtMillis = SystemClock.elapsedRealtime(),
                polling = false,
                failure = message,
                failedAtStage = DeviceEnrollmentStage.APPLYING,
            ),
        )
    }

    fun retrySessionRestore() {
        if (state.stage == AccountStage.SIGN_IN && !state.busy && repository.canRestoreSession()) {
            restoreSession()
        }
    }

    suspend fun refreshManagedProfileBeforeConnection(): String? {
        actionJob?.takeIf(Job::isActive)?.join()
        val generation = operationGeneration
        val enrollment = enrollmentGeneration
        val bundle = try {
            withContext(Dispatchers.IO) {
                try {
                    repository.latestManagedProfileBundle()
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (error: AccountDeviceNotBoundException) {
                    throw error
                } catch (error: Exception) {
                    repository.recoverManagedProfileBundle(error)
                }
            }
        } catch (error: AccountDeviceNotBoundException) {
            if (generation != operationGeneration || enrollment != enrollmentGeneration) {
                throw CancellationException("Account device changed")
            }
            invalidateCurrentDeviceProfile()
            state = state.copy(feedback = checkNotNull(error.message), feedbackIsError = true)
            throw error
        }
        if (generation != operationGeneration || enrollment != enrollmentGeneration) {
            throw CancellationException("Account device changed")
        }
        connectionProfileBundle = bundle
        return bundle
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
                val recoveredBundle = try {
                    withContext(Dispatchers.IO) { repository.recoverManagedProfileBundle(error) }
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Exception) {
                    null
                }
                if (recoveredBundle != null) {
                    val shouldApply = repository.shouldApplyManagedProfile(recoveredBundle)
                    state = AccountUiState(
                        stage = AccountStage.SIGN_IN,
                        managedProfileInstalled = state.managedProfileInstalled,
                        pendingProfileBundle = recoveredBundle.takeIf { shouldApply },
                        feedback = if (shouldApply) {
                            "Основной сервис недоступен. Профиль восстановлен"
                        } else {
                            "Основной сервис недоступен. Используется сохранённый профиль"
                        },
                    )
                } else {
                    state = AccountUiState(
                        stage = AccountStage.SIGN_IN,
                        managedProfileInstalled = state.managedProfileInstalled,
                        feedback = readableError(error),
                        feedbackIsError = true,
                    )
                }
            } finally {
                if (generation == operationGeneration) {
                    state = state.copy(busy = false)
                }
            }
        }
    }

    private suspend fun loadSignedIn(account: AccountProfile) {
        val loaded = loadDashboard(account)
        if (loaded.currentDeviceId.isBlank()) invalidateCurrentDeviceProfile()
        state = AccountUiState(
            stage = AccountStage.DASHBOARD,
            busy = true,
            managedProfileInstalled = state.managedProfileInstalled,
            dashboard = loaded.dashboard,
            currentDeviceId = loaded.currentDeviceId,
            profileInvalidationRevision = state.profileInvalidationRevision,
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

    private suspend fun syncCurrentDevice(reportErrors: Boolean = false, allowEnrollment: Boolean = false) {
        profilePollingJob?.cancel()
        val generation = operationGeneration
        val enrollment = ++enrollmentGeneration
        var enrollmentConfirmed = false
        state = state.copy(
            pendingProfileBundle = null,
            enrollment = DeviceEnrollmentProgress(
                stage = DeviceEnrollmentStage.PREPARING,
                startedAtMillis = SystemClock.elapsedRealtime(),
            ),
        )
        try {
            val sync = withContext(Dispatchers.IO) {
                if (!allowEnrollment) return@withContext repository.pollCurrentDeviceBundle()
                repository.enrollAndFetchBundle { stage ->
                    if (stage == DeviceEnrollmentStage.PROVISIONING) enrollmentConfirmed = true
                    viewModelScope.launch {
                        if (generation == operationGeneration && enrollment == enrollmentGeneration) {
                            val progress = state.enrollment
                            if (progress != null && stage.ordinal > progress.stage.ordinal) {
                                state = state.copy(enrollment = progress.copy(stage = stage))
                            }
                        }
                    }
                }
            }
            if (generation != operationGeneration || enrollment != enrollmentGeneration) return
            if (sync == null) {
                invalidateCurrentDeviceProfile()
                return
            }
            applyDeviceSync(sync)
            if (!sync.bundle.isNullOrBlank()) {
                acceptDeviceBundle(sync.bundle)
            } else if (sync.device.status in setOf("failed", "disabled")) {
                failEnrollment("Сервер не смог подготовить устройство. Повторите привязку")
            } else {
                state = state.copy(enrollment = state.enrollment?.copy(stage = DeviceEnrollmentStage.PROVISIONING))
                if (state.dashboard?.subscription?.subscription != null) startProfilePolling()
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: AccountDeviceNotBoundException) {
            if (generation != operationGeneration || enrollment != enrollmentGeneration) return
            invalidateCurrentDeviceProfile()
            state = state.copy(feedback = checkNotNull(error.message), feedbackIsError = true)
        } catch (error: Exception) {
            if (generation != operationGeneration || enrollment != enrollmentGeneration) return
            val hasSubscription = state.dashboard?.subscription?.subscription != null
            if (shouldRetryDeviceEnrollment(error)) {
                state = state.copy(
                    feedback = "Подготовка профиля продолжается. Проверяем готовность",
                    feedbackIsError = false,
                    enrollment = state.enrollment?.copy(stage = DeviceEnrollmentStage.PROVISIONING),
                )
                startProfilePolling(retryEnrollment = allowEnrollment && !enrollmentConfirmed)
            } else if (reportErrors || hasSubscription || error !is AccountApiException || error.statusCode !in setOf(404, 409)) {
                failEnrollment(readableError(error))
            } else {
                state = state.copy(enrollment = null)
            }
        }
    }

    private fun acceptDeviceBundle(bundle: String) {
        val shouldApply = repository.shouldApplyManagedProfile(bundle)
        state = state.copy(
            pendingProfileBundle = bundle.takeIf { shouldApply },
            feedback = if (shouldApply) "Применяем профиль устройства" else "Профиль устройства готов",
            feedbackIsError = false,
            enrollment = state.enrollment?.copy(
                stage = if (shouldApply) DeviceEnrollmentStage.APPLYING else DeviceEnrollmentStage.READY,
                finishedAtMillis = if (shouldApply) null else SystemClock.elapsedRealtime(),
                polling = false,
                pollError = "",
            ),
        )
    }

    private fun failEnrollment(message: String) {
        state = state.copy(
            feedback = message,
            feedbackIsError = true,
            enrollment = state.enrollment?.copy(
                stage = DeviceEnrollmentStage.FAILED,
                finishedAtMillis = SystemClock.elapsedRealtime(),
                polling = false,
                failure = message,
                failedAtStage = state.enrollment?.stage,
            ),
        )
    }

    private fun startProfilePolling(retryEnrollment: Boolean = false) {
        val generation = operationGeneration
        val enrollment = enrollmentGeneration
        state = state.copy(enrollment = state.enrollment?.copy(polling = true))
        profilePollingJob = viewModelScope.launch {
            var enrollmentUnconfirmed = retryEnrollment
            val deadline = SystemClock.elapsedRealtime() + DeviceProfilePollWindowMillis
            while (SystemClock.elapsedRealtime() < deadline) {
                delay(ProfilePollIntervalMillis)
                if (generation != operationGeneration || enrollment != enrollmentGeneration) return@launch
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        if (enrollmentUnconfirmed && repository.currentDevice() == null) {
                            repository.enrollAndFetchBundle { stage ->
                                if (stage == DeviceEnrollmentStage.PROVISIONING) enrollmentUnconfirmed = false
                            }
                        } else {
                            enrollmentUnconfirmed = false
                            repository.pollCurrentDeviceBundle()
                        }
                    }
                }
                result.exceptionOrNull()?.let { if (it is CancellationException) throw it }
                if (generation != operationGeneration || enrollment != enrollmentGeneration) return@launch
                val error = result.exceptionOrNull()
                if (error is AccountDeviceNotBoundException) {
                    invalidateCurrentDeviceProfile()
                    state = state.copy(feedback = checkNotNull(error.message), feedbackIsError = true)
                    return@launch
                }
                state = state.copy(
                    enrollment = state.enrollment?.afterPoll(error?.let(::readableError).orEmpty()),
                )
                if (error != null && !shouldRetryDeviceEnrollment(error)) {
                    failEnrollment(readableError(error))
                    return@launch
                }
                val sync = result.getOrNull()
                if (error == null && sync == null) {
                    invalidateCurrentDeviceProfile()
                    return@launch
                }
                if (sync != null) {
                    enrollmentUnconfirmed = false
                    applyDeviceSync(sync)
                    if (sync.device.status in setOf("failed", "disabled")) {
                        failEnrollment("Сервер не смог подготовить устройство. Повторите привязку")
                        return@launch
                    }
                    if (!sync.bundle.isNullOrBlank()) {
                        acceptDeviceBundle(sync.bundle)
                        return@launch
                    }
                }
            }
            state = state.copy(
                enrollment = state.enrollment?.copy(polling = false),
                feedback = "Сервер ещё готовит профиль. Можно проверить готовность позже",
                feedbackIsError = false,
            )
        }
    }

    private fun applyDeviceSync(sync: AccountDeviceSync) {
        state = state.copy(
            currentDeviceId = sync.device.id,
            dashboard = state.dashboard?.withCurrentDevice(sync.device),
        )
    }

    private fun invalidateCurrentDeviceProfile() {
        val hadProfile = state.managedProfileInstalled || state.pendingProfileBundle != null ||
            connectionProfileBundle != null || repository.hasManagedProfile()
        enrollmentGeneration += 1
        profilePollingJob?.cancel()
        profilePollingJob = null
        connectionProfileBundle = null
        repository.clearManagedProfile()
        state = state.copy(
            managedProfileInstalled = false,
            pendingProfileBundle = null,
            currentDeviceId = "",
            enrollment = null,
            profileInvalidationRevision = state.profileInvalidationRevision + if (hadProfile) 1L else 0L,
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
    }
}

private data class LoadedAccountDashboard(
    val dashboard: AccountDashboard,
    val currentDeviceId: String,
)
