package shop.whitezia.client.runtime

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

/** One cancellable, monotonic SOCKS startup wait shared by runtime and trial callers. */
internal suspend fun awaitProxyStartup(
    runtimeName: String,
    timeoutMillis: Long,
    startupFailure: () -> String?,
    isRunning: () -> Boolean,
    exitCode: () -> Int?,
    isPortReady: () -> Boolean,
    keepWaiting: () -> Boolean = { true },
    pollMillis: Long = 500L,
    monotonicMillis: () -> Long = { System.nanoTime() / 1_000_000L },
    pause: suspend (Long) -> Unit = { delay(it) },
) {
    require(timeoutMillis > 0L && pollMillis > 0L)
    val started = monotonicMillis()
    fun remainingMillis() = timeoutMillis - (monotonicMillis() - started)
    fun requireRequested() {
        if (!keepWaiting()) throw CancellationException("$runtimeName startup stopped")
    }
    while (true) {
        currentCoroutineContext().ensureActive()
        requireRequested()
        startupFailure()?.let { throw IllegalStateException("$runtimeName startup failed: $it") }
        if (!isRunning()) {
            val code = exitCode()
            throw IllegalStateException("$runtimeName process exited before SOCKS was ready" +
                (code?.let { " (exit code $it)" } ?: ""))
        }
        if (remainingMillis() <= 0L) throw IllegalStateException("$runtimeName SOCKS startup timed out")
        if (isPortReady()) {
            currentCoroutineContext().ensureActive()
            requireRequested()
            if (remainingMillis() <= 0L) throw IllegalStateException("$runtimeName SOCKS startup timed out")
            return
        }
        pause(minOf(pollMillis, remainingMillis().coerceAtLeast(1L)))
    }
}
