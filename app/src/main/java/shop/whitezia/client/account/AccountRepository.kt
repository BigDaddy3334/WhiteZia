package shop.whitezia.client.account

import android.app.Application
import android.os.Build
import java.util.Locale

internal class AccountRepository(application: Application) {
    private val api = WhiteZiaAccountApi(application)
    private val secureStore = SecureAccountStore(application)
    private val deviceName = listOf(Build.MANUFACTURER, Build.MODEL)
        .joinToString(" ")
        .trim()
        .replaceFirstChar { it.titlecase(Locale.getDefault()) }
        .ifBlank { "Android" }
    private val sessions = AccountSessionManager(
        readRefreshToken = secureStore::refreshToken,
        saveRefreshToken = secureStore::saveRefreshToken,
        clearRefreshToken = secureStore::clearRefreshToken,
        rotate = api::refresh,
    )

    fun restore(): AccountProfile? = sessions.restore()

    fun hasManagedProfile(): Boolean = secureStore.hasManagedProfile()

    fun shouldApplyManagedProfile(bundle: String): Boolean = secureStore.shouldApplyManagedProfile(bundle)

    fun markManagedProfileInstalled(bundle: String) = secureStore.markManagedProfileInstalled(bundle)

    fun clearManagedProfile() = secureStore.clearManagedProfile()

    fun canRestoreSession(): Boolean = secureStore.hasRefreshToken()

    fun recoverManagedProfileBundle(cause: Throwable): String? {
        if (!shouldAttemptAccountRecovery(cause)) return null
        val refreshToken = secureStore.refreshToken() ?: return null
        val installationIds = recoveryInstallationCandidates(
            storedId = secureStore.installationId(),
            stableId = secureStore.stableInstallationId(),
        )
        for (installationId in installationIds) {
            val challenge = api.recoveryDeviceBundleChallenge(refreshToken, installationId) ?: continue
            val signature = secureStore.signDeviceChallenge(challenge.challenge)
            api.recoveryDeviceBundle(challenge.id, signature)?.let { return it.bundle }
        }
        return null
    }

    fun latestManagedProfileBundle(): String? {
        if (!canRestoreSession()) return null
        if (sessions.snapshot().accessToken.isBlank() && restore() == null) return null
        val sync = pollCurrentDeviceBundle() ?: throw AccountDeviceNotBoundException()
        if (sync.device.status == "disabled") throw AccountDeviceNotBoundException()
        return sync.bundle
    }

    fun register(email: String, password: String, displayName: String) =
        api.register(email, password, displayName)

    fun verifyEmail(email: String, code: String): AccountSession {
        val generation = sessions.snapshot()
        return sessions.apply(api.verifyEmail(email, code), generation)
    }

    fun resendVerification(email: String) = api.resendVerification(email)

    fun login(email: String, password: String): AccountSession {
        val generation = sessions.snapshot()
        return sessions.apply(api.login(email, password), generation)
    }

    fun requestPasswordReset(email: String) = api.requestPasswordReset(email)

    fun resetPassword(email: String, code: String, password: String) =
        api.resetPassword(email, code, password)

    fun dashboard(knownAccount: AccountProfile? = sessions.account()): AccountDashboard {
        val generation = sessions.snapshot()
        val dashboard = withAccess { token ->
            val currentAccount = knownAccount ?: api.account(token)
            val subscription = api.subscription(token)
            AccountDashboard(
                account = currentAccount,
                subscription = subscription,
                devices = api.devices(token),
                payments = api.payments(token),
                plans = api.plans().availablePlans(subscription.trialAvailable),
            )
        }
        return sessions.ifCurrent(generation) {
            sessions.updateAccount(generation, dashboard.account)
            dashboard
        }
    }

    fun enrollAndFetchBundle(
        onStage: (DeviceEnrollmentStage) -> Unit = {},
    ): AccountDeviceSync = withAccess { token ->
        val stableInstallationId = secureStore.stableInstallationId()
        val publicKey = secureStore.devicePublicKey()
        onStage(DeviceEnrollmentStage.BINDING)
        val device = api.enrollDevice(
            accessToken = token,
            installationId = stableInstallationId,
            publicKey = publicKey,
            name = deviceName,
        )
        secureStore.promoteStableInstallationId()
        onStage(DeviceEnrollmentStage.PROVISIONING)
        val bundle = if (device.bundleReady || device.status == "active") {
            try {
                fetchCurrentDeviceBundle(token)
            } catch (error: AccountApiException) {
                if (isDeviceProfilePending(error)) null else throw error
            }
        } else {
            null
        }
        AccountDeviceSync(device = device, bundle = bundle)
    }

    fun pollCurrentDeviceBundle(): AccountDeviceSync? = readExistingDeviceProfile(
        findDevice = ::currentDevice,
        fetchBundle = ::fetchCurrentDeviceBundle,
    )

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

    private fun fetchCurrentDeviceBundle(): String = withAccess { token ->
        fetchCurrentDeviceBundle(token)
    }

    private fun fetchCurrentDeviceBundle(token: String): String {
        val installationId = secureStore.stableInstallationId()
        val challenge = api.deviceBundleChallenge(token, installationId)
        val signature = secureStore.signDeviceChallenge(challenge.challenge)
        return api.deviceBundle(token, installationId, challenge.id, signature)
    }

    fun clearLocalSession(): String? = sessions.clear()

    fun revokeSession(refreshToken: String) {
        runCatching { api.logout(refreshToken) }
    }

    private fun <T> withAccess(block: (String) -> T): T = sessions.withAccess(block)
}
