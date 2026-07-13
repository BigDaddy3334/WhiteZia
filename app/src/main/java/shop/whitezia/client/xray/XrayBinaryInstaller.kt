package shop.whitezia.client.xray

import android.content.Context
import java.io.File

class XrayBinaryInstaller(
    private val context: Context,
) {

    fun installExecutable(): File {
        val nativeDir = File(context.applicationInfo.nativeLibraryDir)
        val executable = NativeLibraryNames
            .asSequence()
            .map { name -> File(nativeDir, name) }
            .firstOrNull(File::exists)
            ?: throw IllegalStateException(
                "Xray runtime executable not found. Bundle it as jniLibs/<abi>/libxray.so",
            )
        if (!executable.canExecute()) {
            throw IllegalStateException(
                "Xray runtime executable is not executable: ${executable.absolutePath}",
            )
        }
        return executable
    }

    companion object {
        private val NativeLibraryNames = listOf("libxray.so")
    }
}
