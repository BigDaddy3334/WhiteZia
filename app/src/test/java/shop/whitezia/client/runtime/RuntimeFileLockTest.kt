package shop.whitezia.client.runtime

import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.OverlappingFileLockException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RuntimeFileLockTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test(timeout = 20_000) fun serializesReadModifyWriteAcrossThreads() {
        val lockFile = File(temporaryFolder.root, "threads.lock")
        val counter = File(temporaryFolder.root, "counter")
        counter.writeText("0")
        val workers = Executors.newFixedThreadPool(6)
        val start = CountDownLatch(1)
        try {
            val tasks = (0 until 6).map {
                workers.submit {
                    start.await()
                    repeat(100) { increment(lockFile, counter) }
                }
            }
            start.countDown()
            tasks.forEach { it.get(10, TimeUnit.SECONDS) }
            assertEquals("600", counter.readText())
        } finally {
            workers.shutdownNow()
        }
    }

    @Test fun canonicalAliasesAreReentrantWithoutReleasingOuterFileLock() {
        val lockFile = File(temporaryFolder.root, "nested/runtime.lock")
        RuntimeFileLock.withLock(lockFile) {
            val alias = File(lockFile.parentFile, "../nested/runtime.lock")
            assertEquals(42, RuntimeFileLock.withLock(alias) { 42 })
            RandomAccessFile(lockFile, "rw").use { file ->
                try {
                    file.channel.tryLock()?.use { fail("Outer interprocess lock was released") }
                    fail("Expected an overlapping lock in this JVM")
                } catch (_: OverlappingFileLockException) {
                    // The nested operation must leave the outer OS lock intact.
                }
            }
        }
        RandomAccessFile(lockFile, "rw").use { file ->
            file.channel.tryLock().use { assertNotNull(it) }
        }
        assertTrue(lockFile.isFile)
    }

    @Test(timeout = 10_000) fun exceptionsReleaseBothLocks() {
        val lockFile = File(temporaryFolder.root, "exception.lock")
        try {
            RuntimeFileLock.withLock(lockFile) { throw IllegalStateException("expected") }
            fail("Operation failure should propagate")
        } catch (error: IllegalStateException) {
            assertEquals("expected", error.message)
        }
        val worker = Executors.newSingleThreadExecutor()
        try {
            assertEquals(7, worker.submit<Int> { RuntimeFileLock.withLock(lockFile) { 7 } }
                .get(5, TimeUnit.SECONDS))
        } finally {
            worker.shutdownNow()
        }
    }

    @Test(timeout = 10_000) fun independentScopesDoNotBlockEachOther() {
        val worker = Executors.newSingleThreadExecutor()
        try {
            RuntimeFileLock.withLock(File(temporaryFolder.root, "vpn.lock")) {
                assertEquals(9, worker.submit<Int> {
                    RuntimeFileLock.withLock(File(temporaryFolder.root, "proxy.lock")) { 9 }
                }.get(5, TimeUnit.SECONDS))
            }
        } finally {
            worker.shutdownNow()
        }
    }

    @Test(timeout = 20_000) fun serializesReadModifyWriteAcrossProcessesAndThreads() {
        val lockFile = File(temporaryFolder.root, "processes.lock")
        val counter = File(temporaryFolder.root, "counter")
        counter.writeText("0")
        val children = (0 until 2).map { index ->
            startProcess(lockFile, counter, File(temporaryFolder.root, "ready-$index"), 100)
        }
        try {
            repeat(100) { increment(lockFile, counter) }
            children.forEach { child ->
                assertTrue("Child process timed out", child.waitFor(10, TimeUnit.SECONDS))
                assertEquals(child.inputStream.bufferedReader().readText(), 0, child.exitValue())
            }
            assertEquals("300", counter.readText())
        } finally {
            children.forEach { it.destroyForcibly() }
        }
    }

    @Test(timeout = 20_000) fun childProcessCannotEnterUntilOuterLockIsReleased() {
        val lockFile = File(temporaryFolder.root, "held.lock")
        val counter = File(temporaryFolder.root, "counter")
        val ready = File(temporaryFolder.root, "ready")
        counter.writeText("0")
        var child: Process? = null
        try {
            RuntimeFileLock.withLock(lockFile) {
                child = startProcess(lockFile, counter, ready, 1)
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
                while (!ready.exists() && System.nanoTime() < deadline && child!!.isAlive) {
                    Thread.sleep(10)
                }
                assertTrue("Child process did not start", ready.exists())
                assertFalse(child!!.waitFor(100, TimeUnit.MILLISECONDS))
                assertEquals("0", counter.readText())
            }
            assertTrue(child!!.waitFor(5, TimeUnit.SECONDS))
            assertEquals(child!!.inputStream.bufferedReader().readText(), 0, child!!.exitValue())
            assertEquals("1", counter.readText())
        } finally {
            child?.destroyForcibly()
        }
    }

    private fun startProcess(lockFile: File, counter: File, ready: File, iterations: Int): Process {
        val classpath = listOf(
            RuntimeFileLockProcessProbe::class.java,
            RuntimeFileLock::class.java,
            Unit::class.java,
        ).map { File(it.protectionDomain.codeSource.location.toURI()).path }
            .distinct().joinToString(File.pathSeparator)
        return ProcessBuilder(
            File(System.getProperty("java.home"), "bin/java").path,
            "-cp", classpath, RuntimeFileLockProcessProbe::class.java.name,
            lockFile.path, counter.path, ready.path, iterations.toString(),
        ).redirectErrorStream(true).start()
    }
}

object RuntimeFileLockProcessProbe {
    @JvmStatic fun main(args: Array<String>) {
        File(args[2]).writeText("ready")
        repeat(args[3].toInt()) { increment(File(args[0]), File(args[1])) }
    }
}

private fun increment(lockFile: File, counter: File) {
    RuntimeFileLock.withLock(lockFile) {
        val previous = counter.readText().toInt()
        Thread.sleep(1)
        counter.writeText((previous + 1).toString())
    }
}
