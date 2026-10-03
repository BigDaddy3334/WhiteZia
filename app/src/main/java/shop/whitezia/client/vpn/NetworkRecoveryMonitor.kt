package shop.whitezia.client.vpn

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull

internal suspend fun monitorNetworkRecovery(
    changes: ReceiveChannel<Unit>,
    pollMillis: Long,
    settleMillis: Long,
    onError: (Exception) -> Unit,
    checkNetwork: suspend () -> Unit,
) {
    while (currentCoroutineContext().isActive) {
        val closed = withTimeoutOrNull(pollMillis) { changes.receiveCatching().isClosed } ?: false
        if (closed) return
        delay(settleMillis)
        try {
            checkNetwork()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            onError(error)
        }
    }
}
