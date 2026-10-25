package shop.whitezia.client.runtime

import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import org.json.JSONObject

internal data class VpnDesiredSession(val sessionId: String, val wasReady: Boolean = false)

/** Desired connection survives process death; Stop removes it before dispatching its Intent. */
internal class VpnDesiredSessionStore(private val directory: File) {
    private val stateFile get() = File(directory, "desired-vpn.json")
    private val lockFile get() = File(directory, "desired-vpn.lock")

    fun read(): VpnDesiredSession? = RuntimeFileLock.withLock(lockFile) { readLocked() }

    /** Publishes runtime status only while the same user intent still owns it.
     * Load launch snapshots before this guard: their pruning takes this lock.
     * The block must not re-enter this store or access the launch-request store.
     */
    fun withCurrent(expected: VpnDesiredSession?, publish: () -> Unit): Boolean = RuntimeFileLock.withLock(lockFile) {
        if (readLocked() != expected) return@withLock false
        publish()
        true
    }

    fun request(sessionId: String): VpnDesiredSession = RuntimeFileLock.withLock(lockFile) {
        require(sessionId.isValidVpnSessionId()) { "Invalid VPN session ID" }
        VpnDesiredSession(sessionId, readLocked()?.takeIf { it.sessionId == sessionId }?.wasReady == true)
            .also(::writeLocked)
    }

    fun markReady(sessionId: String) = RuntimeFileLock.withLock(lockFile) {
        readLocked()?.takeIf { it.sessionId == sessionId }?.let { writeLocked(it.copy(wasReady = true)) }
    }

    fun clear(expectedSessionId: String? = null): VpnDesiredSession? = RuntimeFileLock.withLock(lockFile) {
        val previous = readLocked()
        if (expectedSessionId != null && previous?.sessionId != expectedSessionId) return@withLock null
        check(!stateFile.exists() || stateFile.delete()) { "Unable to clear desired VPN session" }
        previous
    }

    private fun readLocked(): VpnDesiredSession? = runCatching {
        if (!stateFile.isFile || stateFile.length() > MaxStateBytes) return@runCatching null
        decodeDesiredVpnSession(JSONObject(stateFile.readText(Charsets.UTF_8)))
    }.getOrNull()

    private fun writeLocked(session: VpnDesiredSession) {
        val temporary = File(directory, "desired-vpn.json.tmp")
        try {
            FileOutputStream(temporary).use { output ->
                output.write(JSONObject().put("sessionId", session.sessionId).put("wasReady", session.wasReady)
                    .put("schemaVersion", 1).toString().toByteArray(Charsets.UTF_8))
                output.fd.sync()
            }
            try {
                Files.move(temporary.toPath(), stateFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), stateFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally { temporary.delete() }
    }

    private companion object { const val MaxStateBytes = 4_096 }
}

internal fun decodeDesiredVpnSession(json: JSONObject): VpnDesiredSession? {
    if (json.optInt("schemaVersion") != 1) return null
    val sessionId = json.optString("sessionId")
    if (!sessionId.isValidVpnSessionId()) return null
    return VpnDesiredSession(sessionId, json.optBoolean("wasReady"))
}

private fun String.isValidVpnSessionId(): Boolean = length in 1..128 && all { it.isLetterOrDigit() || it in "._-" }

/** Old STOPs may clean an old runtime, but may not cancel a newer requested session. */
internal fun acceptsVpnStop(currentSessionId: String, stopSessionId: String, desiredSessionId: String?): Boolean =
    when {
        desiredSessionId == null -> true
        currentSessionId.isBlank() -> stopSessionId == desiredSessionId
        desiredSessionId == currentSessionId -> stopSessionId == currentSessionId
        else -> stopSessionId == currentSessionId
    }

/** Revoke cleans its old runtime even if a new START is queued, but never the new runtime. */
internal fun acceptsVpnRevocation(currentSessionId: String, revokedSessionId: String, desiredSessionId: String?): Boolean =
    if (currentSessionId.isNotBlank()) currentSessionId == revokedSessionId
    else desiredSessionId == null || desiredSessionId == revokedSessionId
