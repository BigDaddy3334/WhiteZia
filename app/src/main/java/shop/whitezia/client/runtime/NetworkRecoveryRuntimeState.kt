package shop.whitezia.client.runtime

internal fun WhiteZiaRuntimeState.isLiveRuntimeStarting(nowMillis: Long = System.currentTimeMillis()): Boolean =
    schemaVersion >= 2 &&
        mode in listOf(WhiteZiaRuntimeStateStore.ModeVpn, WhiteZiaRuntimeStateStore.ModeProxy) &&
        status == WhiteZiaRuntimeStateStore.StatusStarting &&
        sessionId.isNotBlank() &&
        nowMillis - updatedAtMillis in 0L..15_000L

internal fun WhiteZiaRuntimeState.isLiveNetworkRecovery(nowMillis: Long = System.currentTimeMillis()): Boolean =
    mode == WhiteZiaRuntimeStateStore.ModeVpn &&
        status == WhiteZiaRuntimeStateStore.StatusStarting &&
        sessionId.isNotBlank() &&
        (recoveryWaiting || (schemaVersion < 2 && message.startsWith("Reconnecting "))) &&
        nowMillis - updatedAtMillis in 0L..15_000L
