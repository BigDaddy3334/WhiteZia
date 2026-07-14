package shop.whitezia.client.fallback

import org.junit.Assert.assertEquals
import org.junit.Test

class HealthCheckFallbackPolicyTest {
    @Test
    fun awgHealthFailurePrefersXrayWhenAvailable() {
        val action = FallbackPlanner.planAfterHealthCheckFailure(
            failedTransport = FallbackTransport.AmneziaWg,
            hasXray = true,
            allowDnsFallback = true,
        )

        assertEquals(HealthCheckFallbackAction.StartXray, action)
    }

    @Test
    fun awgHealthFailureUsesDnsWhenXrayIsMissing() {
        val action = FallbackPlanner.planAfterHealthCheckFailure(
            failedTransport = FallbackTransport.AmneziaWg,
            hasXray = false,
            allowDnsFallback = true,
        )

        assertEquals(HealthCheckFallbackAction.StartDns, action)
    }

    @Test
    fun xrayHealthFailureUsesDnsOnlyInAutomaticChain() {
        val automaticAction = FallbackPlanner.planAfterHealthCheckFailure(
            failedTransport = FallbackTransport.Xray,
            hasXray = true,
            allowDnsFallback = true,
        )
        val manualAction = FallbackPlanner.planAfterHealthCheckFailure(
            failedTransport = FallbackTransport.Xray,
            hasXray = true,
            allowDnsFallback = false,
        )

        assertEquals(HealthCheckFallbackAction.StartDns, automaticAction)
        assertEquals(HealthCheckFallbackAction.Stop, manualAction)
    }

    @Test
    fun dnsHealthFailureStopsInsteadOfLoopingBackToAnotherTransport() {
        val action = FallbackPlanner.planAfterHealthCheckFailure(
            failedTransport = FallbackTransport.StormDns,
            hasXray = true,
            allowDnsFallback = true,
        )

        assertEquals(HealthCheckFallbackAction.Stop, action)
    }
}
