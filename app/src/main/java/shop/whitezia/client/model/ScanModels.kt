package shop.whitezia.client.model

object WhiteZiaScanStatus {
    const val Idle = "idle"
    const val Ready = "ready"
    const val Starting = "starting"
    const val Running = "running"
    const val Completed = "completed"
    const val Failed = "failed"
    const val Stopped = "stopped"
}

object WhiteZiaScanDefaults {
    const val DefaultWorkerCount = 4
    const val WorkerWarningThreshold = 8
}

object WhiteZiaThemeMode {
    const val System = "system"
    const val Light = "light"
    const val Dark = "dark"
}

object WhiteZiaLanguage {
    const val En = "en"
    const val Fa = "fa"
}

data class WhiteZiaScanState(
    val sessionId: String = "",
    val status: String = WhiteZiaScanStatus.Idle,
    val sourceName: String = "",
    val totalResolvers: Int = 0,
    val completedResolvers: Int = 0,
    val validResolvers: Int = 0,
    val rejectedResolvers: Int = 0,
    val workerCount: Int = WhiteZiaScanDefaults.DefaultWorkerCount,
    val startedAtMillis: Long = 0,
    val updatedAtMillis: Long = 0,
    val durationMillis: Long = 0,
    val message: String = "",
    val validResolverEntries: List<String> = emptyList(),
    val rejectedResolverEntries: List<String> = emptyList(),
    val workerFailures: List<String> = emptyList(),
) {
    val isRunning: Boolean
        get() = status == WhiteZiaScanStatus.Starting || status == WhiteZiaScanStatus.Running

    fun recoverIfStale(
        nowMillis: Long,
        staleAfterMillis: Long,
        message: String = "Previous scan is no longer active",
    ): WhiteZiaScanState {
        if (!isRunning) {
            return this
        }
        val lastUpdateMillis = updatedAtMillis.takeIf { it > 0 } ?: startedAtMillis
        if (lastUpdateMillis <= 0L || nowMillis - lastUpdateMillis < staleAfterMillis) {
            return this
        }
        return copy(
            status = WhiteZiaScanStatus.Stopped,
            updatedAtMillis = nowMillis,
            message = message,
        )
    }

    val fraction: Float
        get() = if (totalResolvers > 0) {
            completedResolvers.coerceIn(0, totalResolvers).toFloat() / totalResolvers
        } else {
            0f
        }
}
