package shop.whitezia.client.account

import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountSessionManagerTest {
    private val profile = AccountProfile("account", "test@example.org", "Test", false)
    private fun session(version: Int) = AccountSession("access-$version", "refresh-$version", 300, profile)

    private inner class Fixture(initialSession: Boolean = true, rotate: (String) -> AccountSession) {
        val stored = AtomicReference<String?>("refresh-1")
        val rotations = AtomicInteger()
        val saves = AtomicInteger()
        val clears = AtomicInteger()
        val manager = AccountSessionManager(
            readRefreshToken = stored::get,
            saveRefreshToken = { stored.set(it); saves.incrementAndGet() },
            clearRefreshToken = { stored.set(null); clears.incrementAndGet() },
            rotate = { rotations.incrementAndGet(); rotate(it) },
        )

        init {
            if (initialSession) manager.apply(session(1), manager.snapshot())
            saves.set(0)
        }
    }

    private class Worker<T>(block: () -> T) {
        val task = FutureTask<T> { block() }
        val thread = Thread(task).apply { isDaemon = true; start() }
        fun result(): T = task.get(5, TimeUnit.SECONDS)
        fun failure(): Throwable = try {
            result()
            throw AssertionError("Expected failure")
        } catch (error: ExecutionException) {
            checkNotNull(error.cause)
        }

        fun awaitWaiting() {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (thread.state != Thread.State.WAITING && System.nanoTime() < deadline) {
                Thread.yield()
            }
            assertEquals("Waiter must join the refresh flight", Thread.State.WAITING, thread.state)
        }
    }

    private fun await(latch: CountDownLatch) = assertTrue(latch.await(5, TimeUnit.SECONDS))

    @Test fun concurrent401sRotateOnceAndBothReplayWithTheNewToken() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val fixture = Fixture { token ->
            assertEquals("refresh-1", token)
            entered.countDown()
            await(release)
            session(2)
        }
        fun request() = fixture.manager.withAccess { token ->
            if (token == "access-1") throw AccountApiException(401, "expired")
            token
        }
        val owner = Worker { request() }
        await(entered)
        val waiter = Worker { request() }
        try {
            waiter.awaitWaiting()
            assertEquals(1, fixture.rotations.get())
        } finally {
            release.countDown()
        }
        assertEquals("access-2", owner.result())
        assertEquals("access-2", waiter.result())
        assertEquals("refresh-2", fixture.stored.get())
        assertEquals(1, fixture.rotations.get())
        assertEquals(1, fixture.saves.get())
        assertEquals(0, fixture.clears.get())
    }

    @Test fun late401ReusesTheAlreadyRotatedSession() {
        val fixture = Fixture { session(2) }
        val lateRequestStarted = CountDownLatch(1)
        val release = CountDownLatch(1)
        val late = Worker {
            fixture.manager.withAccess { token ->
                if (token == "access-1") {
                    lateRequestStarted.countDown()
                    await(release)
                    throw AccountApiException(401, "expired")
                }
                token
            }
        }
        await(lateRequestStarted)
        try {
            fixture.manager.refresh(fixture.manager.snapshot())
        } finally {
            release.countDown()
        }
        assertEquals("access-2", late.result())
        assertEquals(1, fixture.rotations.get())
        assertEquals("refresh-2", fixture.stored.get())
    }

    @Test fun restoreAndUnauthorizedRequestShareTheSameFlight() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val fixture = Fixture {
            entered.countDown()
            await(release)
            session(2)
        }
        val owner = Worker { fixture.manager.restore() }
        await(entered)
        val waiter = Worker {
            fixture.manager.withAccess { token ->
                if (token == "access-1") throw AccountApiException(401, "expired")
                token
            }
        }
        try { waiter.awaitWaiting() } finally { release.countDown() }
        assertEquals(profile, owner.result())
        assertEquals("access-2", waiter.result())
        assertEquals(1, fixture.rotations.get())
    }

    @Test fun concurrentColdRestoresRotateOnce() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val fixture = Fixture(initialSession = false) {
            entered.countDown()
            await(release)
            session(2)
        }
        val owner = Worker { fixture.manager.restore() }
        await(entered)
        val waiter = Worker { fixture.manager.restore() }
        try { waiter.awaitWaiting() } finally { release.countDown() }
        assertEquals(profile, owner.result())
        assertEquals(profile, waiter.result())
        assertEquals(1, fixture.rotations.get())
    }

    @Test fun networkFailureIsSharedAndRetainsCredentialsForRetry() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val failure = IOException("offline")
        var offline = true
        val fixture = Fixture {
            if (offline) {
                entered.countDown()
                await(release)
                throw failure
            }
            session(2)
        }
        val snapshot = fixture.manager.snapshot()
        val owner = Worker { fixture.manager.refresh(snapshot) }
        await(entered)
        val waiter = Worker { fixture.manager.refresh(snapshot) }
        try { waiter.awaitWaiting() } finally { release.countDown() }
        assertSame(failure, owner.failure())
        assertSame(failure, waiter.failure())
        assertEquals(1, fixture.rotations.get())
        assertEquals("refresh-1", fixture.stored.get())
        assertEquals(0, fixture.clears.get())
        offline = false
        assertEquals(session(2), fixture.manager.refresh(snapshot))
        assertEquals(2, fixture.rotations.get())
    }

    @Test fun invalidRefreshClearsOnlyTheCurrentSessionAndIsNotRetriedWithTheOldToken() {
        val fixture = Fixture { throw AccountApiException(401, "invalid or expired session") }
        val snapshot = fixture.manager.snapshot()
        assertNull(fixture.manager.restore())
        try {
            fixture.manager.refresh(snapshot)
            throw AssertionError("Invalid session was refreshed again")
        } catch (error: AccountApiException) {
            assertEquals(401, error.statusCode)
        }
        assertNull(fixture.stored.get())
        assertEquals("", fixture.manager.snapshot().accessToken)
        assertEquals(1, fixture.rotations.get())
        assertEquals(1, fixture.clears.get())
    }

    @Test fun concurrentInvalidRefreshSharesTheFailureAndClearsOnce() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val failure = AccountApiException(401, "invalid or expired session")
        val fixture = Fixture {
            entered.countDown()
            await(release)
            throw failure
        }
        val snapshot = fixture.manager.snapshot()
        val owner = Worker { fixture.manager.refresh(snapshot) }
        await(entered)
        val waiter = Worker { fixture.manager.refresh(snapshot) }
        try { waiter.awaitWaiting() } finally { release.countDown() }
        assertSame(failure, owner.failure())
        assertSame(failure, waiter.failure())
        assertEquals(1, fixture.rotations.get())
        assertEquals(1, fixture.clears.get())
        assertNull(fixture.stored.get())
    }

    @Test fun logoutRejectsOwnerAndWaiterWithoutBlockingOnNetwork() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val fixture = Fixture {
            entered.countDown()
            await(release)
            session(2)
        }
        val snapshot = fixture.manager.snapshot()
        val owner = Worker { fixture.manager.refresh(snapshot) }
        await(entered)
        val waiter = Worker { fixture.manager.refresh(snapshot) }
        try {
            waiter.awaitWaiting()
            assertEquals("refresh-1", fixture.manager.clear())
            assertNull(fixture.stored.get())
        } finally {
            release.countDown()
        }
        assertTrue(owner.failure() is CancellationException)
        assertTrue(waiter.failure() is CancellationException)
        assertEquals(0, fixture.saves.get())
        assertNull(fixture.stored.get())
    }

    @Test fun failedOldRefreshCannotClearNewLoginAfterLogout() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val fixture = Fixture {
            entered.countDown()
            await(release)
            throw AccountApiException(401, "invalid or expired session")
        }
        val owner = Worker { fixture.manager.refresh(fixture.manager.snapshot()) }
        await(entered)
        try {
            fixture.manager.clear()
            fixture.manager.apply(session(3), fixture.manager.snapshot())
        } finally {
            release.countDown()
        }
        assertTrue(owner.failure() is CancellationException)
        assertEquals("refresh-3", fixture.stored.get())
        assertEquals("access-3", fixture.manager.snapshot().accessToken)
        assertEquals(1, fixture.clears.get())
    }

    @Test fun successfulOldRefreshCannotOverwriteNewLoginAfterLogout() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val fixture = Fixture {
            entered.countDown()
            await(release)
            session(2)
        }
        val owner = Worker { fixture.manager.refresh(fixture.manager.snapshot()) }
        await(entered)
        try {
            fixture.manager.clear()
            fixture.manager.apply(session(3), fixture.manager.snapshot())
        } finally {
            release.countDown()
        }
        assertTrue(owner.failure() is CancellationException)
        assertEquals("refresh-3", fixture.stored.get())
        assertEquals("access-3", fixture.manager.snapshot().accessToken)
        assertEquals(1, fixture.saves.get())
    }

    @Test fun lateLoginAfterLogoutIsRejected() {
        val fixture = Fixture { session(2) }
        val login = fixture.manager.snapshot()
        fixture.manager.clear()
        try {
            fixture.manager.apply(session(2), login)
            throw AssertionError("Late login restored the session")
        } catch (_: CancellationException) {
            assertNull(fixture.stored.get())
            assertEquals(0, fixture.saves.get())
        }
    }

    @Test fun second401IsNotRetriedAndNon401DoesNotRotate() {
        val fixture = Fixture { session(2) }
        val calls = AtomicInteger()
        try {
            fixture.manager.withAccess { calls.incrementAndGet(); throw AccountApiException(401, "expired") }
            throw AssertionError("Expected unauthorized")
        } catch (error: AccountApiException) {
            assertEquals(401, error.statusCode)
        }
        assertEquals(2, calls.get())
        assertEquals(1, fixture.rotations.get())
        try {
            fixture.manager.withAccess { throw AccountApiException(503, "unavailable") }
            throw AssertionError("Expected unavailable")
        } catch (error: AccountApiException) {
            assertEquals(503, error.statusCode)
        }
        assertEquals(1, fixture.rotations.get())
    }
}
