package shop.whitezia.client.account

import kotlinx.coroutines.CancellationException

internal class AccountOperationGuard {
    private var generation = 0L

    @Synchronized
    fun snapshot(): Long = generation

    @Synchronized
    fun <T> ifCurrent(expectedGeneration: Long, action: () -> T): T {
        if (generation != expectedGeneration) {
            throw CancellationException("Account session changed")
        }
        return action()
    }

    @Synchronized
    fun <T> invalidate(action: () -> T): T {
        generation += 1
        return action()
    }
}
