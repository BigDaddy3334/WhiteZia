package shop.whitezia.client.storm

import android.content.Context
import java.io.File

class StormDnsBinaryInstaller(
    private val context: Context,
) {

    fun installExecutable(): File {
        val executable = File(context.applicationInfo.nativeLibraryDir, NativeLibraryName)
        if (!executable.exists()) {
            throw IllegalStateException(
                "StormDNS native executable not found: ${executable.absolutePath}",
            )
        }
        if (!executable.canExecute()) {
            throw IllegalStateException(
                "StormDNS native executable is not executable: ${executable.absolutePath}",
            )
        }
        return executable
    }

    companion object {
        private const val NativeLibraryName = "libstormdns_client.so"
    }
}
