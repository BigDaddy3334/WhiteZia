package shop.whitezia.client.runtime

import android.content.Context
import android.util.AtomicFile
import java.io.File
import android.util.Log
import java.io.FileOutputStream
import org.json.JSONObject
import shop.whitezia.client.model.WhiteZiaSettings
import shop.whitezia.client.model.WhiteZiaOptions
import shop.whitezia.client.model.resolve
import shop.whitezia.client.model.selectedConnectionProfile

data class WhiteZiaRuntimeState(
    val sessionId: String,
    val mode: String,
    val status: String,
    val connectionProfileId: String,
    val listenIp: String,
    val listenPort: Int,
    val updatedAtMillis: Long,
    val message: String = "",
    val transportMode: String = "",
    val recoveryWaiting: Boolean = false,
    val schemaVersion: Int = 1,
)

internal fun shouldApplyRuntimeModeUpdate(previous: WhiteZiaRuntimeState?, sessionId: String): Boolean =
    previous?.sessionId.isNullOrBlank() || sessionId.isBlank() || previous.sessionId == sessionId

object WhiteZiaRuntimeStateStore {
    const val ModeProxy = "proxy"
    const val ModeVpn = "vpn"
    const val StatusStarting = "starting"
    const val StatusStopping = "stopping"
    const val StatusReady = "ready"
    const val StatusStopped = "stopped"
    const val StatusFailed = "failed"
    const val TransportAmneziaWg = "awg"
    const val CurrentSchemaVersion = 2

    fun markStarting(
        context: Context,
        settings: WhiteZiaSettings,
        sessionId: String,
        message: String = "",
        recoveryWaiting: Boolean = false,
        transportMode: String = settings.transportMode,
    ) {
        writeSettingsState(context, settings, sessionId, StatusStarting, message, recoveryWaiting, transportMode)
    }

    fun markReady(
        context: Context,
        settings: WhiteZiaSettings,
        sessionId: String,
        message: String = "",
        transportMode: String = settings.transportMode,
    ) {
        writeSettingsState(context, settings, sessionId, StatusReady, message, false, transportMode)
    }

    fun markStopping(context: Context, mode: String, sessionId: String = "", message: String = "") {
        writeModeState(context, mode, sessionId, StatusStopping, message)
    }

    fun markStopped(context: Context, mode: String, sessionId: String = "", message: String = "") {
        writeModeState(context, mode, sessionId, StatusStopped, message)
    }

    fun markFailed(context: Context, mode: String, message: String, sessionId: String = "") {
        writeModeState(context, mode, sessionId, StatusFailed, message)
    }

    fun read(context: Context, mode: String): WhiteZiaRuntimeState? {
        return runCatching {
            RuntimeFileLock.withLock(lockFile(context, mode)) {
                readStateLocked(stateFile(context, mode))
            }
        }.getOrNull()
    }

    private fun readStateLocked(file: File): WhiteZiaRuntimeState? {
        return runCatching {
            val raw = AtomicFile(file).openRead().use { stream ->
                stream.readBytes().toString(Charsets.UTF_8)
            }
            decode(JSONObject(raw))
        }.getOrNull()
    }

    fun readAll(context: Context): List<WhiteZiaRuntimeState> {
        return listOf(ModeProxy, ModeVpn).mapNotNull { mode ->
            read(context, mode)
        }
    }

    private fun writeSettingsState(
        context: Context,
        settings: WhiteZiaSettings,
        sessionId: String,
        status: String,
        message: String,
        recoveryWaiting: Boolean,
        transportMode: String,
    ) {
        val resolvedSettings = settings.resolve()
        val connectionProfile = settings.selectedConnectionProfile()
        writeState(
            context = context,
            state = WhiteZiaRuntimeState(
                sessionId = sessionId,
                mode = resolvedSettings.connectionMode,
                status = status,
                connectionProfileId = connectionProfile.id,
                listenIp = resolvedSettings.listenIp,
                listenPort = resolvedSettings.listenPort,
                updatedAtMillis = System.currentTimeMillis(),
                message = message,
                transportMode = when {
                    transportMode != WhiteZiaOptions.TransportAuto -> transportMode
                    message.contains("AmneziaWG", ignoreCase = true) -> TransportAmneziaWg
                    message.contains("Xray", ignoreCase = true) -> WhiteZiaOptions.TransportXray
                    else -> transportMode
                },
                recoveryWaiting = recoveryWaiting,
                schemaVersion = CurrentSchemaVersion,
            ),
        )
    }

