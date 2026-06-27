package shop.whitezia.client.vpn

import android.content.Context
import java.io.File

class Tun2SocksBinaryInstaller(
    private val context: Context,
) {

    fun requireLibrary(): File {
        val library = File(context.applicationInfo.nativeLibraryDir, NativeLibraryName)
        if (!library.exists()) {
            throw IllegalStateException(
                "tun2proxy native library not found. Bundle it as jniLibs/<abi>/$NativeLibraryName",
            )
        }
        if (!library.canRead()) {
            throw IllegalStateException(
                "tun2proxy native library is not readable: ${library.absolutePath}",
            )
        }
        return library
    }

    companion object {
        private const val NativeLibraryName = "libtun2proxy.so"
    }
}
