package shop.whitezia.client.account

import org.junit.Assert.*
import org.junit.Test

class AccountWalletTest {
    @Test fun topupAmountsKeepExactKopecksAndRejectAmbiguousInput() {
        assertEquals(15000L, parseTopupAmount("150"))
        assertEquals(1001L, parseTopupAmount("10,01"))
        assertEquals(1000000L, parseTopupAmount("10000.00"))
        for (raw in listOf("-10", "1e3", "10.001", "NaN", "1 000", ".50", "")) assertNull(raw, parseTopupAmount(raw))
    }

    @Test fun walletParsingPreservesDebtAndSeparatePendingTopup() {
        val wallet = parseAccountWallet("""{"balance_minor":-4000,"available_minor":0,"currency":"RUB","blocked":true,
            "minimum_topup_minor":1000,"maximum_topup_minor":1000000,
            "entries":[{"id":"entry","amount_minor":-4000,"kind":"referral_reversal","created_at":"2026-11-01T00:00:00Z"}],
            "pending_topup":{"id":"topup","amount_minor":15000,"status":"pending","payment_url":"https://example.test/pay"},
            "autorenew":{"enabled":true,"plan_id":"month","maximum_price_minor":30000,"duration_days":31,"state":"payment_review"}}""")
        assertEquals(-4000L, wallet.balanceMinor)
        assertEquals("topup", wallet.pendingTopup?.id)
        assertEquals(31, wallet.autorenew?.durationDays)
        assertFalse(wallet.canPay(1000))
        assertFalse(wallet.copy(blocked = false).canPay(1000))
        assertTrue(wallet.copy(blocked = false, availableMinor = 15000).canPay(15000))
        assertFalse(wallet.copy(blocked = false, availableMinor = 15000).canPay(15001))
    }

    @Test fun renewalQuotesActualPeriodAndAllPaidDevices() {
        val plan = AccountPlan("month", "Месяц", 31, 15000, currency = "RUB", devicePricing = AccountDevicePricing(3, 6, 5000))
        val status = AccountSubscriptionStatus(AccountSubscription("s", "month", "active", "2099-01-01T00:00:00Z", false), 6, 3, remainingSeconds = 10)
        val dashboard = AccountDashboard(AccountProfile("a", "test@example.test", "", false), status, emptyList(), emptyList(), listOf(plan))
        assertEquals(30000L, dashboard.renewalPlan()?.priceMinor)
        assertEquals(31, dashboard.renewalPlan()?.durationDays)
        assertNull(dashboard.copy(subscription = status.copy(remainingSeconds = 0)).renewalPlan())
        assertNull(dashboard.copy(subscription = status.copy(subscription = status.subscription?.copy(isForever = true))).renewalPlan())
        assertNull(dashboard.copy(plans = emptyList()).renewalPlan())
    }

    @Test fun referralConditionsComeFromServerAndLinkCanBePasted() {
        val referral = parseAccountReferrals("""{"mode":"hybrid","rate_percent":10,"code":"${"a".repeat(32)}","invited":2,"reward_minor":4000,
            "minimum_purchase_minor":15000,"hold_days":7,"pending_minor":5500,"earned_minor":4000}""")
        assertEquals(4000L, referral.rewardMinor)
        assertEquals(10, referral.ratePercent)
        assertEquals(15000L, referral.minimumPurchaseMinor)
        assertEquals("a".repeat(32), normalizeReferralCode("https://whitezia.su/account.html?ref=${"a".repeat(32)}#account-referrals"))
        assertEquals("", normalizeReferralCode(" "))
        assertThrows(IllegalArgumentException::class.java) { normalizeReferralCode("https://evil.test/?ref=${"a".repeat(32)}") }
    }

    @Test fun uncertainRequestSurvivesRecreationAndIsScopedToAccountAndQuote() {
        val saved = mutableMapOf<String, String>()
        fun store() = WalletRequestStore(saved::get, { name, value -> saved[name] = value; true }, { saved.remove(it); true })
        val key = store().key("account-a", "subscription:month:6:30000")
        assertEquals(key, store().key("account-a", "subscription:month:6:30000"))
        assertNotEquals(key, store().key("account-b", "subscription:month:6:30000"))
        assertNotEquals(key, store().key("account-a", "subscription:month:6:35000"))
        store().complete("account-a", "subscription:month:6:30000")
        assertNotEquals(key, store().key("account-a", "subscription:month:6:30000"))
    }

    @Test fun failedPersistenceNeverAuthorizesAMoneyRequest() {
        val store = WalletRequestStore({ null }, { _, _ -> false }, { false })
        assertThrows(IllegalStateException::class.java) { store.key("account-a", "topup:15000") }
    }

    @Test fun concurrentRepositoriesShareThePersistedKey() {
        val saved = mutableMapOf<String, String>()
        val keys = java.util.Collections.synchronizedList(mutableListOf<String>())
        val start = java.util.concurrent.CountDownLatch(1)
        val threads = List(8) {
            Thread {
                val store = WalletRequestStore(saved::get, { name, value -> saved[name] = value; true }, { saved.remove(it); true })
                start.await()
                keys.add(store.key("same-account", "subscription:month:6:30000"))
            }.apply { start() }
        }
        start.countDown()
        threads.forEach { it.join(3000) }
        assertEquals(8, keys.size)
        assertEquals(1, keys.toSet().size)
    }
}
