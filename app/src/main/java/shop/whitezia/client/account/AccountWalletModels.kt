package shop.whitezia.client.account

import java.math.BigDecimal

data class AccountWallet(
    val balanceMinor: Long,
    val availableMinor: Long,
    val currency: String,
    val blocked: Boolean,
    val entries: List<AccountWalletEntry>,
    val pendingTopup: AccountTopup?,
    val autorenew: AccountAutorenew?,
    val minimumTopupMinor: Long,
    val maximumTopupMinor: Long,
)

data class AccountWalletEntry(val id: String, val amountMinor: Long, val kind: String, val createdAt: String)
data class AccountTopup(val id: String, val amountMinor: Long, val status: String, val paymentUrl: String)
data class AccountAutorenew(val enabled: Boolean, val planId: String, val maximumPriceMinor: Long, val state: String, val durationDays: Int)
data class AccountReferrals(
    val code: String, val invited: Long, val pendingMinor: Long, val earnedMinor: Long,
    val rewardMinor: Long, val ratePercent: Int, val minimumPurchaseMinor: Long, val holdDays: Int,
)

internal fun parseTopupAmount(value: String): Long? = runCatching {
    val normalized = value.trim().replace(',', '.')
    require(Regex("[0-9]{1,5}(\\.[0-9]{1,2})?").matches(normalized))
    BigDecimal(normalized).movePointRight(2).longValueExact()
}.getOrNull()

internal fun AccountDashboard.renewalPlan(): AccountPlan? {
    val current = subscription.subscription ?: return null
    if (current.isForever || current.status !in listOf("active", "pending") || subscription.remainingSeconds <= 0) return null
    return plans.firstOrNull { it.id == current.planId && !it.isTrial && it.priceMinor > 0 && it.durationDays > 0 }
        ?.forDeviceLimit(subscription.deviceLimit)
}

internal fun AccountWallet.canPay(amountMinor: Long): Boolean =
    !blocked && amountMinor > 0 && availableMinor >= amountMinor

internal fun normalizeReferralCode(value: String): String {
    val raw = value.trim()
    if (raw.isBlank()) return ""
    val code = if (raw.startsWith("https://whitezia.su/")) {
        java.net.URI(raw).rawQuery.orEmpty().split('&').firstOrNull { it.startsWith("ref=") }?.removePrefix("ref=").orEmpty()
    } else raw
    require(Regex("[a-f0-9]{32}").matches(code)) { "invalid referral code" }
    return code
}