    private fun writeModeState(
        context: Context,
        mode: String,
        sessionId: String,
        status: String,
        message: String,
    ) {
        updateState(context, mode) { previous ->
            if (!shouldApplyRuntimeModeUpdate(previous, sessionId)) {
                null
            } else {
                WhiteZiaRuntimeState(
                    sessionId = sessionId.ifBlank { previous?.sessionId.orEmpty() },
                    mode = mode,
                    status = status,
                    connectionProfileId = previous?.connectionProfileId.orEmpty(),
                    listenIp = previous?.listenIp.orEmpty(),
                    listenPort = previous?.listenPort ?: 0,
                    updatedAtMillis = System.currentTimeMillis(),
                    message = message,
                    transportMode = previous?.transportMode.orEmpty(),
                    schemaVersion = CurrentSchemaVersion,
                )
            }
        }
    }

    private fun writeState(context: Context, state: WhiteZiaRuntimeState) {
        updateState(context, state.mode) { state }
    }

    private fun updateState(
        context: Context,
        mode: String,
        update: (WhiteZiaRuntimeState?) -> WhiteZiaRuntimeState?,
    ) {
        try {
            RuntimeFileLock.withLock(lockFile(context, mode)) {
                val target = stateFile(context, mode)
                val state = update(readStateLocked(target)) ?: return@withLock
                val atomicFile = AtomicFile(target)
                var stream: FileOutputStream? = null
                try {
                    stream = atomicFile.startWrite()
                    stream.write(encode(state).toString().toByteArray(Charsets.UTF_8))
                    atomicFile.finishWrite(stream)
                } catch (error: Throwable) {
                    stream?.let(atomicFile::failWrite)
                    throw error
                }
            }
        } catch (error: Exception) {
            Log.w(LogTag, "Failed to write $mode runtime state", error)
        }
    }

    private fun stateFile(context: Context, mode: String): File {
        return File(File(context.noBackupFilesDir, RuntimeStateDirectory), "$mode.json")
    }

    private fun lockFile(context: Context, mode: String): File {
        return File(File(context.noBackupFilesDir, RuntimeStateDirectory), "$mode.lock")
    }

    internal fun encode(state: WhiteZiaRuntimeState): JSONObject {
        return JSONObject()
            .put("sessionId", state.sessionId)
            .put("mode", state.mode)
            .put("status", state.status)
            .put("connectionProfileId", state.connectionProfileId)
            .put("listenIp", state.listenIp)
            .put("listenPort", state.listenPort)
            .put("updatedAtMillis", state.updatedAtMillis)
            .put("message", state.message)
            .put("transportMode", state.transportMode)
            .put("recoveryWaiting", state.recoveryWaiting)
            .put("schemaVersion", state.schemaVersion)
    }

    internal fun decode(json: JSONObject): WhiteZiaRuntimeState {
        return WhiteZiaRuntimeState(
            sessionId = json.optString("sessionId"),
            mode = json.optString("mode"),
            status = json.optString("status"),
            connectionProfileId = json.optString("connectionProfileId"),
            listenIp = json.optString("listenIp"),
            listenPort = json.optInt("listenPort"),
            updatedAtMillis = json.optLong("updatedAtMillis"),
            message = json.optString("message"),
            transportMode = json.optString("transportMode"),
            recoveryWaiting = json.optBoolean("recoveryWaiting", false),
            schemaVersion = json.optInt("schemaVersion", 1),
        )
    }

    private const val LogTag = "WhiteZiaRuntimeState"
    private const val RuntimeStateDirectory = "runtime-state"
}
