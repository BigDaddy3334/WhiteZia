package shop.whitezia.client.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SecureAccountStoreTest {
    @Test
    fun stableIdentityIsDeterministic() {
        assertEquals(
            deriveStableInstallationId("abcdef0123456789"),
            deriveStableInstallationId(" ABCDEF0123456789 "),
        )
    }

    @Test
    fun differentAndroidIdsProduceDifferentIdentities() {
        assertNotEquals(
            deriveStableInstallationId("abcdef0123456789"),
            deriveStableInstallationId("abcdef0123456780"),
        )
    }

    @Test
    fun missingOrBrokenAndroidIdIsRejected() {
        assertNull(deriveStableInstallationId(null))
        assertNull(deriveStableInstallationId(""))
        assertNull(deriveStableInstallationId("9774d56d682e549c"))
    }

    @Test
    fun bundleFingerprintChangesOnlyWhenBundleChanges() {
        assertEquals(bundleFingerprint("stormbundle://first"), bundleFingerprint("stormbundle://first"))
        assertNotEquals(bundleFingerprint("stormbundle://first"), bundleFingerprint("stormbundle://second"))
    }
}
