package shop.whitezia.client.runtime

import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.TimeoutException
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class VpnDesiredSessionStoreTest {
    @Test fun activeSessionSurvivesANewStoreInstance() = withStore { directory, store ->
        store.request("session-1")
        store.markReady("session-1")
        assertEquals(VpnDesiredSession("session-1", true), VpnDesiredSessionStore(directory).read())
    }

    @Test fun stopIsDurableAcrossAnotherJvmProcess() = withStore { directory, store ->
        store.request("session-1")
        store.markReady("session-1")
        runChild(directory, "verify-and-stop")
        assertNull(store.read())
    }

    @Test fun oldRecoveryCannotPublishAfterStopOrNewSession() = withStore { _, store ->
        store.request("old")
        store.markReady("old")
        val old = store.read()
        store.clear()
        var published = false
        assertFalse(store.withCurrent(old) { published = true })
        store.request("new")
        assertFalse(store.withCurrent(old) { published = true })
        assertFalse(store.withCurrent(null) { published = true })
        assertFalse(published)
        assertEquals("new", store.read()?.sessionId)
    }

    @Test fun stopAndNewSessionWaitForInFlightRecoveryPublication() = withStore { directory, store ->
        store.request("old")
        store.markReady("old")
        val old = store.read()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val stopping = CountDownLatch(1)
        val events = CopyOnWriteArrayList<String>()
        val executor = Executors.newFixedThreadPool(2)
        try {
            val publication = executor.submit<Boolean> {
                store.withCurrent(old) {
                    entered.countDown()
                    check(release.await(5, TimeUnit.SECONDS))
                    events += "old recovering"
                }
            }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            val replacement = executor.submit {
                val other = VpnDesiredSessionStore(directory)
                stopping.countDown()
                other.clear()
                other.request("new")
                events += "new starting"
            }
            assertTrue(stopping.await(5, TimeUnit.SECONDS))
            assertThrows(TimeoutException::class.java) { replacement.get(150, TimeUnit.MILLISECONDS) }
            release.countDown()
            assertTrue(publication.get(5, TimeUnit.SECONDS))
            replacement.get(5, TimeUnit.SECONDS)
            assertEquals(listOf("old recovering", "new starting"), events.toList())
            assertFalse(store.withCurrent(old) { events += "stale recovering" })
            assertEquals("new", store.read()?.sessionId)
        } finally {
            release.countDown()
            executor.shutdownNow()
            executor.awaitTermination(5, TimeUnit.SECONDS)
        }
    }

    @Test fun reconfigurePreservesReadinessButNewSessionStartsUnready() = withStore { _, store ->
        store.request("session-1")
        store.markReady("session-1")
        assertTrue(store.request("session-1").wasReady)
        assertFalse(store.request("session-2").wasReady)
    }

    @Test fun lateReadinessAndCleanupCannotMutateNewSession() = withStore { _, store ->
        store.request("old")
        store.request("new")
        store.markReady("old")
        store.clear("old")
        assertEquals(VpnDesiredSession("new", false), store.read())
    }

    @Test fun corruptOrOversizedStateCannotAuthorizeRestart() = withStore { directory, store ->
        val state = File(directory, "desired-vpn.json")
        state.writeText("{invalid-json")
        assertNull(store.read())
        state.writeText("x".repeat(4_097))
        assertNull(store.read())
        store.request("replacement")
        assertEquals("replacement", store.read()?.sessionId)
    }

    @Test fun unknownSchemaAndEmptyIdsFailClosed() {
        assertNull(decodeDesiredVpnSession(JSONObject().put("schemaVersion", 2).put("sessionId", "session")))
        assertNull(decodeDesiredVpnSession(JSONObject().put("schemaVersion", 1).put("sessionId", "")))
        assertNull(decodeDesiredVpnSession(JSONObject().put("schemaVersion", 1).put("sessionId", "../escape")))
    }

    @Test fun queuedStopForOldSessionCannotCancelNewActiveSession() {
        assertFalse(acceptsVpnStop("new", "old", "new"))
        assertFalse(acceptsVpnStop("new", "", "new"))
        assertTrue(acceptsVpnStop("new", "new", "new"))
    }

    @Test fun stopOldRuntimePreservesNewQueuedStart() {
        assertTrue(acceptsVpnStop("old", "old", "new"))
        assertFalse(acceptsVpnStop("", "", "new"))
        assertFalse(acceptsVpnStop("", "old", "new"))
    }

    @Test fun revocationCleansItsRuntimeButPreservesNewSessionOwnership() = withStore { _, store ->
        assertTrue(acceptsVpnRevocation("", "old", "old")) // Bound-only recreation, before RESTORE delivery.
        assertFalse(acceptsVpnRevocation("", "old", "new"))
        assertFalse(acceptsVpnRevocation("new", "old", "new"))
        store.request("new")
        assertTrue(acceptsVpnRevocation("old", "old", store.read()?.sessionId))
        store.clear("old") // Stop the old native runtime without clearing a queued START.
        assertEquals("new", store.read()?.sessionId)
        assertTrue(acceptsVpnRevocation("old", "old", null))
    }

    @Test fun cancellingQueuedStartAlsoStopsAnyOldRuntime() {
        assertTrue(acceptsVpnStop("old", "new", null))
        assertTrue(acceptsVpnStop("", "new", null))
    }

    @Test fun rejectedSessionIdDoesNotReplaceDesiredSession() = withStore { _, store ->
        store.request("valid")
        assertThrows(IllegalArgumentException::class.java) { store.request("../escape") }
        assertEquals("valid", store.read()?.sessionId)
    }

    @Test fun emptyStopCannotClearANewlyWrittenSession() = withStore { _, store ->
        assertNull(store.read())
        store.request("new-start")
        store.clear("")
        assertEquals("new-start", store.read()?.sessionId)
    }

    @Test fun longLivedActiveSnapshotSurvivesAgeAndCountCleanupUntilStop() = withStore { directory, store ->
        val now = System.currentTimeMillis()
        val day = 24L * 60L * 60L * 1_000L
        fun snapshot(id: String, age: Long): File = File(directory, "$id.json").apply {
            writeText("{}")
            check(setLastModified(now - age))
        }
        store.request("active")
        val active = snapshot("active", 8 * day)
        val fresh = List(65) { snapshot("request-$it", 1_000L + it) }
        val files = fresh + active
        val pruned = runtimeLaunchRequestsToPrune(files, store.read()?.sessionId, now)
        assertEquals(listOf(fresh.last()), pruned)
        assertFalse(pruned.contains(active))
        store.clear()
        assertTrue(runtimeLaunchRequestsToPrune(files, store.read()?.sessionId, now).contains(active))
    }

    @Test fun inactiveSnapshotAgeBoundaryKeepsFreshAndExpiresOnlyOlderThanSevenDays() = withStore { directory, _ ->
        val now = System.currentTimeMillis()
        val retention = 7L * 24L * 60L * 60L * 1_000L
        val boundary = File(directory, "boundary.json").apply { writeText("{}"); check(setLastModified(now - retention)) }
        val expired = File(directory, "expired.json").apply { writeText("{}"); check(setLastModified(now - retention - 1)) }
        assertEquals(listOf(expired), runtimeLaunchRequestsToPrune(listOf(boundary, expired), null, now))
    }

    private fun withStore(block: (File, VpnDesiredSessionStore) -> Unit) {
        val directory = Files.createTempDirectory("whitezia-desired-vpn-").toFile()
        try { block(directory, VpnDesiredSessionStore(directory)) } finally {
            directory.listFiles().orEmpty().forEach { it.delete() }
            directory.delete()
        }
    }

    private fun runChild(directory: File, command: String) {
        val classpath = listOf(VpnDesiredSessionFixture::class.java, VpnDesiredSessionStore::class.java,
            kotlin.Unit::class.java, JSONObject::class.java)
            .map { File(checkNotNull(it.protectionDomain).codeSource.location.toURI()).path }.distinct().joinToString(File.pathSeparator)
        val process = ProcessBuilder(File(System.getProperty("java.home"), "bin/java").path, "-cp", classpath,
            VpnDesiredSessionFixture::class.java.name, directory.path, command).redirectErrorStream(true).start()
        try {
            assertTrue(process.waitFor(10, TimeUnit.SECONDS))
            assertEquals(process.inputStream.bufferedReader().readText(), 0, process.exitValue())
        } finally { if (process.isAlive) process.destroyForcibly() }
    }
}

object VpnDesiredSessionFixture {
    @JvmStatic fun main(args: Array<String>) {
        val store = VpnDesiredSessionStore(File(args[0]))
        check(store.read() == VpnDesiredSession("session-1", true))
        check(args[1] == "verify-and-stop")
        store.clear()
        check(store.read() == null)
    }
}
