package shop.whitezia.client.runtime

/** A persisted status and a live listener must both retire before the next start. */
internal class RuntimeStopGuard(
    private val readStates: () -> List<WhiteZiaRuntimeState>,
    private val readPorts: () -> Set<Int>,
    private val probePort: (Int) -> Boolean,
    private val stopServices: () -> Unit,
    private val log: (String) -> Unit,
    private val debug: (String) -> Unit,
    private val awaiter: RuntimeStopAwaiter = RuntimeStopAwaiter(12_000L, 100L, 500L),
) {
    suspend fun prepare(): Boolean {
        val states = readStates()
        val ports = readPorts().sorted()
        val activePorts = ports.filter(probePort)
        debug("Runtime preflight: states=${states.joinToString { "${it.mode}:${it.status}:${it.listenPort}" }}, checkedPorts=$ports, activePorts=$activePorts")
        if (states.any(::isActive) || activePorts.isNotEmpty()) {
            log("Previous VPN runtime detected; stopping before connection")
            stopServices()
        }
        return awaitStopped().also { debug("Runtime preflight finished: stopped=$it") }
    }

    suspend fun stopAndAwait(): Boolean {
        val started = System.nanoTime()
        debug("Sending stop command to VPN and proxy services")
        stopServices()
        val stopped = awaitStopped()
        val millis = (System.nanoTime() - started) / 1_000_000L
        debug("Runtime services stop check: stopped=$stopped, elapsed=${millis}ms")
        if (!stopped) log("Runtime stop timeout; VPN transport may still be closing")
        return stopped
    }

    suspend fun awaitStopped(): Boolean = awaiter.awaitStopped(
        isRuntimeActive = { readStates().any(::isActive) },
        isProxyActive = { readPorts().any(probePort) },
    )

    private fun isActive(state: WhiteZiaRuntimeState): Boolean = state.status in ActiveStatuses

    private companion object {
        val ActiveStatuses = setOf(WhiteZiaRuntimeStateStore.StatusReady,
            WhiteZiaRuntimeStateStore.StatusStarting, WhiteZiaRuntimeStateStore.StatusStopping)
    }
}
