package shop.whitezia.client.runtime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionHealthPolicyTest {
    @Test fun regionalSiteAloneDoesNotVerifyXrayRelay() {
        assertFalse(HealthProbeSummary(1, 1, 0).isHealthy(requireRelayEgress = true))
        assertFalse(HealthProbeSummary(2, 1, 0).isHealthy(requireRelayEgress = true))
    }

    @Test fun relayAndIndependentResponseVerifyXray() {
        assertTrue(HealthProbeSummary(2, 1, 1).isHealthy(requireRelayEgress = true))
        assertFalse(HealthProbeSummary(1, 1, 1).isHealthy(requireRelayEgress = true))
    }

    @Test fun explicitCustomKeepsLegacySingleEndpointAcceptance() {
        assertTrue(HealthProbeSummary(1, 1, 0).isHealthy(requireRelayEgress = false))
        assertFalse(HealthProbeSummary(0, 0, 0).isHealthy(requireRelayEgress = false))
    }

    @Test fun automaticDnsRejectsTheObservedSingle204AndTwo204OnlyFalsePositive() {
        assertFalse(HealthProbeSummary(1, 0).isHealthy(false, requireDnsContent = true))
        assertFalse(HealthProbeSummary(2, 0, 1).isHealthy(false, requireDnsContent = true))
    }

    @Test fun automaticDnsRequiresIndependentSuccessAndAContentEndpoint() {
        assertFalse(HealthProbeSummary(1, 1).isHealthy(false, requireDnsContent = true))
        assertTrue(HealthProbeSummary(2, 1).isHealthy(false, requireDnsContent = true))
        assertTrue(HealthProbeSummary(2, 2).isHealthy(false, requireDnsContent = true))
    }

    @Test fun portalPageIsNotAConnectivity204Response() {
        assertFalse(acceptsHealthResponse(HealthResponseKind.Connectivity204, 200, "sign in"))
        assertFalse(acceptsHealthResponse(HealthResponseKind.Connectivity204, 302))
        assertTrue(acceptsHealthResponse(HealthResponseKind.Connectivity204, 204))
    }

    @Test fun traceRequiresBodyWithAnAddress() {
        assertFalse(acceptsHealthResponse(HealthResponseKind.CloudflareTrace, 200, "maintenance"))
        assertFalse(acceptsHealthResponse(HealthResponseKind.CloudflareTrace, 200, "ip=not-an-address"))
        assertFalse(acceptsHealthResponse(HealthResponseKind.CloudflareTrace, 200, "ip=999.1.1.1"))
        assertTrue(acceptsHealthResponse(HealthResponseKind.CloudflareTrace, 200, "fl=abc\nip=192.0.2.1\n"))
        assertTrue(acceptsHealthResponse(HealthResponseKind.CloudflareTrace, 200, "ip=2001:db8::1\n"))
    }
}
