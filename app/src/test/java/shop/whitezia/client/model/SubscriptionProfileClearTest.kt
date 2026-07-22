package shop.whitezia.client.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionProfileClearTest {
    @Test
    fun clearActiveSubscriptionProfileRemovesOnlySelectedImportedProfileAndCredentials() {
        val accountProfile = ConnectionProfile(
            id = "profile-imported-account",
            name = "WhiteZia",
            customServerDomain = "account.example.com",
            customServerEncryptionKey = "account-secret",
        )
        val savedManualProfile = ConnectionProfile(
            id = "profile-imported-manual",
            name = "Manual",
            customServerDomain = "manual.example.com",
            customServerEncryptionKey = "manual-secret",
        )
        val settings = WhiteZiaSettings(
            selectedConnectionProfileId = accountProfile.id,
            connectionProfiles = listOf(ConnectionProfile.defaultProfile(), accountProfile, savedManualProfile),
            subscriptionLink = "stormbundle://account",
            amneziaWgConfig = "awg-secret",
            xrayUri = "vless://xray-secret",
            xrayDailyLimitBytes = 5L,
        ).syncSelectedConnectionProfileFields()

        val cleared = settings.clearActiveSubscriptionProfile()

        assertEquals(ConnectionProfile.DefaultId, cleared.selectedConnectionProfileId)
        assertFalse(cleared.connectionProfiles.any { it.id == accountProfile.id })
        assertTrue(cleared.connectionProfiles.any { it.id == savedManualProfile.id })
        assertEquals("", cleared.subscriptionLink)
        assertEquals("", cleared.customServerDomain)
        assertEquals("", cleared.customServerEncryptionKey)
        assertEquals("", cleared.amneziaWgConfig)
        assertEquals("", cleared.xrayUri)
        assertEquals(0L, cleared.xrayDailyLimitBytes)
    }
}
