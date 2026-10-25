package shop.whitezia.client.ui.connection

import shop.whitezia.client.runtime.WhiteZiaRuntimeState
import shop.whitezia.client.runtime.WhiteZiaRuntimeStateStore

/** Evaluate at execution time on Main, after an event may have waited in a queue. */
internal suspend fun applyCurrentRuntimeUiEvent(
    sessionId: String,
    isCurrent: (String) -> Boolean,
    apply: suspend () -> Unit,
) {
    if (isCurrent(sessionId)) apply()
}

/** A queued Ready from a previous attempt must not turn a newer recovery green. */
internal fun currentReadyRuntimeSnapshot(
    state: WhiteZiaRuntimeState?,
    sessionId: String,
    expectedMode: String,
): WhiteZiaRuntimeState? = state?.takeIf {
    it.sessionId == sessionId && it.mode == expectedMode && it.status == WhiteZiaRuntimeStateStore.StatusReady
}
