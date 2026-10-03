package shop.whitezia.client.account

import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException

internal class AccountSessionManager(
    private val readRefreshToken: () -> String?,
    private val saveRefreshToken: (String) -> Unit,
    private val clearRefreshToken: () -> Unit,
    private val rotate: (String) -> AccountSession,
) {
    private val guard = AccountOperationGuard()
    private var revision = 0L
    private var session: AccountSession? = null
    private var flight: RefreshFlight? = null

    data class Snapshot(val generation: Long, val revision: Long, val accessToken: String)

    private class RefreshFlight(val revision: Long, val token: String) {
        val result = CompletableFuture<AccountSession>()
    }

    fun snapshot(): Snapshot {
        val generation = guard.snapshot()
        return guard.ifCurrent(generation) {
            Snapshot(generation, revision, session?.accessToken.orEmpty())
        }
    }

    fun <T> ifCurrent(snapshot: Snapshot, block: () -> T): T =
        guard.ifCurrent(snapshot.generation, block)

    fun account(): AccountProfile? = ifCurrent(snapshot()) { session?.account }

    fun updateAccount(snapshot: Snapshot, account: AccountProfile) = ifCurrent(snapshot) {
        session = session?.copy(account = account)
    }

    fun apply(session: AccountSession, snapshot: Snapshot): AccountSession = ifCurrent(snapshot) {
        saveRefreshToken(session.refreshToken)
        this.session = session
        revision += 1
        session
    }

    fun clear(): String? = guard.invalidate {
        val token = readRefreshToken()
        clearRefreshToken()
        session = null
        revision += 1
        flight = null
        token
    }

    fun restore(): AccountProfile? {
        val snapshot = snapshot()
        if (ifCurrent(snapshot) { readRefreshToken() } == null) return null
        return try {
            refresh(snapshot).account
        } catch (error: AccountApiException) {
            if (error.statusCode != 401) throw error
            null
        }
    }

    fun refresh(snapshot: Snapshot): AccountSession {
        var owner = false
        var reused: AccountSession? = null
        val pending = ifCurrent(snapshot) {
            // A late 401 from an older access token must reuse the rotated session.
            if (revision != snapshot.revision) {
                reused = session ?: throw AccountApiException(401, "invalid or expired session")
                null
            } else flight?.takeIf { it.revision == revision } ?: RefreshFlight(
                revision,
                readRefreshToken() ?: throw AccountApiException(401, "invalid or expired session"),
            ).also {
                flight = it
                owner = true
            }
        }
        if (pending == null) return ifCurrent(snapshot) { checkNotNull(reused) }
        if (owner) {
            try {
                val rotated = rotate(pending.token)
                val applied = ifCurrent(snapshot) {
                    if (revision == pending.revision) apply(rotated, snapshot)
                    else session ?: throw AccountApiException(401, "invalid or expired session")
                }
                pending.result.complete(applied)
            } catch (error: Throwable) {
                val failure = try {
                    ifCurrent(snapshot) {
                        if (revision == pending.revision && error is AccountApiException && error.statusCode == 401) {
                            clearRefreshToken()
                            session = null
                            revision += 1
                        }
                        error
                    }
                } catch (stale: Throwable) {
                    stale
                }
                pending.result.completeExceptionally(failure)
            } finally {
                // Logout may already have started a flight for a newer generation.
                synchronized(guard) {
                    if (flight === pending) flight = null
                }
            }
        }
        val result = try {
            pending.result.get()
        } catch (error: ExecutionException) {
            throw error.cause ?: error
        }
        return ifCurrent(snapshot) { result }
    }

    fun <T> withAccess(block: (String) -> T): T {
        val snapshot = snapshot()
        check(snapshot.accessToken.isNotBlank()) { "Сессия завершена. Войдите снова" }
        return try {
            val result = block(snapshot.accessToken)
            ifCurrent(snapshot) { result }
        } catch (error: AccountApiException) {
            if (error.statusCode != 401) throw error
            val refreshed = refresh(snapshot)
            val result = block(refreshed.accessToken)
            ifCurrent(snapshot) { result }
        }
    }
}
