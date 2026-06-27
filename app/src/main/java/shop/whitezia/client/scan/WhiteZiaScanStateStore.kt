package shop.whitezia.client.scan

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import org.json.JSONArray
import org.json.JSONObject
import shop.whitezia.client.model.WhiteZiaScanDefaults
import shop.whitezia.client.model.WhiteZiaScanState
import shop.whitezia.client.model.WhiteZiaScanStatus

object WhiteZiaScanStateStore {

    fun read(context: Context): WhiteZiaScanState {
        return runCatching {
            val file = stateFile(context)
            if (!file.exists()) {
                return WhiteZiaScanState()
            }
            val raw = AtomicFile(file).openRead().use { stream ->
                stream.readBytes().toString(Charsets.UTF_8)
            }
            decode(JSONObject(raw))
        }.getOrDefault(WhiteZiaScanState())
    }

    fun write(context: Context, state: WhiteZiaScanState) {
        runCatching {
            val target = stateFile(context)
            target.parentFile?.mkdirs()
            val atomicFile = AtomicFile(target)
            var stream: FileOutputStream? = null
            try {
                stream = atomicFile.startWrite()
                stream.write(encode(state).toString().toByteArray(Charsets.UTF_8))
                atomicFile.finishWrite(stream)
            } catch (error: IOException) {
                stream?.let(atomicFile::failWrite)
                throw error
            }
        }
    }

    fun stopped(
        context: Context,
        sessionId: String,
        message: String,
        clearWorkerFailures: Boolean = false,
    ): WhiteZiaScanState {
        val previous = read(context)
        return previous.copy(
            sessionId = sessionId.ifBlank { previous.sessionId },
            status = WhiteZiaScanStatus.Stopped,
            updatedAtMillis = System.currentTimeMillis(),
            message = message,
            workerFailures = if (clearWorkerFailures) emptyList() else previous.workerFailures,
        ).also { write(context, it) }
    }

    private fun stateFile(context: Context): File {
        return File(File(context.noBackupFilesDir, ScanStateDirectory), "scan.json")
    }

    private fun encode(state: WhiteZiaScanState): JSONObject {
        return JSONObject()
            .put("sessionId", state.sessionId)
            .put("status", state.status)
            .put("sourceName", state.sourceName)
            .put("totalResolvers", state.totalResolvers)
            .put("completedResolvers", state.completedResolvers)
            .put("validResolvers", state.validResolvers)
            .put("rejectedResolvers", state.rejectedResolvers)
            .put("workerCount", state.workerCount)
            .put("startedAtMillis", state.startedAtMillis)
            .put("updatedAtMillis", state.updatedAtMillis)
            .put("durationMillis", state.durationMillis)
            .put("message", state.message)
            .put("validResolverEntries", JSONArray().also { array ->
                state.validResolverEntries.forEach(array::put)
            })
            .put("rejectedResolverEntries", JSONArray().also { array ->
                state.rejectedResolverEntries.forEach(array::put)
            })
            .put("workerFailures", JSONArray().also { array ->
                state.workerFailures.forEach(array::put)
            })
    }

    private fun decode(json: JSONObject): WhiteZiaScanState {
        return WhiteZiaScanState(
            sessionId = json.optString("sessionId"),
            status = json.optString("status", WhiteZiaScanStatus.Idle),
            sourceName = json.optString("sourceName"),
            totalResolvers = json.optInt("totalResolvers"),
            completedResolvers = json.optInt("completedResolvers"),
            validResolvers = json.optInt("validResolvers"),
            rejectedResolvers = json.optInt("rejectedResolvers"),
            workerCount = json.optInt("workerCount", WhiteZiaScanDefaults.DefaultWorkerCount).coerceAtLeast(1),
            startedAtMillis = json.optLong("startedAtMillis"),
            updatedAtMillis = json.optLong("updatedAtMillis"),
            durationMillis = json.optLong("durationMillis"),
            message = json.optString("message"),
            validResolverEntries = json.optJSONArray("validResolverEntries").toStringList(),
            rejectedResolverEntries = json.optJSONArray("rejectedResolverEntries").toStringList(),
            workerFailures = json.optJSONArray("workerFailures").toStringList(),
        )
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) {
            return emptyList()
        }
        return List(length()) { index -> optString(index) }
            .map(String::trim)
            .filter(String::isNotEmpty)
    }

    private const val ScanStateDirectory = "scan-state"
}
