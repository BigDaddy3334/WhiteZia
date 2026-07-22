package shop.whitezia.client.account

data class AccountProfile(
    val id: String,
    val email: String,
    val displayName: String,
    val telegramLinked: Boolean,
)

data class AccountSession(
    val accessToken: String,
    val refreshToken: String,
    val expiresInSeconds: Long,
    val account: AccountProfile,
)

data class AccountSubscription(
    val id: String,
    val planId: String,
    val status: String,
    val expiresAt: String,
    val isForever: Boolean,
)

data class AccountSubscriptionStatus(
    val subscription: AccountSubscription?,
    val deviceLimit: Int,
    val deviceCount: Int,
    val trialAvailable: Boolean = false,
)

data class AccountDevice(
    val id: String,
    val name: String,
    val status: String,
    val platform: String,
    val bundleReady: Boolean,
    val createdAt: String,
)

data class AccountPayment(
    val id: String,
    val planId: String,
    val amountMinor: Long,
    val currency: String,
    val status: String,
    val createdAt: String,
)

data class AccountPlan(
    val id: String,
    val title: String,
    val durationDays: Int,
    val priceMinor: Long,
    val originalPriceMinor: Long = 0L,
    val currency: String,
)

internal val AccountPlan.isTrial: Boolean
    get() = id == "trial" && priceMinor == 0L

internal val AccountPlan.hasPromotionalPrice: Boolean
    get() = priceMinor > 0L && originalPriceMinor > priceMinor

internal fun List<AccountPlan>.availablePlans(trialAvailable: Boolean): List<AccountPlan> =
    filter { !it.isTrial || trialAvailable }

data class AccountDashboard(
    val account: AccountProfile,
    val subscription: AccountSubscriptionStatus,
    val devices: List<AccountDevice>,
    val payments: List<AccountPayment>,
    val plans: List<AccountPlan>,
)

enum class AccountStage {
    RESTORING,
    SIGN_IN,
    REGISTER,
    VERIFY_EMAIL,
    RECOVERY,
    RESET_PASSWORD,
    DASHBOARD,
}

data class AccountUiState(
    val stage: AccountStage = AccountStage.RESTORING,
    val managedProfileInstalled: Boolean = false,
    val busy: Boolean = false,
    val email: String = "",
    val feedback: String = "",
    val feedbackIsError: Boolean = false,
    val dashboard: AccountDashboard? = null,
    val paymentUrl: String? = null,
    val pendingProfileBundle: String? = null,
    val currentDeviceId: String = "",
)

internal data class AccountDeviceSync(
    val device: AccountDevice,
    val bundle: String?,
)

internal data class RecoveryDeviceBundle(
    val deviceId: String,
    val bundle: String,
    val updatedAt: String,
)

internal fun AccountDashboard.withCurrentDevice(device: AccountDevice): AccountDashboard {
    val updatedDevices = devices
        .filterNot { it.id == device.id }
        .plus(device)
        .sortedBy(AccountDevice::createdAt)
    return copy(
        subscription = subscription.copy(deviceCount = updatedDevices.size),
        devices = updatedDevices,
    )
}

internal fun AccountDashboard.shouldSyncCurrentDevice(currentDeviceId: String): Boolean =
    currentDeviceId.isNotBlank() || devices.isEmpty()

class AccountApiException(
    val statusCode: Int,
    message: String,
) : Exception(message)
