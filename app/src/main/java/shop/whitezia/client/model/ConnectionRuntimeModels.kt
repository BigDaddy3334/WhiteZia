package shop.whitezia.client.model

data class ConnectionStats(
    val downloadBytes: Long = 0,
    val uploadBytes: Long = 0,
    val totalDataUsageBytes: Long = 0,
    val downloadSpeedBytesPerSecond: Long = 0,
    val uploadSpeedBytesPerSecond: Long = 0,
    val peakSpeedBytesPerSecond: Long = 0,
    val connectedApps: Int = 0,
)

data class ResolverRuntimeState(
    val activeResolvers: List<String> = emptyList(),
    val standbyResolvers: List<String> = emptyList(),
    val validResolvers: List<String> = emptyList(),
)

data class ConnectionProgressState(
    val phase: String = "idle",
    val percent: Int = 0,
    val completed: Int = 0,
    val total: Int = 0,
    val valid: Int = 0,
    val rejected: Int = 0,
    val detail: String = "",
) {
    val fraction: Float
        get() = percent.coerceIn(0, 100) / 100f

    val label: String
        get() = when (phase.lowercase()) {
            "preparing" -> "Preparing"
            "autotune" -> if (total > 0) {
                "Parallel Test $completed/$total"
            } else {
                "Parallel Test"
            }
            "starting" -> "Starting"
            "checking" -> "Checking internet via proxy"
            "reconnecting" -> "Restoring connection"
            "waiting-network" -> "Waiting for network"
            "waiting-action" -> "Action required"
            "stopping" -> "Stopping previous route"
            "mtu" -> if (total > 0) {
                "Scanning $completed/$total"
            } else {
                "Scanning"
            }
            "selecting" -> "Selecting resolver"
            "session" -> "Starting session"
            "runtime" -> "Starting runtime"
            "retry" -> "Retrying"
            "connected" -> "Connected"
            else -> "Preparing"
        }
}

object ConnectionVerificationStatus {
    const val Idle = "idle"
    const val Checking = "checking"
    const val Verified = "verified"
    const val Failed = "failed"
}

enum class ConnectionVerificationCoverage { Unknown, SocksProxy }

data class ConnectionVerificationState(
    val status: String = ConnectionVerificationStatus.Idle,
    val message: String = "",
    val checkedAtMillis: Long = 0,
    val coverage: ConnectionVerificationCoverage = ConnectionVerificationCoverage.Unknown,
)

data class AutoTuneTrialResult(
    val configId: String,
    val label: String,
    val listenIp: String,
    val listenPort: Int,
    val status: String = "pending",
    val speedBytesPerSecond: Long = 0L,
    val pingMillis: Long? = null,
    val message: String = "",
    val selected: Boolean = false,
)

object ServerTestStatus {
    const val Pending = "pending"
    const val Starting = "starting"
    const val Measuring = "measuring"
    const val Ready = "ready"
    const val Failed = "failed"
}

data class ServerTestResult(
    val serverId: String,
    val label: String,
    val domain: String,
    val status: String = ServerTestStatus.Pending,
    val speedBytesPerSecond: Long = 0L,
    val pingMillis: Long? = null,
    val message: String = "",
)

data class ServerTestState(
    val isRunning: Boolean = false,
    val startedAtMillis: Long = 0L,
    val completedAtMillis: Long = 0L,
    val message: String = "",
    val results: List<ServerTestResult> = emptyList(),
)
