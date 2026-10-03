package shop.whitezia.client.account

import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountOperationGuardTest {
    @Test fun currentOperationCanSaveItsSession() {
        val guard = AccountOperationGuard()
        assertEquals("session", guard.ifCurrent(guard.snapshot()) { "session" })
    }

    @Test fun logoutRejectsLateLoginAndRefreshResponses() {
        val guard = AccountOperationGuard()
        val login = guard.snapshot()
        val refresh = guard.snapshot()
        var saved = false
        guard.invalidate { saved = false }
        for (generation in listOf(login, refresh)) {
            try {
                guard.ifCurrent(generation) { saved = true }
                throw AssertionError("An old operation restored the session")
            } catch (_: CancellationException) {
                assertFalse(saved)
            }
        }
    }

    @Test fun newLoginAfterLogoutIsAllowed() {
        val guard = AccountOperationGuard()
        guard.invalidate { }
        assertTrue(guard.ifCurrent(guard.snapshot()) { true })
    }

    @Test fun logoutClearsAnAlreadySavedSession() {
        val guard = AccountOperationGuard()
        var token: String? = null
        guard.ifCurrent(guard.snapshot()) { token = "saved" }
        assertEquals("saved", guard.invalidate { token.also { token = null } })
        assertEquals(null, token)
    }
}
