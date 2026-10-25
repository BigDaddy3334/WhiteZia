package shop.whitezia.client.runtime

import java.io.File
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class ExternalProcessRunnerTest {
    @Test fun stopIsIdempotentAndCleansOwnedLaunch() {
        val cleaned = CopyOnWriteArrayList<String>()
        val ready = CountDownLatch(1)
        val runner = runner(cleaned)
        try {
            runner.start({ "sleep" }) { if (it == "fixture-ready") ready.countDown() }
            assertTrue(ready.await(5, TimeUnit.SECONDS))
            assertTrue(runner.isRunning())
            runner.stop()
            runner.stop()
            assertFalse(runner.isRunning())
            assertEquals(listOf("sleep"), cleaned.toList())
        } finally { runner.stop() }
    }

    @Test fun startRetiresPreviousProcessBeforePreparingReplacement() {
        val cleaned = CopyOnWriteArrayList<String>()
        val runner = runner(cleaned)
        try {
            runner.start({ "sleep" }) {}
            runner.start({ assertEquals(listOf("sleep"), cleaned.toList()); "replacement" }) {}
            assertTrue(runner.isRunning())
            runner.stop()
            assertEquals(listOf("sleep", "replacement"), cleaned.toList())
        } finally { runner.stop() }
    }

    @Test fun failedProcessStartRemovesPreparedConfig() {
        val cleaned = mutableListOf<String>()
        val runner = ExternalProcessRunner<String>("Fixture", startProcess = { throw IOException("cannot execute") }, cleanup = { cleaned += it })
        assertThrows(IOException::class.java) { runner.start({ "config" }) {} }
        assertFalse(runner.isRunning())
        assertEquals(listOf("config"), cleaned)
    }

    private fun runner(cleaned: MutableList<String>) = ExternalProcessRunner<String>(
        name = "Fixture",
        startProcess = { mode ->
            val classpath = listOf(ExternalProcessFixture::class.java, kotlin.Unit::class.java)
                .joinToString(File.pathSeparator) { File(it.protectionDomain.codeSource.location.toURI()).path }
            ProcessBuilder(File(System.getProperty("java.home"), "bin/java").path, "-cp", classpath,
                ExternalProcessFixture::class.java.name, mode).redirectErrorStream(true).start()
        },
        cleanup = { cleaned += it },
    )
}

object ExternalProcessFixture {
    @JvmStatic fun main(args: Array<String>) {
        System.out.println("fixture-ready")
        System.out.flush()
        Thread.sleep(30_000L)
    }
}
