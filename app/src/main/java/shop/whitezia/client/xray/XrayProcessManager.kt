package shop.whitezia.client.xray

import android.content.Context
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import shop.whitezia.client.model.ResolvedWhiteZiaSettings
import shop.whitezia.client.model.WhiteZiaSettings

data class XrayLaunchSpec(
    val binaryFile: File,
    val workingDirectory: File,
    val configFile: File,
)

class XrayProcessManager(
    private val context: Context,
    private val binaryInstaller: XrayBinaryInstaller = XrayBinaryInstaller(context),
) {

    private val processLock = Any()
    private var process: Process? = null
    private var currentLaunchSpec: XrayLaunchSpec? = null
    private var outputDrainThread: Thread? = null

    fun prepareLaunch(
        settings: WhiteZiaSettings,
        resolvedSettings: ResolvedWhiteZiaSettings,
    ): XrayLaunchSpec {
        val runtimeDir = File(context.noBackupFilesDir, "xray/runtime").apply {
            mkdirs()
        }
        cleanupStaleLaunchFiles(runtimeDir)
        val binaryFile = binaryInstaller.installExecutable()
        val launchId = UUID.randomUUID().toString()
        val configFile = File(runtimeDir, ".wx-$launchId.json")
        configFile.writeText(
            XrayConfigRenderer.renderClientJson(
                xrayUri = settings.xrayUri,
                resolvedSettings = resolvedSettings,
            ),
        )
        return XrayLaunchSpec(
            binaryFile = binaryFile,
            workingDirectory = runtimeDir,
            configFile = configFile,
        )
    }

    fun start(
        settings: WhiteZiaSettings,
        resolvedSettings: ResolvedWhiteZiaSettings,
        onOutput: (String) -> Unit = {},
    ): XrayLaunchSpec {
        stop()
        val launchSpec = prepareLaunch(settings, resolvedSettings)
        onOutput("Xray runtime prepared")
        try {
            val startedProcess = ProcessBuilder(
                launchSpec.binaryFile.absolutePath,
                "run",
                "-config",
                launchSpec.configFile.absolutePath,
            )
                .directory(launchSpec.workingDirectory)
                .redirectErrorStream(true)
                .start()
            val drainThread = drainProcessOutput(startedProcess, onOutput)
            synchronized(processLock) {
                currentLaunchSpec = launchSpec
                process = startedProcess
                outputDrainThread = drainThread
            }
            drainThread.start()
            onOutput("Xray process started")
        } catch (error: IOException) {
            cleanupLaunchFiles(launchSpec)
            throw error
        }
        return launchSpec
    }

    fun stop(gracePeriodMillis: Long = 1_500) {
        val activeProcess: Process
        val drainThread: Thread?
        synchronized(processLock) {
            activeProcess = process ?: return
            drainThread = outputDrainThread
        }
        activeProcess.destroy()
        try {
            activeProcess.waitFor(gracePeriodMillis, TimeUnit.MILLISECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        if (activeProcess.isAlive) {
            activeProcess.destroyForcibly()
            try {
                activeProcess.waitFor(gracePeriodMillis, TimeUnit.MILLISECONDS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
        synchronized(processLock) {
            if (process === activeProcess) {
                process = null
                outputDrainThread = null
                cleanupLaunchFilesLocked()
            }
        }
        if (drainThread != null && drainThread !== Thread.currentThread()) {
            try {
                drainThread.join(OutputDrainJoinMillis)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
    }

    fun isRunning(): Boolean {
        return synchronized(processLock) {
            process?.isAlive == true
        }
    }

    fun exitCodeOrNull(): Int? {
        val activeProcess = synchronized(processLock) {
            process
        } ?: return null
        if (activeProcess.isAlive) {
            return null
        }
        return synchronized(processLock) {
            if (process !== activeProcess) {
                return@synchronized null
            }
            val exitCode = activeProcess.exitValue()
            process = null
            outputDrainThread = null
            cleanupLaunchFilesLocked()
            exitCode
        }
    }

    private fun drainProcessOutput(
        process: Process,
        onOutput: (String) -> Unit,
    ): Thread {
        return thread(
            name = "xray-output",
            isDaemon = true,
            start = false,
        ) {
            try {
                process.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        if (shouldForwardOutput(line)) {
                            onOutput(line)
                        }
                    }
                }
            } catch (_: IOException) {
                // Destroying the process closes this stream on another thread during normal shutdown.
            } finally {
                cleanupExitedProcess(process)
            }
        }
    }

    private fun cleanupExitedProcess(finishedProcess: Process) {
        synchronized(processLock) {
            if (process !== finishedProcess || finishedProcess.isAlive) {
                return
            }
            process = null
            outputDrainThread = null
            cleanupLaunchFilesLocked()
        }
    }

    private fun cleanupLaunchFiles(launchSpec: XrayLaunchSpec) {
        runCatching { launchSpec.configFile.delete() }
    }

    private fun cleanupLaunchFilesLocked() {
        val launchSpec = currentLaunchSpec ?: return
        cleanupLaunchFiles(launchSpec)
        currentLaunchSpec = null
    }

    companion object {
        private const val StaleLaunchFileMaxAgeMillis = 24L * 60L * 60L * 1_000L
        private const val OutputDrainJoinMillis = 500L
        private val LaunchFileRegex = Regex("""\.wx-[A-Za-z0-9-]+\.json""")

        internal fun shouldForwardOutput(line: String): Boolean {
            if (line.isBlank()) {
                return false
            }
            val normalized = line.lowercase()
            return " accepted tcp:" !in normalized && " accepted udp:" !in normalized
        }

        internal fun cleanupStaleLaunchFiles(
            runtimeDir: File,
            nowMillis: Long = System.currentTimeMillis(),
            maxAgeMillis: Long = StaleLaunchFileMaxAgeMillis,
        ) {
            runtimeDir.listFiles()
                ?.asSequence()
                ?.filter { file ->
                    file.isFile &&
                        LaunchFileRegex.matches(file.name) &&
                        nowMillis - file.lastModified() > maxAgeMillis
                }
                ?.forEach { file ->
                    runCatching { file.delete() }
                }
        }
    }
}
