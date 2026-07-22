package shop.whitezia.client.account

import org.junit.Assert.assertEquals
import org.junit.Test

class AccountModelsTest {
    @Test
    fun onlyZeroPricedTrialPlanUsesTrialFlow() {
        assertEquals(true, plan("trial", 0).isTrial)
        assertEquals(false, plan("trial", 100).isTrial)
        assertEquals(false, plan("promo", 0).isTrial)
    }

    @Test
    fun trialPlanIsVisibleOnlyWhileAccountIsEligible() {
        val plans = listOf(plan("trial", 0), plan("month", 29900))

        assertEquals(listOf("trial", "month"), plans.availablePlans(trialAvailable = true).map(AccountPlan::id))
        assertEquals(listOf("month"), plans.availablePlans(trialAvailable = false).map(AccountPlan::id))
    }

    @Test
    fun promotionalPriceRequiresHigherOriginalPrice() {
        assertEquals(true, plan("month", 15000, 29900).hasPromotionalPrice)
        assertEquals(false, plan("month", 15000, 15000).hasPromotionalPrice)
        assertEquals(false, plan("month", 15000, 0).hasPromotionalPrice)
    }

    @Test
    fun withCurrentDeviceAddsNewDeviceAndUpdatesCount() {
        val dashboard = dashboard(devices = listOf(device("first", "OnePlus")))

        val updated = dashboard.withCurrentDevice(device("second", "Samsung"))

        assertEquals(listOf("first", "second"), updated.devices.map(AccountDevice::id))
        assertEquals(2, updated.subscription.deviceCount)
    }

    @Test
    fun withCurrentDeviceReplacesExistingDeviceWithoutDuplicate() {
        val dashboard = dashboard(devices = listOf(device("current", "Old name")))

        val updated = dashboard.withCurrentDevice(device("current", "New name"))

        assertEquals(1, updated.devices.size)
        assertEquals("New name", updated.devices.single().name)
        assertEquals(1, updated.subscription.deviceCount)
    }

    @Test
    fun knownCurrentDeviceIsSynchronizedAfterLogin() {
        val dashboard = dashboard(devices = listOf(device("current", "OnePlus")))

        assertEquals(true, dashboard.shouldSyncCurrentDevice("current"))
    }

    @Test
    fun newInstallationDoesNotAutoAttachWhenAccountAlreadyHasDevices() {
        val dashboard = dashboard(devices = listOf(device("other", "Samsung")))

        assertEquals(false, dashboard.shouldSyncCurrentDevice(""))
    }

    @Test
    fun firstDeviceIsAutomaticallyAttachedToEmptyAccount() {
        assertEquals(true, dashboard(devices = emptyList()).shouldSyncCurrentDevice(""))
    }

    private fun dashboard(devices: List<AccountDevice>) = AccountDashboard(
        account = AccountProfile("account", "user@example.com", "User", false),
        subscription = AccountSubscriptionStatus(
            subscription = AccountSubscription("subscription", "week", "active", "2026-07-24T00:00:00Z", false),
            deviceLimit = 3,
            deviceCount = devices.size,
        ),
        devices = devices,
        payments = emptyList(),
        plans = emptyList(),
    )

    private fun device(id: String, name: String) = AccountDevice(
        id = id,
        name = name,
        status = "active",
        platform = "android",
        bundleReady = true,
        createdAt = id,
    )

    private fun plan(id: String, priceMinor: Long, originalPriceMinor: Long = 0) = AccountPlan(
        id = id,
        title = id,
        durationDays = 3,
        priceMinor = priceMinor,
        originalPriceMinor = originalPriceMinor,
        currency = "RUB",
    )
}
