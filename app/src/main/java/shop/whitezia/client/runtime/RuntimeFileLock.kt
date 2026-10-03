package shop.whitezia.client.runtime

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal object RuntimeFileLock {
    private val localLocks = ConcurrentHashMap<String, ReentrantLock>()

    fun <T> withLock(lockFile: File, operation: () -> T): T {
        val canonicalFile = lockFile.canonicalFile
        val localLock = localLocks.computeIfAbsent(canonicalFile.path) { ReentrantLock() }
        return localLock.withLock {
            // FileLock is not reentrant, even when the JVM lock is.
            if (localLock.holdCount > 1) {
                operation()
            } else {
                canonicalFile.parentFile?.let { directory ->
                    if (!directory.isDirectory && !directory.mkdirs() && !directory.isDirectory) {
                        throw IOException("Unable to create runtime lock directory: $directory")
                    }
                }
                // Keep the lock file: deleting it would let processes lock different inodes.
                RandomAccessFile(canonicalFile, "rw").use { file ->
                    file.channel.lock().use { operation() }
                }
            }
        }
    }
}
