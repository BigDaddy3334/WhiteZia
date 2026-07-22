package shop.whitezia.client.controlplane

import java.io.IOException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ControlPlaneTransportTest {
    @Test
    fun retriesNetworkFailuresThroughBootstrap() {
        assertTrue(isDefaultBootstrapRetry(IOException("network unavailable")))
    }

    @Test
    fun retriesOnlyServerSideHttpFailuresThroughBootstrap() {
        assertTrue(isDefaultBootstrapRetry(ControlPlaneHttpStatusException(502, "bad gateway")))
        assertTrue(isDefaultBootstrapRetry(ControlPlaneHttpStatusException(503, "unavailable")))
        assertTrue(isDefaultBootstrapRetry(ControlPlaneHttpStatusException(403, "network block page")))
        assertTrue(isDefaultBootstrapRetry(ControlPlaneHttpStatusException(451, "network restriction")))
        assertFalse(isDefaultBootstrapRetry(ControlPlaneHttpStatusException(404, "not found")))
        assertFalse(isDefaultBootstrapRetry(ControlPlaneHttpStatusException(401, "unauthorized")))
    }

    @Test
    fun doesNotRetryApplicationValidationFailures() {
        assertFalse(isDefaultBootstrapRetry(IllegalArgumentException("invalid response")))
    }
}
