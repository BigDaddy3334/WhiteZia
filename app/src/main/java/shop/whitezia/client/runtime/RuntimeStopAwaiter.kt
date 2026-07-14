package shop.whitezia.client.runtime

import kotlinx.coroutines.delay

internal class RuntimeStopAwaiter(
    private val timeoutMillis: Long,
    private val pollIntervalMillis: Long,
    private val stableWindowMillis: Long,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val delayMillis: suspend (Long) -> Unit = { delay(it) },
) {
    init {
        require(timeoutMillis >= 0L)
        require(pollIntervalMillis > 0L)
        require(stableWindowMillis >= 0L)
    }

    suspend fun awaitStopped(
        isRuntimeActive: () -> Boolean,
        isProxyActive: () -> Boolean,
    ): Boolean {
        val deadline = nowMillis() + timeoutMillis
        var stoppedSinceMillis: Long? = null
        while (true) {
            val now = nowMillis()
            if (!isRuntimeActive() && !isProxyActive()) {
                val stoppedSince = stoppedSinceMillis ?: now.also { stoppedSinceMillis = it }
                if (now - stoppedSince >= stableWindowMillis) {
                    return true
                }
            } else {
                stoppedSinceMillis = null
            }
            if (now >= deadline) {
                return false
            }
            delayMillis(minOf(pollIntervalMillis, deadline - now))
        }
    }
}
