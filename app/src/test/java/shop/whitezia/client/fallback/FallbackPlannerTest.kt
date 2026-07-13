package shop.whitezia.client.fallback

import org.junit.Assert.assertEquals
import org.junit.Test

class FallbackPlannerTest {
    @Test
    fun `amnezia failure starts xray on validated mobile network`() {
        val action = FallbackPlanner.planAfterAmneziaUnavailable(
            hasXray = true,
            network = FallbackNetworkState(activeWifi = false, mobileAvailable = true),
        )

        assertEquals(FallbackPlanAction.StartXray, action)
    }

    @Test
    fun `amnezia failure waits before xray when wifi is active`() {
        val action = FallbackPlanner.planAfterAmneziaUnavailable(
            hasXray = true,
            network = FallbackNetworkState(activeWifi = true, mobileAvailable = true),
        )

        assertEquals(FallbackPlanAction.WaitForMobileForXray, action)
    }

    @Test
    fun `amnezia failure waits before xray when mobile network is unavailable`() {
        val action = FallbackPlanner.planAfterAmneziaUnavailable(
            hasXray = true,
            network = FallbackNetworkState(activeWifi = false, mobileAvailable = false),
        )

        assertEquals(FallbackPlanAction.WaitForMobileForXray, action)
    }

    @Test
    fun `amnezia failure falls back to dns when xray is missing and wifi is off`() {
        val action = FallbackPlanner.planAfterAmneziaUnavailable(
            hasXray = false,
            network = FallbackNetworkState(activeWifi = false, mobileAvailable = true),
        )

        assertEquals(FallbackPlanAction.StartDns, action)
    }

    @Test
    fun `amnezia failure waits for wifi off before dns when xray is missing`() {
        val action = FallbackPlanner.planAfterAmneziaUnavailable(
            hasXray = false,
            network = FallbackNetworkState(activeWifi = true, mobileAvailable = true),
        )

        assertEquals(FallbackPlanAction.WaitForWifiOffForDns, action)
    }

    @Test
    fun `xray fallback without xray can continue to dns when allowed`() {
        val action = FallbackPlanner.planXrayFallback(
            hasXray = false,
            network = FallbackNetworkState(activeWifi = false, mobileAvailable = false),
            allowDnsFallback = true,
        )

        assertEquals(FallbackPlanAction.StartDns, action)
    }

    @Test
    fun `manual xray only requires an available mobile network`() {
        val action = FallbackPlanner.planManualXrayOnly(
            network = FallbackNetworkState(activeWifi = false, mobileAvailable = false),
        )

        assertEquals(FallbackPlanAction.WaitForMobileForXray, action)
    }

    @Test
    fun `manual xray starts when wifi is off and mobile network is available`() {
        val action = FallbackPlanner.planManualXrayOnly(
            network = FallbackNetworkState(activeWifi = false, mobileAvailable = true),
        )

        assertEquals(FallbackPlanAction.StartXray, action)
    }
}
