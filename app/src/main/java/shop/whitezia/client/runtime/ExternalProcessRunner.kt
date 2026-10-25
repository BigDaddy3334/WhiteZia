package shop.whitezia.client.runtime

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** Serializes startup/teardown and owns the process, config and output reader. */
internal class ExternalProcessRunner<Spec>(
    private val name: String,
    private val startProcess: (Spec) -> Process,
    private val cleanup: (Spec) -> Unit,
    private val shouldForwardOutput: (String) -> Boolean = String::isNotBlank,
    private val preparedMessage: String = "$name runtime prepared",
) {
    private val operationLock = Any()
    private val stateLock = Any()
    private var process: Process? = null
    private var spec: Spec? = null
    private var outputReader: Thread? = null

    fun start(prepare: () -> Spec, onOutput: (String) -> Unit): Spec = synchronized(operationLock) {
        stop()
        val launch = prepare()
        try {
            onOutput(preparedMessage)
            val started = startProcess(launch)
            val reader = thread(name = "${name.lowercase()}-output", isDaemon = true, start = false) {
                try {
                    started.inputStream.bufferedReader().useLines { lines ->
                        lines.filter(shouldForwardOutput).forEach(onOutput)
                    }
                } catch (_: IOException) {
                    // Closing a process stream during Stop is expected.
                } finally {
                    retireExitedProcess(started)
                }
            }
            synchronized(stateLock) {
                process = started
                spec = launch
                outputReader = reader
            }
            reader.start()
            onOutput("$name process started")
            launch
        } catch (error: Exception) {
            // If the process was installed in state, normal teardown owns cleanup.
            if (synchronized(stateLock) { spec === launch }) stop() else cleanup(launch)
            throw error
        }
    }

    fun stop(gracePeriodMillis: Long = 1_500L): Unit = synchronized(operationLock) {
        val active: Process
        val reader: Thread?
        synchronized(stateLock) {
            active = process ?: return
            reader = outputReader
        }
        active.destroy()
        try {
            active.waitFor(gracePeriodMillis, TimeUnit.MILLISECONDS)
            if (active.isAlive) {
                active.destroyForcibly()
                active.waitFor(gracePeriodMillis, TimeUnit.MILLISECONDS)
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        check(!active.isAlive) { "$name process did not stop" }
        retireExitedProcess(active)
        if (reader != null && reader !== Thread.currentThread()) {
            try { reader.join(OutputDrainJoinMillis) } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
    }

    fun isRunning(): Boolean = synchronized(stateLock) { process?.isAlive == true }

    fun exitCodeOrNull(): Int? = synchronized(stateLock) {
        val active = process ?: return@synchronized null
        if (active.isAlive) return@synchronized null
        active.exitValue().also { retireExitedProcess(active) }
    }

    fun cleanupLaunchFiles() = synchronized(operationLock) {
        synchronized(stateLock) {
            check(process?.isAlive != true) { "Cannot clean files for a running $name process" }
            cleanupSpecLocked()
        }
    }

    private fun retireExitedProcess(finished: Process) = synchronized(stateLock) {
        if (process !== finished || finished.isAlive) return@synchronized
        process = null
        outputReader = null
        cleanupSpecLocked()
    }

    private fun cleanupSpecLocked() {
        spec?.let(cleanup)
        spec = null
    }

    private companion object { const val OutputDrainJoinMillis = 500L }
}
