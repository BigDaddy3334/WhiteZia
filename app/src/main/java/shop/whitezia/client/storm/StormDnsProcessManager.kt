package shop.whitezia.client.storm

import android.content.Context
import shop.whitezia.client.runtime.ExternalProcessRunner
import java.io.File
import java.util.UUID
import shop.whitezia.client.model.StormDnsServerProfile
import shop.whitezia.client.model.WhiteZiaSettings

data class StormDnsLaunchSpec(
    val binaryFile: File,
    val workingDirectory: File,
    val configFile: File,
    val resolversFile: File,
)

class StormDnsProcessManager(
    private val context: Context,
    private val binaryInstaller: StormDnsBinaryInstaller = StormDnsBinaryInstaller(context),
) {

    private val runner = ExternalProcessRunner<StormDnsLaunchSpec>(
        name = CottenDnsEngineContract.DisplayName,
        startProcess = { launch ->
            ProcessBuilder(CottenDnsEngineContract.command(
                launch.binaryFile, launch.configFile, launch.resolversFile,
            )).directory(launch.workingDirectory).redirectErrorStream(true).start()
        },
        cleanup = { launch ->
            runCatching { launch.configFile.delete() }
            runCatching { launch.resolversFile.delete() }
        },
        preparedMessage = "Runtime prepared",
    )

    fun prepareLaunch(
        serverProfile: StormDnsServerProfile,
        settings: WhiteZiaSettings,
    ): StormDnsLaunchSpec {
        val runtimeDir = File(context.noBackupFilesDir, "stormdns/runtime").apply {
            mkdirs()
        }
        cleanupStaleLaunchFiles(runtimeDir)
        val binaryFile = binaryInstaller.installExecutable()
        val launchId = UUID.randomUUID().toString()
        val configFile = File(runtimeDir, ".wd-$launchId.toml")
        val resolversFile = File(runtimeDir, ".wd-$launchId.resolvers")

        configFile.writeText(
            StormDnsConfigRenderer.renderClientToml(
                serverProfile = serverProfile,
                settings = settings,
            ),
        )
        resolversFile.writeText(StormDnsConfigRenderer.renderResolvers(settings))

        return StormDnsLaunchSpec(
            binaryFile = binaryFile,
            workingDirectory = runtimeDir,
            configFile = configFile,
            resolversFile = resolversFile,
        )
    }

    fun start(
        serverProfile: StormDnsServerProfile,
        settings: WhiteZiaSettings,
        onOutput: (String) -> Unit = {},
    ): StormDnsLaunchSpec = runner.start({ prepareLaunch(serverProfile, settings) }, onOutput)

    fun stop(gracePeriodMillis: Long = 1_500L) = runner.stop(gracePeriodMillis)
    fun isRunning(): Boolean = runner.isRunning()
    fun exitCodeOrNull(): Int? = runner.exitCodeOrNull()
    fun cleanupLaunchFiles() = runner.cleanupLaunchFiles()

    companion object {
        private const val StaleLaunchFileMaxAgeMillis = 24L * 60L * 60L * 1_000L
        private val LaunchFileRegex = Regex("""\.wd-[A-Za-z0-9-]+\.(toml|resolvers)""")

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
