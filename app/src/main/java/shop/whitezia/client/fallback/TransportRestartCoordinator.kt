package shop.whitezia.client.fallback

import kotlinx.coroutines.delay

internal enum class TransportRestartResult {
    Ready,
    Cancelled,
    RuntimeStopTimedOut,
}

internal class TransportRestartCoordinator(
    private val delayMillis: suspend (Long) -> Unit = { delay(it) },
) {
    suspend fun awaitReady(
        awaitRuntimeStop: suspend () -> Boolean,
        settleDelayMillis: Long,
        shouldContinue: () -> Boolean,
    ): TransportRestartResult {
        require(settleDelayMillis >= 0L)
        if (!shouldContinue()) {
            return TransportRestartResult.Cancelled
        }
        if (!awaitRuntimeStop()) {
            return TransportRestartResult.RuntimeStopTimedOut
        }
        if (!shouldContinue()) {
            return TransportRestartResult.Cancelled
        }
        delayMillis(settleDelayMillis)
        return if (shouldContinue()) {
            TransportRestartResult.Ready
        } else {
            TransportRestartResult.Cancelled
        }
    }
}
